// 零售商品工作台 · 状态（方案 §5.1）：商品资料改价 → 自动进「待打价签」；价签模板与打印队列。
import { create } from 'zustand';
import { item } from './data';

export type GoodsSub = 'items' | 'stock' | 'labels';
export type LabelTpl = '60x40' | '40x30' | 'shelf';
export interface LabelJob { id: string; why: 'price' | 'new' | 'manual'; old?: number }
interface GS {
  sub: GoodsSub; sel: string; cat: string; sf: 'all' | 'low' | 'out' | 'near';
  queue: LabelJob[]; picked: string[]; tpl: LabelTpl; focus: string; printed: number;
  set: (p: Partial<GS>) => void;
}
const Q0: LabelJob[] = [{ id: 'milk', why: 'price', old: 42.9 }, { id: 'croissant', why: 'price', old: 6.5 }, { id: 'tee', why: 'new' }];
export const useGoods = create<GS>((set) => ({ sub: 'items', sel: 'milk', cat: 'common', sf: 'all', queue: Q0.slice(), picked: Q0.map((q) => q.id), tpl: '60x40', focus: 'milk', printed: 0, set: (p) => set(p) }));

/** 改售价：原型直接改商品资料（收银按新价算），并把它放进待打价签 */
export function setPrice(id: string, p: number) {
  const it = item(id), old = it.p; if (!(p > 0) || p === old) return false;
  it.p = p;
  const s = useGoods.getState(), had = s.queue.find((q) => q.id === id);
  s.set({ queue: had ? s.queue.map((q) => (q.id === id ? { ...q, why: 'price', old: q.old ?? old } : q)) : [{ id, why: 'price', old }, ...s.queue], picked: [...new Set([id, ...s.picked])], focus: id });
  return true;
}
export function queueLabel(id: string) {
  const s = useGoods.getState(); if (s.queue.some((q) => q.id === id)) { s.set({ focus: id }); return; }
  s.set({ queue: [{ id, why: 'manual' }, ...s.queue], picked: [...new Set([id, ...s.picked])], focus: id });
}
export const togglePick = (id: string) => { const s = useGoods.getState(); s.set({ picked: s.picked.includes(id) ? s.picked.filter((x) => x !== id) : [...s.picked, id] }); };
/** 打印选中的：从队列里移走，返回张数 */
export function printPicked() {
  const s = useGoods.getState(), n = s.picked.filter((id) => s.queue.some((q) => q.id === id)).length;
  const rest = s.queue.filter((q) => !s.picked.includes(q.id));
  s.set({ queue: rest, picked: [], printed: s.printed + n, focus: rest[0]?.id || s.focus });
  return n;
}
