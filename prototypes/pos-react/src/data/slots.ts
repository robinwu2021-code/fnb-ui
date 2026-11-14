// 点餐页快捷方式停靠槽：Q 结单区头 · T 工具条尾 · S 系统栏 · F 悬浮。Q 沿用 store.quick，其余三个在 store.slots。
// 只放纯数据和纯函数（不引 store），避免和 store/index 循环引用。
export type SlotId = 'q' | 't' | 's' | 'f';
export type SlotForm = 'chip' | 'icon' | 'entry' | 'fab';
export interface SlotDef { cap: number; form: SlotForm; axis: 'x' | 'y'; label: string }
export const SLOT_IDS: SlotId[] = ['q', 't', 's', 'f'];
export const SLOTS: Record<SlotId, SlotDef> = {
  q: { cap: 5, form: 'chip', axis: 'x', label: 'edit.slot.q' },
  t: { cap: 3, form: 'icon', axis: 'x', label: 'edit.slot.t' },
  s: { cap: 2, form: 'entry', axis: 'x', label: 'edit.slot.s' },
  f: { cap: 3, form: 'fab', axis: 'y', label: 'edit.slot.f' },
};
export type SlotMap = Record<SlotId, string[]>;
export type ExtraSlots = { t: string[]; s: string[]; f: string[] };
export const DEFAULT_QUICK = ['member', 'hold', 'split', 'share', 'reprice'];
export const DEFAULT_EXTRA: ExtraSlots = { t: ['soldout'], s: ['drawer'], f: [] };

export function readSlots(s: { quick: string[]; slots: ExtraSlots }): SlotMap {
  return { q: s.quick.slice(), t: s.slots.t.slice(), s: s.slots.s.slice(), f: s.slots.f.slice() };
}
export function writeSlots(a: SlotMap): { quick: string[]; slots: ExtraSlots } {
  return { quick: a.q, slots: { t: a.t, s: a.s, f: a.f } };
}
/** 去重 + 去掉不存在的 key + 截到容量（读 localStorage 后跑一遍） */
export function cleanSlots(a: SlotMap, known: string[]): SlotMap {
  const seen = new Set<string>(); const r = {} as SlotMap;
  SLOT_IDS.forEach((id) => { r[id] = (a[id] || []).filter((k) => known.includes(k) && !seen.has(k) && seen.add(k)).slice(0, SLOTS[id].cap); });
  return r;
}
/** 移动一个快捷方式：先从所有槽里拿掉，再插到 to 的 index；超容量时挤出最后一个（不是刚放进来的那个），返回被挤出的 key */
export function placeIn(a: SlotMap, key: string, to: SlotId | 'lib', index = Infinity): { next: SlotMap; bumped: string | null } {
  const next = {} as SlotMap; SLOT_IDS.forEach((id) => (next[id] = a[id].filter((x) => x !== key)));
  let bumped: string | null = null;
  if (to !== 'lib') {
    const arr = next[to]; arr.splice(Math.max(0, Math.min(index, arr.length)), 0, key);
    if (arr.length > SLOTS[to].cap) { for (let i = arr.length - 1; i >= 0; i--) if (arr[i] !== key) { bumped = arr.splice(i, 1)[0]; break; } }
  }
  return { next, bumped };
}
export function slotOf(a: SlotMap, key: string): SlotId | 'lib' { return SLOT_IDS.find((id) => a[id].includes(key)) ?? 'lib'; }
