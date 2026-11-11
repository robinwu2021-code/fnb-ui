// 零售库存作业 · 示例数据与状态（方案 §5.2）：入库 · 盘点 · 报损 · 调拨（未实现）。
// 规则在 domain/stock.ts；收银卖出、退货回库、退货不回库（自动报损）都通过领域事件 stock.changed 进来，不直接互相调用。
import { create } from 'zustand';
import { on } from '@/domain/events';
import { applyCount, applyDelta, countDiff, costTotal, nearExpiry, qtyTotal, receive, writeOff, type StockMap } from '@/domain/stock';
import { round2 } from '@/domain/money';
import { ITEMS, item } from './data';

export type DocKind = 'receive' | 'count' | 'loss' | 'transfer';
/** 单据状态：入库 draft → done / void；盘点 counting → pending（待审批）→ done / variance（有差异已过账）；报损 draft → done */
export type DocSt = 'draft' | 'done' | 'void' | 'counting' | 'pending' | 'variance';
export type LossWhy = 'expired' | 'damaged' | 'return';
export interface DocLine { id: string; q: number; why?: LossWhy }
export interface StockDoc { no: string; kind: DocKind; st: DocSt; time: string; who: string; lines: DocLine[]; supplier?: string; scope?: string; ref?: string; diff?: { gain: number; loss: number }; /** 提交盘点时的账面快照：之后再卖出也不影响这张单的差异 */ snap?: StockMap }

/** 账面库存：有 s 的用 s；称重商品按 kg */
export const INITIAL_BOOK: StockMap = Object.fromEntries(ITEMS.map((it) => [it.id, it.s ?? (it.weigh ? 18.5 : 36)]));
/** 进价：示例按售价 6 折 */
export const costOf = (id: string) => round2(item(id).p * 0.6);
export const SAFETY: StockMap = { milk: 6, toast: 4, baguette: 3, croissant: 10, cola: 12, water: 24 };
export const EXPIRY: Record<string, string> = { milk: '2026-11-13', croissant: '2026-11-12', yogurt: '2026-11-12', toast: '2026-11-14', eggtart: '2026-11-11' };
export const TODAY = '2026-11-11';
export const isNear = (id: string) => !!EXPIRY[id] && nearExpiry(EXPIRY[id], TODAY);
export const isLow = (id: string, book: StockMap) => SAFETY[id] != null && (book[id] ?? 0) <= SAFETY[id];
export const unitOf = (id: string) => (item(id).weigh ? 'kg' : '件');

const INITIAL_DOCS: StockDoc[] = [
  { no: 'RK1111-03', kind: 'receive', st: 'draft', time: '10:12', who: '李娜', supplier: '城北乳业', lines: [{ id: 'milk', q: 12 }, { id: 'yogurt', q: 24 }] },
  { no: 'RK1111-02', kind: 'receive', st: 'done', time: '08:40', who: '李娜', supplier: '可口可乐华南', lines: [{ id: 'cola', q: 48 }, { id: 'water', q: 72 }, { id: 'tea', q: 24 }] },
  { no: 'RK1111-01', kind: 'receive', st: 'void', time: '08:05', who: '张伟', supplier: '城北乳业', lines: [{ id: 'milk', q: 6 }] },
  { no: 'PD1111-01', kind: 'count', st: 'counting', time: '09:30', who: '李娜', scope: '饮品', lines: [{ id: 'cola', q: 30 }, { id: 'water', q: 36 }, { id: 'coffee', q: 34 }] },
  { no: 'PD1110-02', kind: 'count', st: 'pending', time: '昨天 21:10', who: '张伟', scope: '烘焙', lines: [{ id: 'croissant', q: 33 }, { id: 'toast', q: 2 }, { id: 'eggtart', q: 36 }], diff: { gain: 0, loss: 3 }, snap: { croissant: 36, toast: 2, eggtart: 36 } },
  { no: 'PD1109-01', kind: 'count', st: 'variance', time: '11-09 21:30', who: '李娜', scope: '零食', lines: [{ id: 'chips', q: 35 }], diff: { gain: 0, loss: 1 }, snap: { chips: 36 } },
  { no: 'BS1111-01', kind: 'loss', st: 'draft', time: '09:50', who: '李娜', lines: [{ id: 'eggtart', q: 4, why: 'expired' }] },
  { no: 'BS1110-01', kind: 'loss', st: 'done', time: '昨天 20:45', who: '张伟', lines: [{ id: 'baguette', q: 2, why: 'damaged' }] },
];

