// 零售订单与退货 · 示例数据和状态（方案 §四）。退货的规则在 domain/aftersale.ts，这里只存数据、串流程。
import { create } from 'zustand';
import { emit } from '@/domain/events';
import { allocate, applyReturn, refundAmount, restockByDefault, saleStatus, type ReturnReason, type SoldLine } from '@/domain/aftersale';
import { round2 } from '@/domain/money';
import { item } from './data';
import { useBiz } from '@/biz/store';

/** 已售行：在 SoldLine（key · 件数 · 行实付 · 已退 · 称重）上加商品 id、单价、重量、规格 */
export interface SaleLine extends SoldLine { id: string; p: number; w?: number; sku?: [string, string] }
export type Channel = 'store' | 'mini' | 'platform';
export interface Sale { no: string; time: string; ch: Channel; member?: string; pay: [string, number][]; /** 整单优惠（会员券、抹零等），退货时按行分摊 */ disc: number; lines: SaleLine[]; returns: { time: string; amt: number; pcs: number; why: ReturnReason; method: string }[] }

const L = (id: string, q: number, opt: Partial<SaleLine> = {}): SaleLine => {
  const it = item(id), p = opt.p ?? it.p, amt = opt.amt ?? (opt.w != null ? round2(p * opt.w) : round2(p * q));
  return { key: id + (opt.sku ? '-' + opt.sku[0] : ''), id, q, p, amt, weighed: opt.w != null, ...opt };
};
export const INITIAL_SALES: Sale[] = [
  { no: '#R0157', time: '10:21', ch: 'store', member: '王女士', pay: [['wx', 77]], disc: 0.72, returns: [],
    lines: [L('cola', 2), L('milk', 1, { p: 36.9 }), L('apple', 1, { w: 1.236 }), L('croissant', 3)] },
  { no: '#R0155', time: '10:05', ch: 'store', pay: [['cash', 79.2]], disc: 8.8, returns: [],
    lines: [L('tee', 1, { sku: ['白 / M', 'White / M'] }), L('socks', 1, { sku: ['灰', 'Grey'] })] },
  { no: '#R0152', time: '09:40', ch: 'store', member: '王女士', pay: [['sv', 36.8]], disc: 0, returns: [{ time: '09:58', amt: 7.9, pcs: 1, why: 'unwanted', method: 'sv' }],
    lines: [L('water', 4, { amt: 6 }), { ...L('chips', 2), returned: 1 }, L('coffee', 1)] },
  { no: '#R0150', time: '09:12', ch: 'mini', pay: [['ali', 50]], disc: 1.6, returns: [],
    lines: [L('toast', 2), L('eggtart', 4)] },
  { no: '#R0149', time: '08:55', ch: 'store', pay: [['card', 75.8]], disc: 0.07, returns: [{ time: '09:20', amt: 75.8, pcs: 2, why: 'quality', method: 'card' }],
    lines: [{ ...L('cherry', 1, { w: 0.512 }), returned: 1 }, { ...L('grape', 1, { w: 0.862 }), returned: 1 }] },
  { no: '#R0146', time: '08:31', ch: 'platform', pay: [['wx', 14.7]], disc: 0, returns: [],
    lines: [L('nuts', 3)] },
];
export const saleOf = (sales: Sale[], no: string | null) => sales.find((s) => s.no === no) || null;
export const salePcs = (s: Sale) => s.lines.reduce((a, l) => a + (l.weighed ? 1 : l.q), 0);
export const salePaid = (s: Sale) => round2(s.pay.reduce((a, p) => a + p[1], 0));
/** 无单退货能选的商品（现价退） */
export const NO_RECEIPT_ITEMS = ['cola', 'water', 'chips', 'milk', 'croissant', 'tee'];

/* ---------- 状态：订单页 + 退货流程 ---------- */
export type RetStep = 1 | 2 | 3 | 4;
interface RS {
  sales: Sale[]; sel: string; filter: 'all' | 'paid' | 'partial_return' | 'returned'; period: string;
  /** 退货流程：open · step · 原单号（null = 无单退货）· 选的件数 · 原因 · 回库存 · 退款方式 · 授权码位数 · 阶段 */
  /** mode：退货 / 换货（换货 = 前 3 步 + 带抵扣回收银） */
  ret: { open: boolean; mode: 'return' | 'exchange'; step: RetStep; no: string | null; pick: Record<string, number>; why: ReturnReason | ''; restock: boolean; method: string; pin: number; phase: '' | 'refunding' | 'done'; q: string; /** 确认时记下的结果（退完后原单已变，不能再按原单重算） */ result?: { amt: number; pcs: number } };
  set: (p: Partial<RS>) => void; setRet: (p: Partial<RS['ret']>) => void;
}
const RET0: RS['ret'] = { open: false, mode: 'return', step: 1, no: null, pick: {}, why: '', restock: true, method: '', pin: 0, phase: '', q: '' };
export const useSales = create<RS>((set, get) => ({
  sales: INITIAL_SALES.map((s) => ({ ...s, lines: s.lines.map((l) => ({ ...l })) })), sel: '#R0157', filter: 'all', period: 'today', ret: RET0,
  set: (p) => set(p), setRet: (p) => set({ ret: { ...get().ret, ...p } }),
}));

