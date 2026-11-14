// 拖动引擎（Pointer Events，触摸 / 鼠标 / 笔统一）：不用 dnd-kit，因为设备舞台是 transform: scale() 的，坐标自己换算更直接。
// 拖动中的位置放在独立的小 store（useDrag），避免每次 pointermove 都让整个 POS store 重渲染。
import { create } from 'zustand';
import type { PointerEvent as RPointerEvent } from 'react';
import { useStore } from '@/store';
import { notify } from '@/store/actions';
import { tr } from '@/i18n';
import { QUICK } from '@/data/sample';
import { placeIn, readSlots, writeSlots, type SlotId } from '@/data/slots';

export type Area = 'nav' | 'detail';
export type Zone = 'left' | 'right' | 'top';
export type Src = { kind: 'quick'; key: string; from: SlotId | 'lib' } | { kind: 'area'; area: Area };
export type Over = { kind: 'slot'; slot: SlotId | 'lib'; index: number; full: boolean } | { kind: 'zone'; zone: Zone } | null;
interface DragState { src: Src | null; x: number; y: number; over: Over; tap: Src | null; ring: { x: number; y: number } | null }
export const useDrag = create<DragState>(() => ({ src: null, x: 0, y: 0, over: null, tap: null, ring: null }));

export const sameSrc = (a: Src | null, b: Src | null) => !!a && !!b && a.kind === b.kind && (a.kind === 'quick' ? a.key === (b as typeof a).key && a.from === (b as typeof a).from : a.area === (b as typeof a).area);

/** 屏幕坐标 → 设备坐标（1280 × 800 舞台，按缩放换算） */
export function toDevice(cx: number, cy: number) {
  const dev = document.getElementById('device'); if (!dev) return { x: cx, y: cy, k: 1 };
  const r = dev.getBoundingClientRect(), k = r.width / 1280 || 1;
  return { x: (cx - r.left) / k, y: (cy - r.top) / k, k };
}

function hitSlot(cx: number, cy: number, src: Extract<Src, { kind: 'quick' }>): Over {
  const el = document.elementFromPoint(cx, cy) as HTMLElement | null;
  const host = el?.closest<HTMLElement>('[data-slot]'); if (!host) return null;
  const slot = host.dataset.slot as SlotId | 'lib';
  if (slot === 'lib') return { kind: 'slot', slot, index: 0, full: false };
  const axis = host.dataset.axis === 'y' ? 'y' : 'x';
  // 槽内落点：数一下指针前面有几个（不算正在拖的那个）。F 槽是自下而上排（column-reverse），按 y 反向数
  const items = Array.from(host.querySelectorAll<HTMLElement>('[data-slot-item]')).filter((n) => !(n.dataset.qk === src.key && n.dataset.from === slot));
  const rev = host.dataset.rev === '1';
  let index = 0;
  items.forEach((n) => { const r = n.getBoundingClientRect(); const c = axis === 'x' ? r.left + r.width / 2 : r.top + r.height / 2; const p = axis === 'x' ? cx : cy; if (rev ? p < c : p > c) index++; });
  const cap = Number(host.dataset.cap || 99);
  return { kind: 'slot', slot, index, full: src.from !== slot && items.length >= cap };
}
function hitZone(cx: number, cy: number, area: Area): Over {
  const g = document.getElementById('wb-grid'); if (!g) return null;
  const r = g.getBoundingClientRect(), px = (cx - r.left) / r.width, py = (cy - r.top) / r.height;
  const s = useStore.getState();
  let zone: Zone | null = null;
  if (area === 'nav') zone = py < 0.2 ? 'top' : px < 0.35 ? 'left' : px > 0.65 ? 'right' : null;
  else zone = px < 0.4 ? 'left' : px > 0.6 ? 'right' : null;
  const cur = area === 'nav' ? s.nav : s.detail;
  return zone && zone !== cur ? { kind: 'zone', zone } : null;
}

/** 编辑态下所有可拖对象的 onPointerDown：移动 6px 以上算拖，否则算「点按」（选中后出箭头菜单） */
export function beginPointer(e: RPointerEvent<HTMLElement>, src: Src) {
  if (e.button !== 0) return;
  e.preventDefault(); e.stopPropagation();
  const el = e.currentTarget; const id = e.pointerId;
  try { el.setPointerCapture(id); } catch { /* 有的元素拿不到 capture，走 window 也行 */ }
  const sx = e.clientX, sy = e.clientY; let started = false;
  const move = (ev: PointerEvent) => {
    if (ev.pointerId !== id) return;
    if (!started) { if (Math.hypot(ev.clientX - sx, ev.clientY - sy) < 6) return; started = true; useDrag.setState({ src, tap: null }); }
    const p = toDevice(ev.clientX, ev.clientY);
    useDrag.setState({ x: p.x, y: p.y, over: src.kind === 'quick' ? hitSlot(ev.clientX, ev.clientY, src) : hitZone(ev.clientX, ev.clientY, src.area) });
  };
  const end = (ev: PointerEvent, cancel: boolean) => {
    if (ev.pointerId !== id) return;
    window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); window.removeEventListener('pointercancel', cc);
    if (!started) { if (!cancel) useDrag.setState((d) => ({ tap: sameSrc(d.tap, src) ? null : src })); return; }
    const { over } = useDrag.getState(); useDrag.setState({ src: null, over: null });
    if (!cancel && over) drop(src, over);
  };
  const up = (ev: PointerEvent) => end(ev, false), cc = (ev: PointerEvent) => end(ev, true);
  window.addEventListener('pointermove', move); window.addEventListener('pointerup', up); window.addEventListener('pointercancel', cc);
}

function drop(src: Src, over: NonNullable<Over>) {
  if (src.kind === 'area' && over.kind === 'zone') { setArea(src.area, over.zone); return; }
  if (src.kind === 'quick' && over.kind === 'slot') moveQuick(src.key, over.slot, over.index);
}
export function setArea(area: Area, zone: Zone) {
  const s = useStore.getState();
  if (area === 'nav') s.set({ nav: zone }); else if (zone !== 'top') s.set({ detail: zone });
}
/** 放一个快捷方式到槽（或库），被挤出的提示一下 */
export function moveQuick(key: string, to: SlotId | 'lib', index = Infinity) {
  const s = useStore.getState();
  const { next, bumped } = placeIn(readSlots(s), key, to, index);
  s.set(writeSlots(next));
  if (bumped && QUICK[bumped]) notify(useStore.getState, 'edit.bumped', { n: tr(s.lang, QUICK[bumped][0]) });
}
