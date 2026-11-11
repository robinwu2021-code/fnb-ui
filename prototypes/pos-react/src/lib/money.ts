// 金额工具：格式化在领域层 domain/money.ts，这里转发给界面用，并提供按地区换币种符号的两个小工具。
import { currency, money, round2, setCurrency, type Currency } from '@/domain/money';
export { money, round2, currency, setCurrency };
/** 门店地区 → 币种（本期只换符号和小数位，不做汇率换算） */
export const REGION_CURRENCY: Record<string, Currency> = {
  tw: { code: 'TWD', sym: 'NT$', dp: 0, glyph: '$' },
  uae: { code: 'AED', sym: 'AED ', dp: 2, glyph: 'د.إ' },
  cn: { code: 'CNY', sym: '¥', dp: 2, glyph: '¥' },
};
/** 当前币种符号（原来写死 '¥' + x 的地方用它） */
export const cs = () => currency().sym;
/** 把文字里的 ¥ 换成当前币种：「¥236.00」→「NT$236」/「AED 236.00」，「满 200 减 ¥15」同理；不含 ¥ 的原样返回 */
export function cur(s: string): string {
  if (!s || s.indexOf('¥') < 0) return s;
  const c = currency();
  return s.replace(/¥(\s?)([\d,]+(?:\.\d+)?)?/g, (_m, _sp, num?: string) => (num == null ? c.sym.trimEnd() : c.sym + (c.dp === 0 ? num.replace(/\.\d+$/, '') : num)));
}
/** 展示组件用：传进来是纯文字就换币种（示例数据里的「+¥3」「最多减 ¥30」等），别的原样返回 */
export function curNode<T>(n: T): T { return (typeof n === 'string' ? cur(n) : n) as T; }
