// 商品服务（零售为主）：扫到的码是什么 · 称重码解析 · 按码找商品 · 库存状态。
import { round2 } from './money';

export type CodeKind = 'weigh' | 'plu' | 'instore' | 'std' | 'member' | 'pay' | 'unknown';
/** 判断码的类型（方案 §3.2 扫码分流）。称重码 18 位、2 开头；店内码 7 位 2 开头；标准码 EAN-13 / UPC-A；付款码 18 位 10–15 开头；会员码 M 开头 */
export function classifyCode(raw: string): CodeKind {
  const c = raw.replace(/\s/g, '');
  if (/^M\d+$/i.test(c)) return 'member';
  if (/^1[0-5]\d{16}$/.test(c)) return 'pay';
  if (/^2\d{17}$/.test(c)) return 'weigh';
  if (/^PLU\d+$/i.test(c)) return 'plu';
  if (/^2\d{6}$/.test(c)) return 'instore';
  if (/^\d{12,13}$/.test(c)) return 'std';
  return 'unknown';
}
/** 称重码 18 位：2 + 商品码 6 + 金额 5（分）+ 重量 5（克）+ 校验 1；格式以设置 › 商品 › 条码规则为准 */
export function parseWeighCode(raw: string): { plu: string; amount: number; weight: number } | null {
  const c = raw.replace(/\s/g, ''); if (classifyCode(c) !== 'weigh') return null;
  return { plu: c.slice(1, 7), amount: round2(Number(c.slice(7, 12)) / 100), weight: Number(c.slice(12, 17)) / 1000 };
}
export interface Coded { code: string }
export const findByCode = <T extends Coded>(items: T[], raw: string) => { const c = raw.replace(/\s/g, ''); return items.find((x) => x.code.replace(/\s/g, '') === c); };

/** 库存状态：缺货 / 仅剩 N（≤ low）/ 充足；null = 不管库存 */
export function stockState(left: number | null | undefined, low = 5): 'out' | 'low' | 'ok' | null {
  if (left == null) return null; return left <= 0 ? 'out' : left <= low ? 'low' : 'ok';
}