/** 库存流水：每次过账、卖出、退货一条（商品库存页的「最近变动」） */
export interface Move { time: string; id: string; delta: number; kind: 'receive' | 'count' | 'loss' | 'sale' | 'return' | 'init'; ref: string }
const INITIAL_MOVES: Move[] = [
  { time: '08:40', id: 'cola', delta: 48, kind: 'receive', ref: 'RK1111-02' }, { time: '08:40', id: 'water', delta: 72, kind: 'receive', ref: 'RK1111-02' }, { time: '08:40', id: 'tea', delta: 24, kind: 'receive', ref: 'RK1111-02' },
  { time: '09:40', id: 'chips', delta: -2, kind: 'sale', ref: '#R0152' }, { time: '09:58', id: 'chips', delta: 1, kind: 'return', ref: '#R0152' }, { time: '10:21', id: 'milk', delta: -1, kind: 'sale', ref: '#R0157' }, { time: '10:21', id: 'cola', delta: -2, kind: 'sale', ref: '#R0157' },
  { time: '昨天 20:45', id: 'baguette', delta: -2, kind: 'loss', ref: 'BS1110-01' },
];
interface SS {
  moves: Move[];
  book: StockMap; docs: StockDoc[]; kind: DocKind; sel: string; filter: string;
  set: (p: Partial<SS>) => void;
}
export const useStock = create<SS>((set) => ({ moves: INITIAL_MOVES.slice(), book: { ...INITIAL_BOOK }, docs: INITIAL_DOCS.map((d) => ({ ...d, lines: d.lines.map((l) => ({ ...l })) })), kind: 'receive', sel: 'RK1111-03', filter: 'all', set: (p) => set(p) }));
export const docOf = (no: string) => useStock.getState().docs.find((d) => d.no === no);
const patch = (no: string, p: Partial<StockDoc>) => { const s = useStock.getState(); s.set({ docs: s.docs.map((d) => (d.no === no ? { ...d, ...p } : d)) }); };
const seq: Record<string, number> = { receive: 4, count: 2, loss: 2 };
const PREFIX: Record<DocKind, string> = { receive: 'RK1111-', count: 'PD1111-', loss: 'BS1111-', transfer: 'DB1111-' };

/** 记流水（最新在前） */
function log(items: { id: string; delta: number }[], kind: Move['kind'], ref: string) { const s = useStock.getState(); s.set({ moves: [...items.map((x) => ({ time: '10:30', id: x.id, delta: x.delta, kind, ref })), ...s.moves] }); }
/** 某商品的流水 */
export const movesOf = (moves: Move[], id: string) => moves.filter((m) => m.id === id);

