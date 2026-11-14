// 编辑态外围：栏位框 + 拖柄（AreaFrames，叠在网格同一 gridArea 上）· 落位插入条（DropZones）· 快捷方式库（Library）
// · 拖影（Ghost）· 点按菜单（TapMenu）· 长按进度环（LongPressRing + useLongPressEntry）· 编辑条（EditBar，替换系统栏内容）
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useStore } from '@/store';
import { MODULES } from '@/layout/modules';
import { QUICK } from '@/data/sample';
import { SLOTS, SLOT_IDS, readSlots, slotOf, type SlotId } from '@/data/slots';
import { useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Button } from '@/components/ui/button';
import { beginPointer, moveQuick, sameSrc, setArea, toDevice, useDrag, type Area, type Zone } from './drag';
import { QuickFace, Slot, useEditing } from './Slot';

export const LIB_H = 128;

/** 当前模块哪些栏存在、哪些能拖 */
export function useAreas() {
  const wb = useStore((s) => s.wb); const m = MODULES[wb];
  const hasNav = m.layout === 'W1' || m.layout === 'W4', hasDetail = m.layout !== 'W4';
  return { hasNav, hasDetail, navMovable: hasNav, detailMovable: hasDetail && !m.detailSide };
}

/* ── 栏位框 ── */
function Grip({ area, movable }: { area: 'nav' | 'content' | 'detail'; movable: boolean }) {
  const t = useT(); const tap = useDrag((d) => d.tap), src = useDrag((d) => d.src);
  const me = movable ? { kind: 'area' as const, area: area as Area } : null;
  const on = sameSrc(tap, me), lifting = sameSrc(src, me);
  if (!movable) return <span className="absolute bottom-1.5 left-1/2 -translate-x-1/2 inline-flex items-center gap-1 h-6 px-2 rounded-full bg-wash-strong text-sub text-xs font-600 whitespace-nowrap">{t('edit.area.' + area)} · {t('edit.areaFixed')}</span>;
  return (
    <button type="button" data-editable="" data-grip={area} onPointerDown={(e) => beginPointer(e, me!)}
      className={cn('edit-item absolute top-1.5 left-1/2 -translate-x-1/2 inline-flex items-center gap-1 h-7 px-2.5 rounded-full text-xs font-600 whitespace-nowrap shadow-[0_2px_8px_rgba(0,0,0,.18)] [&>svg]:w-14px [&>svg]:h-14px',
        on || lifting ? 'bg-primary text-primary-on' : 'bg-inverse text-on-inverse')}>
      <Ico name="grip-horizontal" size={14} />{t('edit.area.' + area)}
    </button>
  );
}
export function AreaFrames() {
  const edit = useStore((s) => s.edit); const a = useAreas(); const src = useDrag((d) => d.src);
  if (!edit) return null;
  const frame = (area: 'nav' | 'content' | 'detail', movable: boolean) => (
    <div key={area} style={{ gridArea: area }} className={cn('relative z-10 pointer-events-none rounded-rc outline outline-2 outline-dashed -outline-offset-2 ed-line transition-colors',
      src?.kind === 'area' && src.area === area && 'ed-lift !outline-ink')}>
      <Grip area={area} movable={movable} />
    </div>
  );
  return <>{a.hasNav && frame('nav', a.navMovable)}{frame('content', false)}{a.hasDetail && frame('detail', a.detailMovable)}</>;
}

/* ── 落位插入条：拖栏时，网格的左 / 右 / 上边缘出现可放位置；悬停的那条变成实心墨色 + 标签 ── */
export function DropZones() {
  const t = useT(); const src = useDrag((d) => d.src), over = useDrag((d) => d.over); const nav = useStore((s) => s.nav), detail = useStore((s) => s.detail);
  if (src?.kind !== 'area') return null;
  const zones: Zone[] = src.area === 'nav' ? ['left', 'top', 'right'] : ['left', 'right'];
  const cur = src.area === 'nav' ? nav : detail;
  return (
    <div className="absolute inset-0 z-20 pointer-events-none">
      {zones.filter((z) => z !== cur).map((z) => {
        const on = over?.kind === 'zone' && over.zone === z;
        const pos = z === 'left' ? 'left-0.5 top-3 bottom-3 w-1.5' : z === 'right' ? 'right-0.5 top-3 bottom-3 w-1.5' : 'top-0.5 left-3 right-3 h-1.5';
        return (
          <div key={z} className={cn('absolute rounded-full transition-all', pos, on ? 'bg-ink' : 'ed-bar')}>
            {on && <span className={cn('absolute whitespace-nowrap h-6 px-2 rounded-full bg-inverse text-on-inverse text-xs font-600 inline-flex items-center', z === 'top' ? 'left-1/2 top-3 -translate-x-1/2' : z === 'left' ? 'left-3 top-1/2 -translate-y-1/2' : 'right-3 top-1/2 -translate-y-1/2')}>{t('edit.dropTo', { p: t('edit.pos.' + z) })}</span>}
          </div>
        );
      })}
    </div>
  );
}

