// 计价引擎（方案：零售 §八 · 架构文档 §二）：公共流水线 + 业态规则。
// 小计 → 行级促销 → 会员价 → 费用 → 平台券 → 整单活动 → 券 → 人工折扣 → 抹零 → 应收 → 已收 / 待收
// 输入是「价格行」，由各业态的适配器从自己的订单行 / 购物车行算出来（餐饮 lib/pricing.ts，零售 retail/data.ts）。
import { money, round2 } from './money';

/** 价格行：gross 已是这一行的原价小计（赠送 / 已退行传 0）；promo 行级促销减额；memberSave 会员价减额 */
export interface PriceLine { gross: number; promo?: number; memberSave?: number; pcs?: number }
export interface Coupon { id: string; name: string; kind: string; amt?: number; min?: number; need?: string; disc?: number; cap?: number }
export interface Voucher { face: number; items?: string[] }
export interface PricingInput {
  lines: PriceLine[]; member?: boolean;
  /** 平台券：[券, 张数]，已按本单可用性过滤（不可用的传 null） */ vouchers?: [Voucher | null, number][];
  campaignOn?: boolean; /** 已选且可用的本店券 */ coupon?: Coupon | null; manual?: number; paid?: number[];
}
export interface PricingRules {
  key: 'fnb' | 'retail';
  /** 服务费率（按小计），并按 svcStep 取整 */ serviceRate: number; svcStep: number;
  /** 整单活动减额（null = 本业态没有整单活动，促销在行上） */ campaign: ((sub: number) => number) | null;
  /** B01：参加活动后还能不能用券 */ couponWithCampaign: boolean;
  /** B08：用了券 / 平台券后还能不能抹零 */ roundWithCoupon: boolean;
  /** 抹零到：元 1 · 角 0.1 */ roundTo: 1 | 0.1;
}
export const FNB_RULES: PricingRules = { key: 'fnb', serviceRate: 0.05, svcStep: 0.1, campaign: (sub) => Math.min(20, round2(sub * 0.2)), couponWithCampaign: false, roundWithCoupon: false, roundTo: 1 };
export const RETAIL_RULES: PricingRules = { key: 'retail', serviceRate: 0, svcStep: 0.1, campaign: null, couponWithCampaign: true, roundWithCoupon: true, roundTo: 0.1 };
/** 按业态配置取规则（BIZ[biz].pricing 是这里的键） */
export const RULES: Record<PricingRules['key'], PricingRules> = { fnb: FNB_RULES, retail: RETAIL_RULES };
export const rulesOf = (k: PricingRules['key']) => RULES[k];

export function couponAmt(c: Coupon, sub: number) { return c.disc ? Math.min(c.cap!, round2(sub * c.disc)) : c.amt!; }
/** 券不可用的原因（空 = 可用）；has(n) 判断本单有没有某菜 / 商品 */
export function couponBlock(c: Coupon, sub: number, has: (n: string) => boolean): { key: string; params?: Record<string, string> } | null {
  if (c.min && sub < c.min) return { key: 'short', params: { m: money(c.min - sub) } };
  if (c.need && !has(c.need)) return { key: 'noItem' };
  return null;
}
/** 套餐类平台券：本单缺券里的菜就不可用 */
export function voucherMissing(v: Voucher, has: (n: string) => boolean): string[] { return v.items ? v.items.filter((n) => !has(n)) : []; }

export function totals(inp: PricingInput, R: PricingRules) {
  let sub = 0, promo = 0, memberSave = 0, pcs = 0;
  inp.lines.forEach((l) => { sub += l.gross; promo += l.promo || 0; if (inp.member) memberSave += l.memberSave || 0; pcs += l.pcs ?? 0; });
  sub = round2(sub); promo = round2(promo); memberSave = round2(memberSave);
  const sk = Math.round(1 / R.svcStep), svc = R.serviceRate ? Math.round(sub * R.serviceRate * sk) / sk : 0;
  const base = round2(sub - promo - memberSave);
  // 平台券：不与活动、本店券、人工折扣同享，不找零（抵扣封顶到应付）
  let pvFace = 0; (inp.vouchers || []).forEach(([v, n]) => { if (v) pvFace += v.face * n; });
  const pv = Math.min(pvFace, round2(base + svc));
  const act = !pv && R.campaign && inp.campaignOn !== false && sub ? R.campaign(sub) : 0;
  const coupon = !pv && (R.couponWithCampaign || !act) && inp.coupon ? inp.coupon : null; const cp = coupon ? couponAmt(coupon, sub) : 0;
  const manual = !pv && !act && !cp ? inp.manual || 0 : 0;                       // B07：用了活动或券不能再人工打折
  const raw = round2(base + svc - pv - act - cp - manual);
  const k = Math.round(1 / R.roundTo), round = !R.roundWithCoupon && (cp || pv) ? 0 : round2(raw - Math.floor(round2(raw * k)) / k);
  const due = round2(raw - round);
  const paid = round2((inp.paid || []).reduce((a, x) => a + x, 0));
  return { sub, svc: round2(svc), promo, memberSave, pv, pvOver: round2(pvFace - pv), act, cp, coupon, manual, mem: round2(act + cp + manual), round, due, paid, rest: Math.max(0, round2(due - paid)), pcs, save: round2(promo + memberSave + act + cp + manual + round + pv) };
}
export type Totals = ReturnType<typeof totals>;
