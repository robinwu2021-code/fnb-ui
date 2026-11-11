// 会员营销服务：会员分层筛选 · 标签 · 储值方案与到账 · 活动状态流转。
// 会员在原型里是元组 [称呼, 手机尾号, 分层, 标签, 余额, 到店次数, 消费额, 最近到店, 来源, 线索标记]，这里只用到分层、标签、余额、线索标记。

/** 分层筛选：「全部」· 「线索」只看线索会员 · 其余按分层，线索会员不计入任何分层 */
export function matchLayer(filter: string, layer: string, lead: boolean) {
  if (filter === '全部') return true;
  if (filter === '线索') return lead;
  return layer === filter && !lead;
}
/** 标签开关：有就去掉，没有就加；最多 max 个 */
export function toggleTag(tags: string[], tag: string, max = 10): string[] {
  if (tags.includes(tag)) return tags.filter((x) => x !== tag);
  return tags.length >= max ? tags : [...tags, tag];
}

/* 储值 */
export interface Plan { n: string; pay: number; gift: number; on: boolean }
export const activePlans = (plans: Plan[]) => plans.filter((p) => p.on);
/** 收银台选第 i 个启用的方案；越界回到第一个 */
export const pickPlan = (plans: Plan[], i: number) => activePlans(plans)[i] || plans[0];
/** 到账：实付 + 赠送都进余额 */
export const recharge = (balance: number, p: Plan) => balance + p.pay + p.gift;
export const togglePlan = (plans: Plan[], i: number) => plans.map((p, j) => (j === i ? { ...p, on: !p.on } : p));

/* 营销活动：草稿 → 进行中 ⇄ 已暂停 → 已结束（结束后不能恢复） */
export type CampSt = 'draft' | 'running' | 'paused' | 'ended';
export const CAMP_FLOW: Record<CampSt, CampSt[]> = { draft: ['running'], running: ['paused', 'ended'], paused: ['running', 'ended'], ended: [] };
export const canCamp = (from: string, to: string) => !!CAMP_FLOW[from as CampSt]?.includes(to as CampSt);
