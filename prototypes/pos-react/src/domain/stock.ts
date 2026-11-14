// 库存服务（零售为主，零售方案 §5.2）：入库累加 · 盘点差异 · 临期判断 · 低库存。界面在第 5 期接，这里先把规则定下来并自测。

export type StockMap = Record<string, number>;
/** 入库：按扫码清单累加数量 */
export function receive(stock: StockMap, lines: { id: string; q: number }[]): StockMap {
  const out = { ...stock }; lines.forEach((l) => (out[l.id] = (out[l.id] || 0) + l.q)); return out;
}
/** 卖出 / 退货回库：q 为负表示回库 */
export const deduct = (stock: StockMap, id: string, q: number): StockMap => ({ ...stock, [id]: (stock[id] || 0) - q });
/** 盘点差异：实盘 − 账面；只列有差异的；盘盈 / 盘亏件数合计 */
export function countDiff(book: StockMap, counted: StockMap) {
  const rows = Object.keys({ ...book, ...counted }).map((id) => ({ id, book: book[id] || 0, counted: counted[id] ?? 0, diff: (counted[id] ?? 0) - (book[id] || 0) })).filter((r) => r.diff !== 0);
  return { rows, gain: rows.filter((r) => r.diff > 0).reduce((a, r) => a + r.diff, 0), loss: rows.filter((r) => r.diff < 0).reduce((a, r) => a - r.diff, 0) };
}
/** 临期：距到期 ≤ days 天（含当天）；已过期也算 */
export function nearExpiry(expiry: string, today: string, days = 3) {
  const d = (Date.parse(expiry) - Date.parse(today)) / 86400000; return d <= days;
}
export const lowStock = (left: number, safety: number) => left <= safety;

/** 盘点过账：实盘覆盖账面（只改盘到的商品） */
export const applyCount = (book: StockMap, counted: StockMap): StockMap => ({ ...book, ...counted });
/** 报损：按清单扣减 */
export const writeOff = (book: StockMap, lines: { id: string; q: number }[]): StockMap => lines.reduce((b, l) => deduct(b, l.id, l.q), book);
/** 按变化量批量改库存（销售为负、退货回库为正） */
export const applyDelta = (book: StockMap, delta: StockMap): StockMap => { const out = { ...book }; Object.keys(delta).forEach((id) => (out[id] = Math.round(((out[id] || 0) + delta[id]) * 1000) / 1000)); return out; };
/** 入库金额：件数 × 进价 */
export const costTotal = (lines: { id: string; q: number }[], cost: (id: string) => number) => Math.round(lines.reduce((a, l) => a + l.q * cost(l.id), 0) * 100) / 100;
/** 单据件数合计 */
export const qtyTotal = (lines: { q: number }[]) => Math.round(lines.reduce((a, l) => a + l.q, 0) * 1000) / 1000;
