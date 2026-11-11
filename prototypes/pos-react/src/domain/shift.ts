// 班次服务：钱箱实盘 · 应有现金 · 盘差。两业态共用。
import { round2 } from './money';

export const DENOMS = [100, 50, 20, 10, 5, 1] as const;
/** 按面额张数算实盘金额 */
export const drawerTotal = (counts: number[], denoms: readonly number[] = DENOMS) => round2(counts.reduce((a, n, i) => a + n * (denoms[i] || 0), 0));
/** 应有现金 = 备用金 + 现金收入 − 现金退款 − 中途取款 */
export const expectedCash = (c: { float: number; cashIn: number; cashRefund?: number; payout?: number }) => round2(c.float + c.cashIn - (c.cashRefund || 0) - (c.payout || 0));
/** 盘差：实盘 − 应有；容差内算平账 */
export function variance(expected: number, counted: number, tolerance = 0) {
  const diff = round2(counted - expected);
  return { diff, st: Math.abs(diff) <= tolerance ? 'ok' as const : diff < 0 ? 'short' as const : 'over' as const };
}
