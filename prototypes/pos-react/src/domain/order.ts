// 订单服务（餐饮订单行）：行状态机 · 加菜 / 加减份数 · 送厨 · 催菜 · 退菜 · 拆单 · 改价规则 · 库存余量。
// 只处理数据，返回新数组；不发 toast、不碰界面（提示文案由调用方按返回的原因 key 去取）。
import { round2 } from './money';

export type LineSt = 'new' | 'cook' | 'done' | 'rush' | 'gift' | 'void';
export interface Line { n: string; q: number; p: number; st: LineSt; sub?: string; rp?: boolean }
export interface Sellable { n: string; p: number; s: number | null; spec?: 1; timed?: string }

/** 行状态机：待下单 → 制作中 → 已上菜；制作中可催菜；已下单的行可赠送、退菜（需授权由界面把关） */
export const LINE_FLOW: Record<LineSt, LineSt[]> = { new: ['cook'], cook: ['rush', 'done', 'gift', 'void'], rush: ['done', 'gift', 'void'], done: ['gift', 'void'], gift: ['void'], void: [] };
export const canTransit = (from: LineSt, to: LineSt) => LINE_FLOW[from].includes(to);
const live = (l: Line) => l.st !== 'void';

export const qtyOf = (lines: Line[], n: string) => lines.reduce((q, l) => (l.n === n && live(l) ? q + l.q : q), 0);
export const newCount = (lines: Line[]) => lines.reduce((c, l) => (l.st === 'new' ? c + l.q : c), 0);
export const dishCount = (lines: Line[]) => lines.filter(live).length;
/** 今日余量：null = 不限量 */
export const stockLeft = (d: Sellable, lines: Line[]) => (d.s == null ? null : Math.max(0, d.s - qtyOf(lines, d.n)));

/** 点一下菜品：不能直接加的返回原因（沽清 / 限时 / 要先选规格） */
export function addCheck(d: Sellable, lines: Line[]): 'soldOut' | 'timed' | 'spec' | null {
  if (stockLeft(d, lines) === 0) return 'soldOut';
  if (d.timed) return 'timed';
  if (d.spec) return 'spec';
  return null;
}
/** 加一份：同名、待下单、无做法的行合并数量 */
export function addItem(lines: Line[], d: Sellable): Line[] {
  const i = lines.findIndex((l) => l.n === d.n && l.st === 'new' && !l.sub);
  if (i > -1) return lines.map((l, j) => (j === i ? { ...l, q: l.q + 1 } : l));
  return [...lines, { n: d.n, q: 1, p: d.p, st: 'new' }];
}
export const addWithSpec = (lines: Line[], d: Sellable, sub: string): Line[] => [...lines, { n: d.n, q: 1, p: d.p, st: 'new', sub }];
export const incAt = (lines: Line[], i: number): Line[] => lines.map((l, j) => (j === i ? { ...l, q: l.q + 1 } : l));
/** 减一份，减到 0 删行 */
export const decAt = (lines: Line[], i: number): Line[] => (lines[i].q <= 1 ? lines.filter((_l, j) => j !== i) : lines.map((l, j) => (j === i ? { ...l, q: l.q - 1 } : l)));
export const clearNew = (lines: Line[]) => lines.filter((l) => l.st !== 'new');
/** 送厨：待下单 → 制作中 */
export const sendNew = (lines: Line[]) => ({ lines: lines.map((l) => (l.st === 'new' ? { ...l, st: 'cook' as const } : l)), n: newCount(lines) });
/** 催菜：只有制作中的行能催 */
export function rush(lines: Line[], idx: number[]) { let n = 0; const out = lines.map((l, j) => (idx.includes(j) && canTransit(l.st, 'rush') ? (n++, { ...l, st: 'rush' as const }) : l)); return { lines: out, n }; }
/** 拆单：选中的行拆走 */
export const removeAt = (lines: Line[], idx: number[]) => lines.filter((_l, j) => !idx.includes(j));
/** 已下单可点的行（批量 / 拆单用） */
export const pickable = (lines: Line[]) => lines.map((l, j) => (l.st !== 'new' && l.st !== 'void' ? j : -1)).filter((j) => j > -1);
export function voidAt(lines: Line[], i: number) { return i > -1 && canTransit(lines[i].st, 'void') ? lines.map((l, j) => (j === i ? { ...l, st: 'void' as const } : l)) : lines; }

/* 改价：三种方式 · 授权门槛（低于 8 折或一次减超 ¥20 要主管授权） */
export type RepriceMode = 'price' | 'disc' | 'off';
export function repriced(p: number, mode: RepriceMode, v: number | null) { if (v == null) return p; return mode === 'disc' ? Math.round(p * v * 10) / 100 : mode === 'off' ? Math.max(0, p - v) : v; }
export function repriceNeedsAuth(p: number, mode: RepriceMode, v: number | null) { const np = repriced(p, mode, v); return np < p * 0.8 - 1e-9 || p - np > 20; }
export const applyReprice = (lines: Line[], i: number, mode: RepriceMode, v: number | null): Line[] => lines.map((l, j) => (j === i ? { ...l, p: round2(repriced(l.p, mode, v)), rp: true } : l));
