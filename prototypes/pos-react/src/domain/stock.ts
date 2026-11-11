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
