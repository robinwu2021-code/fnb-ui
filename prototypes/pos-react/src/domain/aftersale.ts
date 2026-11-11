// 售后服务：平台券撤销核销的规则（餐饮订单页已用）· 零售退货（可退数量、优惠分摊、退款金额、是否回库存）。
import { round2 } from './money';

/* ---------- 平台券撤销核销（平台券方案 §七） ---------- */
/** 撤销前置判断：done 已撤销 · late 超 24 小时只能去平台后台退 · auth 他人核销要主管授权 · '' 本人 24 小时内直接撤 */
export function voucherUndo(r: { undone: boolean; hoursAgo: number; operator: string }, me: string, limitHours = 24): 'done' | 'late' | 'auth' | '' {
  if (r.undone) return 'done';
  if (r.hoursAgo >= limitHours) return 'late';
  return r.operator !== me ? 'auth' : '';
}

/* ---------- 零售退货（零售方案 §4.2） ---------- */
export interface SoldLine { key: string; q: number; amt: number; returned?: number; weighed?: boolean }
/** 还能退几件；称重行只能整行退（返回 1 或 0） */
export const refundableQty = (l: SoldLine) => (l.weighed ? ((l.returned || 0) > 0 ? 0 : 1) : Math.max(0, l.q - (l.returned || 0)));
/** 整单优惠按行实付比例分摊到行：返回每行分摊后的实付；尾差记在金额最大的一行，保证合计等于整单实付 */
export function allocate(lines: SoldLine[], orderDiscount: number): Record<string, number> {
  const gross = lines.reduce((a, l) => a + l.amt, 0), out: Record<string, number> = {};
  if (!gross) { lines.forEach((l) => (out[l.key] = 0)); return out; }
  let given = 0;
  lines.forEach((l) => { out[l.key] = round2(l.amt - (orderDiscount * l.amt) / gross); given += out[l.key]; });
  const target = round2(gross - orderDiscount), big = lines.reduce((m, l) => (l.amt > m.amt ? l : m), lines[0]);
  out[big.key] = round2(out[big.key] + target - given);
  return out;
}
/** 退这些（行 key → 件数）应退多少：按分摊后的单件实付 */
export function refundAmount(lines: SoldLine[], orderDiscount: number, pick: Record<string, number>): number {
  const net = allocate(lines, orderDiscount);
  return round2(lines.reduce((a, l) => { const n = Math.min(pick[l.key] || 0, refundableQty(l)); return a + (l.weighed ? (n ? net[l.key] : 0) : (net[l.key] / l.q) * n); }, 0));
}
export type ReturnReason = 'quality' | 'unwanted' | 'wrong' | 'other';
/** 是否回库存：质量问题默认不回（记报损），其余回 */
export const restockByDefault = (why: ReturnReason) => why !== 'quality';
/** 无单退货：需授权，只能退现金或储值 */
export const NO_RECEIPT = { needsAuth: true, methods: ['cash', 'sv'] as const };
/** 退货后把件数记到原单行（不超过可退数量） */
export function applyReturn<T extends SoldLine>(lines: T[], pick: Record<string, number>): T[] {
  return lines.map((l) => { const n = Math.min(pick[l.key] || 0, refundableQty(l)); return n ? { ...l, returned: (l.returned || 0) + (l.weighed ? 1 : n) } : l; });
}
/** 原单状态：没退过 paid · 退了一部分 partial_return · 全退完 returned */
export function saleStatus(lines: SoldLine[]): 'paid' | 'partial_return' | 'returned' {
  const any = lines.some((l) => (l.returned || 0) > 0), all = lines.every((l) => refundableQty(l) === 0);
  return all ? 'returned' : any ? 'partial_return' : 'paid';
}
/** 渠道限制：即时零售平台单要在平台发起退款，门店不能直接退 */
export const channelBlock = (ch: string) => (ch === 'platform' ? 'platform' : null);
/** 选了几件 */
export const pickedPieces = (lines: SoldLine[], pick: Record<string, number>) => lines.reduce((a, l) => a + Math.min(pick[l.key] || 0, refundableQty(l)), 0);

/* ---------- 换货（零售方案 §4.3）：退回的商品折成抵扣额，带到收银；新商品应收减抵扣 ---------- */
/** 补收 pay（新商品更贵）或退差价 refund（新商品更便宜），两者至多一个非 0 */
export function exchangeSettle(newDue: number, credit: number) {
  const d = round2(newDue - credit);
  return { pay: Math.max(0, d), refund: Math.max(0, -d), even: d === 0 };
}