/** 无单退货的「虚拟原单」：按现价、无优惠，件数按选的来 */
export function noReceiptLines(pick: Record<string, number>): SaleLine[] {
  return NO_RECEIPT_ITEMS.map((id) => ({ ...L(id, Math.max(1, pick[id] || 0)), key: id }));
}
/** 当前退货的原单行（有单 → 原单；无单 → 虚拟原单） */
export function retLines(st: RS): { lines: SaleLine[]; disc: number; sale: Sale | null } {
  const sale = saleOf(st.sales, st.ret.no);
  return sale ? { lines: sale.lines, disc: sale.disc, sale } : { lines: noReceiptLines(st.ret.pick), disc: 0, sale: null };
}
export const retAmount = (st: RS) => { const r = retLines(st); return refundAmount(r.lines, r.disc, st.ret.pick); };
export const retNet = (st: RS) => { const r = retLines(st); return allocate(r.lines, r.disc); };

/** 退货对库存：回库 → stock.changed（正数）；不回库 → reason=loss，库存服务生成一张已过账的报损单 */
function emitReturnStock(lines: SaleLine[], pick: Record<string, number>, restock: boolean, ref: string | null) {
  const picked = lines.filter((l) => pick[l.key]), delta: Record<string, number> = {};
  picked.forEach((l) => (delta[l.id] = (delta[l.id] || 0) + (l.weighed ? l.w || 1 : pick[l.key])));
  emit({ type: 'stock.changed', ids: Object.keys(delta), delta, reason: restock ? 'return' : 'loss', ref: ref || undefined });
}
/* 流程动作 */
export function openReturn(no: string | null = null, step: RetStep = 1, mode: 'return' | 'exchange' = 'return') {
  const st = useSales.getState(), sale = saleOf(st.sales, no);
  st.set({ ret: { ...RET0, open: true, mode, step, no, method: sale ? sale.pay[0][0] : 'cash' } });
}
/** 换货第 3 步确认：退回部分记到原单（退款方式记「换货抵扣」），发 exchange.credit 事件，切到收银页签 */
export function confirmExchange() {
  const st = useSales.getState(), r = st.ret, amt = retAmount(st), lines = retLines(st).lines, pcs = Object.values(r.pick).reduce((a, n) => a + n, 0);
  if (!r.no) return;
  st.set({ sales: st.sales.map((x) => (x.no === r.no ? { ...x, lines: applyReturn(x.lines, r.pick), returns: [...x.returns, { time: '10:30', amt, pcs, why: r.why || 'other', method: 'exchange' }] } : x)), ret: { ...r, open: false } });
  emitReturnStock(lines, r.pick, r.restock, r.no);
  emit({ type: 'exchange.credit', orderNo: r.no, amt, pcs, method: r.method });
  useBiz.getState().set({ rwb: 'pos' });
}
export const setReason = (why: ReturnReason) => useSales.getState().setRet({ why, restock: restockByDefault(why) });
/** 确认退款：1.2 秒模拟退款中 → 原单记退货、状态变化、发库存事件 */
export function confirmRefund() {
  const st = useSales.getState(), r = st.ret, amt = retAmount(st), lines = retLines(st).lines, pcs = Object.values(r.pick).reduce((a, n) => a + n, 0);
  st.setRet({ phase: 'refunding', result: { amt, pcs } });
  setTimeout(() => {
    const s2 = useSales.getState();
    if (r.no) s2.set({ sales: s2.sales.map((x) => (x.no === r.no ? { ...x, lines: applyReturn(x.lines, r.pick), returns: [...x.returns, { time: '10:30', amt, pcs, why: r.why || 'other', method: r.method }] } : x)) });
    emitReturnStock(lines, r.pick, r.restock, r.no);
    useSales.getState().setRet({ phase: 'done' });
  }, 1200);
}
export const statusOf = (s: Sale) => saleStatus(s.lines);

/** 页首「退货画面」预设（原型用）：直接打开到某一步 */
export type RetScreen = 'r1' | 'r2' | 'r3' | 'r4' | 'nr' | 'rdone' | 'x2';
export function goReturn(k: RetScreen) {
  const st = useSales.getState(), pick = { cola: 1, apple: 1 };
  const base = { ...RET0, open: true, no: '#R0157', method: 'wx', pick };
  const map: Record<RetScreen, RS['ret']> = {
    r1: { ...RET0, open: true },
    r2: { ...base, step: 2 },
    r3: { ...base, step: 3, why: 'quality', restock: false },
    r4: { ...base, step: 4, why: 'quality', restock: false },
    nr: { ...RET0, open: true, step: 4, no: null, method: 'cash', pick: { cola: 2 }, why: 'wrong', restock: true, pin: 2 },
    rdone: { ...base, step: 4, why: 'quality', restock: false, phase: 'done', result: { amt: 19.14, pcs: 2 } },
    x2: { ...RET0, open: true, mode: 'exchange', step: 2, no: '#R0155', method: 'cash', pick: { 'tee-白 / M': 1 } },
  };
  st.set({ sel: map[k].no || '#R0157', ret: map[k] });
}