/* ── 快捷方式库：编辑态升起在底部；没放进任何槽的都在这里 ── */
export function Library() {
  const t = useT(); const editing = useEditing(); const s = useStore();
  const src = useDrag((d) => d.src), over = useDrag((d) => d.over), tap = useDrag((d) => d.tap);
  if (!editing) return null;
  const all = readSlots(s); const left = Object.keys(QUICK).filter((k) => slotOf(all, k) === 'lib');
  const hot = src?.kind === 'quick' && src.from !== 'lib' && over?.kind === 'slot' && over.slot === 'lib';
  return (
    <div data-slot="lib" data-editable="" style={{ height: LIB_H }}
      className={cn('absolute left-0 right-0 bottom-0 z-30 rounded-t-[18px] px-4 pt-3 pb-4 flex flex-col gap-2 shadow-[0_-6px_24px_rgba(0,0,0,.14)] edit-rise transition-colors', hot ? 'bg-wash-strong' : 'bg-surface')}>
      <div className="flex items-baseline gap-3">
        <b className="text-sm font-600">{t('edit.lib')} · {left.length}</b>
        <span className="help">{hot ? t('edit.libDrop') : t('edit.libHint')}</span>
      </div>
      <div className="flex-1 min-h-0 flex items-center gap-2 overflow-x-auto no-bar">
        {left.length === 0 && <span className="text-sm text-placeholder">{t('edit.libEmpty')}</span>}
        {left.map((k) => {
          const me = { kind: 'quick' as const, key: k, from: 'lib' as const };
          return (
            <div key={k} data-editable="" data-qk={k} data-from="lib" onPointerDown={(e) => beginPointer(e, me)}
              className={cn('edit-item flex-none', sameSrc(src, me) && 'opacity-30')}>
              <QuickFace k={k} form="chip" className={cn('bg-wash px-3 min-h-ctl', sameSrc(tap, me) && 'outline outline-2 outline-ink outline-offset-1')} />
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ── 拖影：跟手；快捷方式按要落进的槽的形态预览（拖向悬浮槽就变圆钮） ── */
export function Ghost() {
  const t = useT(); const src = useDrag((d) => d.src), x = useDrag((d) => d.x), y = useDrag((d) => d.y), over = useDrag((d) => d.over);
  if (!src) return null;
  if (src.kind === 'area') {
    const z = over?.kind === 'zone' ? over.zone : null;
    return <div className="absolute z-50 pointer-events-none -translate-x-1/2 -translate-y-1/2 inline-flex items-center gap-1.5 h-9 px-3 rounded-full bg-inverse text-on-inverse text-sm font-600 whitespace-nowrap shadow-[0_8px_24px_rgba(0,0,0,.25)]" style={{ left: x, top: y }}>
      <Ico name="grip-horizontal" size={16} />{t('edit.area.' + src.area)}{z && <span className="font-400 opacity-80">→ {t('edit.pos.' + z)}</span>}
    </div>;
  }
  const form = over?.kind === 'slot' && over.slot !== 'lib' ? SLOTS[over.slot].form : 'chip';
  return (
    <div className="absolute z-50 pointer-events-none -translate-x-1/2 -translate-y-1/2 scale-105" style={{ left: x, top: y }}>
      <QuickFace k={src.key} form={form} className={cn('shadow-[0_8px_24px_rgba(0,0,0,.25)]', form !== 'fab' && 'bg-surface px-3', over?.kind === 'slot' && over.full && 'outline outline-2 outline-danger-dot')} />
    </div>
  );
}

/* ── 点按模式：编辑态里单击一个对象（不拖），在它旁边出菜单：前后移一位 · 移到别的槽 · 移回库；栏：左 / 上 / 右 ── */
export function TapMenu() {
  const t = useT(); const tap = useDrag((d) => d.tap); const s = useStore();
  const ref = useRef<HTMLDivElement>(null); const [pos, setPos] = useState<{ left: number; top: number } | null>(null);
  const all = readSlots(s);
  useLayoutEffect(() => {
    if (!tap || !ref.current) { setPos(null); return; }
    const sel = tap.kind === 'area' ? `[data-grip="${tap.area}"]` : `[data-qk="${tap.key}"][data-from="${tap.from}"]`;
    const el = document.querySelector<HTMLElement>(sel); if (!el) { setPos(null); return; }
    const r = el.getBoundingClientRect(), a = toDevice(r.left, r.top), b = toDevice(r.right, r.bottom);
    const w = ref.current.offsetWidth, h = ref.current.offsetHeight;
    let top = b.y + 8; if (top + h > 800 - 8) top = a.y - h - 8;
    const left = Math.max(8, Math.min(1280 - w - 8, (a.x + b.x) / 2 - w / 2));
    setPos({ left, top });
  }, [tap, s.quick, s.slots, s.nav, s.detail]);
  useEffect(() => { if (!s.edit) useDrag.setState({ tap: null, src: null, over: null }); }, [s.edit]);
  if (!tap || !s.edit) return null;
  const close = () => useDrag.setState({ tap: null });
  let body;
  if (tap.kind === 'area') {
    const zones: Zone[] = tap.area === 'nav' ? ['left', 'top', 'right'] : ['left', 'right']; const cur = tap.area === 'nav' ? s.nav : s.detail;
    body = <><span className="text-xs opacity-70 px-1">{t('edit.area.' + tap.area)} · {t('edit.position')}</span>
      <div className="flex gap-1">{zones.map((z) => <MenuBtn key={z} on={cur === z} icon={z === 'left' ? 'panel-left' : z === 'right' ? 'panel-right' : 'panel-top'} label={t('edit.pos.' + z)} onClick={() => setArea(tap.area, z)} />)}</div></>;
  } else {
    const from = tap.from; const arr = from === 'lib' ? [] : all[from]; const i = arr.indexOf(tap.key); const vert = from === 'f';
    body = <>
      {from !== 'lib' && <div className="flex items-center gap-1"><span className="text-xs opacity-70 w-9 px-1">{t('edit.move')}</span>
        <MenuBtn icon={vert ? 'chevron-up' : 'chevron-left'} label="" disabled={i <= 0} onClick={() => moveQuick(tap.key, from, i - 1)} />
        <MenuBtn icon={vert ? 'chevron-down' : 'chevron-right'} label="" disabled={i >= arr.length - 1} onClick={() => moveQuick(tap.key, from, i + 1)} /></div>}
      <div className="flex items-center gap-1"><span className="text-xs opacity-70 w-9 px-1">{from === 'lib' ? t('edit.place') : t('edit.moveTo')}</span>
        {SLOT_IDS.map((id: SlotId) => <MenuBtn key={id} on={from === id} disabled={from === id} label={`${t(SLOTS[id].label)} ${all[id].length}/${SLOTS[id].cap}`}
          onClick={() => { moveQuick(tap.key, id); useDrag.setState({ tap: { kind: 'quick', key: tap.key, from: id } }); }} />)}</div>
      {from !== 'lib' && <MenuBtn icon="undo-2" label={t('edit.remove')} onClick={() => { moveQuick(tap.key, 'lib'); close(); }} />}
    </>;
  }
  return (
    <div ref={ref} data-tapmenu="" className="absolute z-40 flex flex-col gap-1.5 p-2 rounded-rt bg-inverse text-on-inverse shadow-[0_8px_24px_rgba(0,0,0,.25)] edit-pop"
      style={pos ? { left: pos.left, top: pos.top } : { left: -9999, top: 0 }}>{body}</div>
  );
}
function MenuBtn({ icon, label, on, disabled, onClick }: { icon?: string; label: string; on?: boolean; disabled?: boolean; onClick: () => void }) {
  return <button type="button" disabled={disabled} onClick={onClick}
    className={cn('inline-flex items-center justify-center gap-1 h-9 min-w-9 px-2 rounded-rk text-sm whitespace-nowrap [&>svg]:w-16px [&>svg]:h-16px', on ? 'bg-on-inverse text-inverse font-600' : 'ed-mbtn', disabled && !on && 'opacity-35')}>
    {icon && <Ico name={icon} size={16} />}{label}
  </button>;
}

/* ── 长按进入：在 [data-lp] 区域按住 600ms（移动 > 8px 取消），进入编辑态并吞掉松手那一下的 click ── */
const LP_MS = 600;
export function useLongPressEntry() {
  useEffect(() => {
    const dev = document.getElementById('device'); if (!dev) return;
    let timer = 0, sx = 0, sy = 0, id = -1;
    const stop = () => { clearTimeout(timer); timer = 0; id = -1; useDrag.setState({ ring: null }); window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', stop); window.removeEventListener('pointercancel', stop); };
    const move = (e: PointerEvent) => { if (e.pointerId === id && Math.hypot(e.clientX - sx, e.clientY - sy) > 8) stop(); };
    const down = (e: PointerEvent) => {
      const target = e.target as HTMLElement; const st = useStore.getState();
      if (st.edit) { if (!target.closest('[data-editable],[data-tapmenu]')) useDrag.setState({ tap: null }); return; }
      if (e.button !== 0 || !target.closest('[data-lp]') || st.ov !== 'none') return;
      sx = e.clientX; sy = e.clientY; id = e.pointerId;
      const p = toDevice(sx, sy); useDrag.setState({ ring: { x: p.x, y: p.y } });
      window.addEventListener('pointermove', move); window.addEventListener('pointerup', stop); window.addEventListener('pointercancel', stop);
      timer = window.setTimeout(() => {
        stop(); useStore.getState().act('edit:on');
        const eat = (ev: Event) => { ev.stopPropagation(); ev.preventDefault(); };
        window.addEventListener('click', eat, true); setTimeout(() => window.removeEventListener('click', eat, true), 400);
        if ((navigator as any).userActivation?.hasBeenActive) navigator.vibrate?.(15);
      }, LP_MS);
    };
    const ctx = (e: Event) => { if ((e.target as HTMLElement).closest('[data-lp],[data-editable]')) e.preventDefault(); };
    dev.addEventListener('pointerdown', down); dev.addEventListener('contextmenu', ctx);
    return () => { stop(); dev.removeEventListener('pointerdown', down); dev.removeEventListener('contextmenu', ctx); };
  }, []);
}
export function LongPressRing() {
  const ring = useDrag((d) => d.ring); if (!ring) return null;
  const R = 22, C = 2 * Math.PI * R;
  return (
    <svg className="absolute z-50 pointer-events-none -translate-x-1/2 -translate-y-1/2" style={{ left: ring.x, top: ring.y }} width="60" height="60" viewBox="0 0 60 60" aria-hidden>
      <circle cx="30" cy="30" r={R} fill="var(--c-inverse)" fillOpacity=".08" stroke="var(--c-inverse)" strokeOpacity=".15" strokeWidth="4" />
      <circle cx="30" cy="30" r={R} fill="none" stroke="var(--c-inverse)" strokeWidth="4" strokeLinecap="round" transform="rotate(-90 30 30)"
        strokeDasharray={C} style={{ strokeDashoffset: C, animation: `lp-ring ${LP_MS}ms linear forwards`, ['--len' as string]: String(C) }} />
    </svg>
  );
}

/* ── 编辑条：编辑态时替换系统栏内容；点餐页把 S 槽留在系统栏里 ── */
export function EditBar() {
  const t = useT(); const wb = useStore((s) => s.wb), dispatch = useStore((s) => s.act); const editing = useEditing();
  return (
    <>
      <span className="inline-flex items-center justify-center w-9 h-9 rounded-rb bg-inverse text-on-inverse"><Ico name="pencil-ruler" size={18} /></span>
      <span className="flex flex-col leading-tight mr-auto min-w-0">
        <b className="text-base font-600 whitespace-nowrap">{t('edit.title', { m: t(MODULES[wb].name) })}</b>
        <small className="text-xs text-sub whitespace-nowrap">{editing ? t('edit.hint') : t('edit.hintNoLib')}</small>
      </span>
      {editing && <Slot id="s" className="mr-2" />}
      <Button variant="muted" size="sm" onClick={() => dispatch('edit:reset')}><Ico name="rotate-ccw" size={16} />{t('edit.reset')}</Button>
      <Button variant="muted" size="sm" onClick={() => dispatch('edit:cancel')}>{t('edit.cancel')}</Button>
      <Button size="sm" onClick={() => dispatch('edit:done')}><Ico name="check" size={16} />{t('edit.done')}</Button>
    </>
  );
}
