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

/* ---------- 价签（零售方案 §5.1） ---------- */
/** EAN-13 校验位（前 12 位） */
export function ean13Check(d12: string) { const s = d12.split('').reduce((a, c, i) => a + Number(c) * (i % 2 ? 3 : 1), 0); return String((10 - (s % 10)) % 10); }
/** 任意商品码转成可打印的 EAN-13：13 位原样；12 位补校验；店内码 / PLU 按「2 + 补零到 11 位」再补校验 */
export function toEan13(raw: string) {
  const d = raw.replace(/\D/g, '');
  if (d.length === 13) return d;
  if (d.length === 12) return d + ean13Check(d);
  const body = ('2' + d.padStart(11, '0')).slice(0, 12); return body + ean13Check(body);
}
const L = ['0001101', '0011001', '0010011', '0111101', '0100011', '0110001', '0101111', '0111011', '0110111', '0001011'];
const G = ['0100111', '0110011', '0011011', '0100001', '0011101', '0111001', '0000101', '0010001', '0001001', '0010111'];
const R = ['1110010', '1100110', '1101100', '1000010', '1011100', '1001110', '1010000', '1000100', '1001000', '1110100'];
const PARITY = ['LLLLLL', 'LLGLGG', 'LLGGLG', 'LLGGGL', 'LGLLGG', 'LGGLLG', 'LGGGLL', 'LGLGLG', 'LGLGGL', 'LGGLGL'];
/** EAN-13 条模块：95 位 0/1（1 = 黑条），界面按位画矩形 */
export function ean13Bars(code13: string) {
  const d = code13.split('').map(Number), par = PARITY[d[0]];
  let s = '101';
  for (let i = 1; i <= 6; i++) s += (par[i - 1] === 'L' ? L : G)[d[i]];
  s += '01010';
  for (let i = 7; i <= 12; i++) s += R[d[i]];
  return s + '101';
}
/** 价签上的单位价：称重商品按 500g 标；其余按件不另标 */
export function labelUnit(p: number, weigh?: 'kg') { return weigh ? { per: '500g', price: Math.round(p * 50) / 100 } : null; }
