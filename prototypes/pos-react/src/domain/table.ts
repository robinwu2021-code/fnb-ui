// 桌台服务（只有餐饮装载）：桌台状态流转。开台 → 待点餐；结账 → 待清台；清台 / 启用 → 空闲；拼桌。
export type TableSt = 'free' | 'booked' | 'seated' | 'dining' | 'late' | 'due' | 'partial' | 'to_clear' | 'merged' | 'shared' | 'disabled';
export interface TableLike { id: string; st: TableSt }
export const isBusy = (st: TableSt) => /^(seated|dining|late|due|partial|shared)$/.test(st);
/** 允许的流转；界面按钮是否出现也按这里判断 */
export const TABLE_FLOW: Partial<Record<string, TableSt[]>> = {
  free: ['seated', 'booked', 'disabled'], booked: ['seated', 'free'], seated: ['dining', 'shared', 'to_clear'], dining: ['due', 'late', 'shared', 'to_clear'], late: ['dining', 'due', 'to_clear'],
  due: ['partial', 'to_clear'], partial: ['to_clear'], shared: ['due', 'to_clear'], to_clear: ['free'], disabled: ['free'], merged: ['free'],
};
export const canTableTransit = (from: TableSt, to: TableSt) => !!TABLE_FLOW[from]?.includes(to);
/** 改一张桌的状态（不检查流转，演示数据里有些跳步；需要检查时先调 canTableTransit） */
export const setTable = <T extends TableLike>(tables: T[], id: string, st: TableSt): T[] => tables.map((t) => (t.id === id ? { ...t, st } : t));
