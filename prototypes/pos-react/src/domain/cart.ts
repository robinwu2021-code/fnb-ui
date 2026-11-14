// 购物车服务（零售）：扫码加购合并 · 加减数量 · 撤销 · 行级促销规则 · 件数。和餐饮订单行分开，因为零售没有制作链路。
import { round2 } from './money';

/** w = 称重重量（kg）；sku = 规格文字；custom = 临时商品 */
export interface CartLine { key: number; id: string; q: number; w?: number; sku?: [string, string]; gift?: boolean; custom?: { n: string; p: number } }
export type NewCartLine = Omit<CartLine, 'key'>;
const sameSku = (a?: [string, string], b?: [string, string]) => (a?.[0] ?? '') === (b?.[0] ?? '');

/** 扫一件：同商品同规格、非称重、非赠送的行合并数量并移到最上；称重行和临时商品每次新起一行。返回新车和「刚加的行」key */
export function scanAdd(lines: CartLine[], nl: NewCartLine, newKey: () => number): { lines: CartLine[]; last: number } {
  const hit = nl.w == null && !nl.custom ? lines.find((l) => l.id === nl.id && l.w == null && !l.gift && !l.custom && sameSku(l.sku, nl.sku)) : undefined;
  if (hit) return { lines: [{ ...hit, q: hit.q + nl.q }, ...lines.filter((l) => l !== hit)], last: hit.key };
  const key = newKey(); return { lines: [{ ...nl, key }, ...lines], last: key };
}
/** 加减数量：减到 0 删行；称重行不能加减 */
export function bump(lines: CartLine[], key: number, d: number): CartLine[] {
  const l = lines.find((x) => x.key === key); if (!l || l.w != null) return lines;
  return l.q + d <= 0 ? lines.filter((x) => x.key !== key) : lines.map((x) => (x.key === key ? { ...x, q: x.q + d } : x));
}
/** 撤销刚扫的：普通行减一件，称重行整行删 */
export const undoLast = (lines: CartLine[], key: number): CartLine[] => { const l = lines.find((x) => x.key === key); return !l ? lines : l.w != null ? lines.filter((x) => x.key !== key) : bump(lines, key, -1); };
export const toggleGift = (lines: CartLine[], key: number) => lines.map((x) => (x.key === key ? { ...x, gift: !x.gift } : x));
export const removeLine = (lines: CartLine[], key: number) => lines.filter((x) => x.key !== key);
/** 件数：称重行算 1 件 */
export const pieces = (lines: CartLine[]) => lines.reduce((a, l) => a + (l.w != null ? 1 : l.q), 0);

/* 行级促销规则 */
export type LinePromo = 'half2';
/** 第二件半价：每满 2 件减半件 */
export function promoOff(kind: LinePromo | undefined, q: number, p: number) { return kind === 'half2' ? round2(Math.floor(q / 2) * p * 0.5) : 0; }