/* ---------- 单据动作 ---------- */
export function newDoc(kind: DocKind) {
  const s = useStock.getState(), no = PREFIX[kind] + String(seq[kind]++).padStart(2, '0');
  const d: StockDoc = { no, kind, st: kind === 'count' ? 'counting' : 'draft', time: '10:30', who: '李娜', lines: [], supplier: kind === 'receive' ? '城北乳业' : undefined, scope: kind === 'count' ? '全店' : undefined };
  s.set({ docs: [d, ...s.docs], sel: no, kind });
}
/** 扫一件进单据：同商品合并；盘点时第一次扫到默认填账面数（再按实盘改） */
export function addToDoc(no: string, id: string, q = 1) {
  const d = docOf(no); if (!d) return; const book = useStock.getState().book;
  const hit = d.lines.find((l) => l.id === id);
  const lines = hit ? d.lines.map((l) => (l.id === id ? { ...l, q: round2(l.q + q) } : l)) : [{ id, q: d.kind === 'count' ? book[id] ?? 0 : q, why: d.kind === 'loss' ? (isNear(id) ? 'expired' as const : 'damaged' as const) : undefined }, ...d.lines];
  patch(no, { lines });
}
export const setQty = (no: string, id: string, q: number) => { const d = docOf(no); if (d) patch(no, { lines: d.lines.map((l) => (l.id === id ? { ...l, q: Math.max(0, round2(q)) } : l)) }); };
export const removeLine = (no: string, id: string) => { const d = docOf(no); if (d) patch(no, { lines: d.lines.filter((l) => l.id !== id) }); };
export const setWhy = (no: string, id: string, why: LossWhy) => { const d = docOf(no); if (d) patch(no, { lines: d.lines.map((l) => (l.id === id ? { ...l, why } : l)) }); };
export const voidDoc = (no: string) => patch(no, { st: 'void' });

/** 盘点差异（只看盘到的商品） */
export function docDiff(d: StockDoc, live: StockMap) {
  const book = d.snap || live;
  const counted = Object.fromEntries(d.lines.map((l) => [l.id, l.q])), sub = Object.fromEntries(d.lines.map((l) => [l.id, book[l.id] ?? 0]));
  return countDiff(sub, counted);
}
/** 确认入库 / 报损：过账；盘点：先提交审批，主管通过后过账 */
export function postDoc(no: string) {
  const s = useStock.getState(), d = docOf(no); if (!d) return;
  if (d.kind === 'receive') { s.set({ book: receive(s.book, d.lines) }); log(d.lines.map((l) => ({ id: l.id, delta: l.q })), 'receive', no); patch(no, { st: 'done' }); }
  else if (d.kind === 'loss') { if (d.st === 'draft') { s.set({ book: writeOff(s.book, d.lines) }); log(d.lines.map((l) => ({ id: l.id, delta: -l.q })), 'loss', no); } patch(no, { st: 'done' }); }
  else if (d.kind === 'count' && d.st === 'counting') { const df = docDiff(d, s.book); patch(no, { st: 'pending', diff: { gain: df.gain, loss: df.loss }, snap: Object.fromEntries(d.lines.map((l) => [l.id, s.book[l.id] ?? 0])) }); }
  else if (d.kind === 'count' && d.st === 'pending') {
    const counted = Object.fromEntries(d.lines.map((l) => [l.id, l.q]));
    log(d.lines.map((l) => ({ id: l.id, delta: Math.round((l.q - (s.book[l.id] ?? 0)) * 1000) / 1000 })).filter((x) => x.delta), 'count', no);
    s.set({ book: applyCount(s.book, counted) }); patch(no, { st: d.diff && (d.diff.gain || d.diff.loss) ? 'variance' : 'done' });
  }
}
export const docQty = (d: StockDoc) => qtyTotal(d.lines);
export const docCost = (d: StockDoc) => costTotal(d.lines, costOf);

/* ---------- 订阅：收银卖出扣库存 · 退货回库 · 退货不回库生成报损单（已过账） ---------- */
on('stock.changed', (e) => {
  const s = useStock.getState();
  if (e.reason === 'loss') {
    const no = PREFIX.loss + String(seq.loss++).padStart(2, '0');
    s.set({ docs: [{ no, kind: 'loss', st: 'done', time: '10:30', who: '系统', ref: e.ref, lines: e.ids.map((id) => ({ id, q: e.delta?.[id] ?? 1, why: 'return' as const })) }, ...s.docs] });
    return;
  }
  if (e.delta) { s.set({ book: applyDelta(s.book, e.delta) }); log(Object.keys(e.delta).map((id) => ({ id, delta: e.delta![id] })), e.reason === 'return' ? 'return' : 'sale', e.ref || ''); }
});
