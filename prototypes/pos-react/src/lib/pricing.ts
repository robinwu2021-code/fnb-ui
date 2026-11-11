// 餐饮计价适配器：把订单行、平台券、本店券换成领域计价引擎的输入，用 FNB_RULES 算（规则本身在 domain/pricing.ts）。
// 优惠规则（TDD-三行业优惠规则）：B01 参加活动就不能用券 · B07 用了活动或券不能再人工打折 · B08 用券后不能抹零；平台券不与活动、本店券、人工折扣同享，不找零
import { COUPONS, pvOf, type Coupon, type Line, type PVoucher } from '@/data/sample';
import { FNB_RULES, couponAmt, couponBlock, totals as engine, voucherMissing } from '@/domain/pricing';
export { qtyOf, newCount, dishCount } from '@/domain/order';

export interface PricingInput { lines: Line[]; pv: { id: string; n: number }[]; pAct: boolean; cpn: string; manual: number; paid: [string, number][] }
const served = (lines: Line[]) => (n: string) => lines.some((l) => l.n === n && l.st !== 'void' && l.st !== 'gift');
const ordered = (lines: Line[]) => (n: string) => lines.some((l) => l.n === n && l.st !== 'void');

export function pvWhy(v: PVoucher, lines: Line[]) { return voucherMissing(v, served(lines)).length ? '本单没有券里的菜：' + v.items!.join('、') : ''; }
export const cpAmt = (c: Coupon, sub: number) => couponAmt(c, sub);
export function cpWhy(c: Coupon, sub: number, lines: Line[]) {
  const b = couponBlock(c, sub, ordered(lines));
  return !b ? '' : b.key === 'short' ? '还差 ' + b.params!.m + ' 可用' : '本单没有可兑换的饮品';
}
const gross = (l: Line) => (l.st === 'gift' || l.st === 'void' ? 0 : l.p * l.q);
export function totals(s: PricingInput) {
  const sub = s.lines.reduce((a, l) => a + gross(l), 0);
  return engine({
    lines: s.lines.map((l) => ({ gross: gross(l), pcs: l.st === 'void' ? 0 : 1 })),
    vouchers: s.pv.map((x) => { const v = pvOf(x.id); return [v && !pvWhy(v, s.lines) ? v : null, x.n]; }),
    campaignOn: s.pAct,
    coupon: s.cpn ? COUPONS.find((x) => x.id === s.cpn && !cpWhy(x, sub, s.lines)) ?? null : null,
    manual: s.manual, paid: s.paid.map((p) => p[1]),
  }, FNB_RULES);
}
export type Totals = ReturnType<typeof totals>;
