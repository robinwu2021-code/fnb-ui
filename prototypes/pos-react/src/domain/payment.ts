// 支付服务：现金输入 · 快捷金额 · 找零 / 还差 · 组合支付 · 收款方式可用性。两业态共用。
import { money, round2 } from './money';

/** 数字键盘输入：退格、清空、只允许一个小数点、最多 8 位 */
export function keyInput(ent: string, k: string, max = 8): string {
  if (k === 'back') return ent.slice(0, -1);
  if (k === 'clr') return '';
  if (k === '.' && ent.includes('.')) return ent;
  if (ent.length >= max) return ent;
  return (ent === '0' && k !== '.' ? '' : ent) + k;
}
/** 快捷金额：正好 · 凑整十 · 凑整百 · 500，去重且不小于应收 */
export const quickAmounts = (due: number) => [due, Math.ceil(due / 10) * 10, Math.ceil(due / 100) * 100, 500].filter((v, i, a) => v >= due && a.indexOf(v) === i);
/** 收现金时默认实收：凑整百 */
export const defaultCash = (rest: number) => Math.ceil(rest / 100) * 100;
export const cashResult = (due: number, got: number) => ({ short: got < due ? round2(due - got) : 0, change: got >= due ? round2(got - due) : 0, ok: got >= due });

/** 组合支付：这一笔最多收到待收为止 */
export const takeAmount = (rest: number, got: number) => Math.min(rest, got || rest);
export type Paid = [string, number];
export const paidSum = (paid: Paid[]) => round2(paid.reduce((a, p) => a + p[1], 0));

/** 收款方式为什么不能用（null = 可用）：离线时扫码不可用；储值要先识别会员、余额够、不能和别的方式组合 */
export function methodBlock(m: string, c: { offline: boolean; member: boolean; balance: number; rest: number; paidCount: number }): { key: string; params?: Record<string, string> } | null {
  if (c.offline && (m === 'wx' || m === 'ali')) return { key: 'offline' };
  if (m === 'sv') {
    if (!c.member) return { key: 'svMember' };
    if (c.balance < c.rest) return { key: 'svShort', params: { m: money(round2(c.rest - c.balance)) } };
    if (c.paidCount) return { key: 'svMix' };
  }
  return null;
}
