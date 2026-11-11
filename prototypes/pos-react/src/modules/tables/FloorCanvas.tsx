// 桌台平面画布（方案 §4.3）：按栅格坐标绝对定位；格子大小按内容区宽度自适应。
// 查看：单击选桌 · 双击按状态开台 / 加菜 / 结账 · 状态筛选时其余桌变淡（位置不动）。
// 编辑：拖动吸附到格 · 右下角拖柄改大小 · 放不下红框并回弹 · 方向键微调 · Delete 移出平面 · Ctrl+Z / Ctrl+Shift+Z。
import { useEffect, useLayoutEffect, useRef, useState, type PointerEvent as RPE, type ReactNode } from 'react';
import { useStore } from '@/store';
import { busyTable, type Table } from '@/data/sample';
import { FIX_ICON, fits, SOLID, type Fixture, type FloorData, type Place } from '@/data/floors';
import { stGroup } from '@/lib/states';
import { useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { StatusIcon } from '@/components/ui/badge';
import { StatusTile } from '@/components/ui/status-tile';
import { change, redo, undo } from './floor-ops';

type Box = { x: number; y: number; w: number; h: number };
type Drag = { kind: 't' | 'f'; id: string; box: Box; ok: boolean } | null;
const devScale = () => { const d = document.getElementById('device'); return d ? d.getBoundingClientRect().width / 1280 || 1 : 1; };

export function FloorCanvas({ area, data, edit, dimOf }: { area: string; data: FloorData; edit?: boolean; dimOf?: (t: Table) => boolean }) {
  const t = useT(); const s = useStore(), dispatch = s.act;
  const plan = data.areas[area];
  const wrap = useRef<HTMLDivElement>(null); const [u, setU] = useState(18);
  useLayoutEffect(() => {
    const el = wrap.current; if (!el || !plan) return;
    const fit = () => setU(Math.max(10, Math.min(28, Math.floor(el.clientWidth / plan.cols))));
    fit(); const ro = new ResizeObserver(fit); ro.observe(el); return () => ro.disconnect();
  }, [plan?.cols]);
  const [drag, setDrag] = useState<Drag>(null); const dragRef = useRef<Drag>(null);
  const setD = (d: Drag) => { dragRef.current = d; setDrag(d); };
  const sel = s.fsel;
  // 键盘：方向键微调 · Delete 移出 · 撤销 / 重做（只在编辑态）
  useEffect(() => {
    if (!edit) return;
    const key = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement).closest('input,textarea')) return;
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'z') { e.preventDefault(); e.shiftKey ? redo() : undo(); return; }
      const cur = useStore.getState().fsel; if (!cur) return;
      const d = useStore.getState().fdraft!; const item = cur.kind === 't' ? d.place[cur.id] : d.areas[area].fixtures.find((x) => x.id === cur.id);
      if (!item) return;
      if (e.key === 'Delete' || e.key === 'Backspace') { e.preventDefault(); removeSel(area); return; }
      const mv: Record<string, [number, number]> = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
      const m = mv[e.key]; if (!m) return; e.preventDefault();
      const box = { x: item.x + m[0], y: item.y + m[1], w: item.w, h: item.h };
      if (legal(d, area, cur.kind, cur.id, box)) change((n) => applyBox(n, area, cur.kind, cur.id, box));
    };
    window.addEventListener('keydown', key); return () => window.removeEventListener('keydown', key);
  }, [edit, area]);
  if (!plan) return null;

  const down = (e: RPE<HTMLElement>, kind: 't' | 'f', id: string, mode: 'move' | 'size') => {
    if (!edit || e.button !== 0) return;
    e.stopPropagation(); e.preventDefault();
    const d = useStore.getState().fdraft!; const item = kind === 't' ? d.place[id] : d.areas[area].fixtures.find((x) => x.id === id)!;
    const shape = kind === 't' ? (item as Place).shape : null;
    s.set({ fsel: { kind, id } });
    const sx = e.clientX, sy = e.clientY, k = devScale(); let moved = false;
    const move = (ev: PointerEvent) => {
      if (!moved && Math.abs(ev.clientX - sx) + Math.abs(ev.clientY - sy) < 4) return; moved = true;
      const dx = Math.round((ev.clientX - sx) / k / u), dy = Math.round((ev.clientY - sy) / k / u);
      let box: Box;
      if (mode === 'move') box = { x: clamp(item.x + dx, 0, plan.cols - item.w), y: clamp(item.y + dy, 0, plan.rows - item.h), w: item.w, h: item.h };
      else { let w = clamp(item.w + dx, minSize(kind), plan.cols - item.x), h = clamp(item.h + dy, minSize(kind), plan.rows - item.y); if (shape === 'round' || shape === 'square') { w = h = Math.min(Math.max(w, h), plan.cols - item.x, plan.rows - item.y); } box = { x: item.x, y: item.y, w, h }; }
      setD({ kind, id, box, ok: legal(d, area, kind, id, box) });
    };
    const up = () => {
      window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); window.removeEventListener('pointercancel', up);
      const cur = dragRef.current; setD(null);
      if (moved && cur && cur.ok) change((n) => applyBox(n, area, kind, id, cur.box));
    };
    window.addEventListener('pointermove', move); window.addEventListener('pointerup', up); window.addEventListener('pointercancel', up);
  };
  const boxOf = (kind: 't' | 'f', id: string, b: Box) => (drag && drag.kind === kind && drag.id === id ? drag.box : b);
  const pos = (b: Box) => ({ left: b.x * u, top: b.y * u, width: b.w * u, height: b.h * u });
  const isSel = (kind: 't' | 'f', id: string) => !!edit && sel?.kind === kind && sel.id === id;
  const ring = (kind: 't' | 'f', id: string) => (drag && drag.kind === kind && drag.id === id ? (drag.ok ? 'outline outline-2 outline-ink z-30' : 'outline outline-2 outline-danger-dot z-30') : isSel(kind, id) ? 'outline outline-2 outline-ink outline-offset-1 z-20' : '');
  const Handle = ({ kind, id }: { kind: 't' | 'f'; id: string }) => isSel(kind, id) ? <i onPointerDown={(e) => down(e, kind, id, 'size')} className="absolute -right-1.5 -bottom-1.5 w-3.5 h-3.5 rounded-1 bg-inverse border-2 border-solid border-surface cursor-nwse-resize z-40" /> : null;

  const ground = plan.fixtures.filter((f) => !SOLID.includes(f.kind));
  const solid = plan.fixtures.filter((f) => SOLID.includes(f.kind));
  const tables = Object.entries(data.place).filter(([, p]) => p.area === area);
  return (
    <div ref={wrap} className="w-full">
      <div className={cn('relative rounded-rt bg-surface shadow-[inset_0_0_0_3px_var(--c-strong)] select-none', edit && 'fl-grid')} style={{ width: plan.cols * u, height: plan.rows * u, ['--u' as string]: u + 'px' }}
        onPointerDown={() => edit && s.set({ fsel: null })}>
        {ground.map((f) => { const b = boxOf('f', f.id, f); return (
          <div key={f.id} onPointerDown={(e) => down(e, 'f', f.id, 'move')} style={pos(b)}
            className={cn('absolute', edit && 'cursor-grab', ring('f', f.id), f.kind === 'zone' && 'fl-zone rounded-rk', f.kind === 'room' && 'rounded-rk shadow-[inset_0_0_0_3px_var(--c-strong)]', f.kind === 'door' && 'bg-surface grid place-items-center')}>
            {f.kind === 'door' ? <span className="inline-flex items-center gap-1 text-[10px] text-sub leading-none"><Ico name="door-open" size={12} />{f.label}</span>
              : <span className={cn('absolute left-1.5 top-1 text-[11px] leading-tight', f.kind === 'room' ? 'font-700 text-ink' : 'text-sub')}>{f.label || t('fl.fix.' + f.kind)}</span>}
            <Handle kind="f" id={f.id} />
          </div>); })}
        {solid.map((f) => { const b = boxOf('f', f.id, f); return (
          <div key={f.id} onPointerDown={(e) => down(e, 'f', f.id, 'move')} style={pos(b)}
            className={cn('absolute grid place-items-center text-sub', edit && 'cursor-grab', ring('f', f.id), f.kind === 'pillar' ? 'bg-[var(--c-strong)] rounded-1' : f.kind === 'plant' ? 'rounded-full bg-wash-strong' : 'rounded-rk bg-wash-strong')}>
            {f.kind !== 'pillar' && <span className="inline-flex flex-col items-center gap-0.5 text-[11px] leading-none text-center"><Ico name={FIX_ICON[f.kind]} size={Math.min(18, u)} />{f.kind !== 'plant' && b.h * u >= 40 && (f.label || t('fl.fix.' + f.kind))}</span>}
            <Handle kind="f" id={f.id} />
          </div>); })}
        {tables.map(([id, p]) => { const b = boxOf('t', id, p); const tb = s.tables.find((x) => x.id === id); return (
          <FloorTile key={id} id={id} p={p} b={b} u={u} tb={tb} edit={!!edit} dim={!edit && !!tb && !!dimOf?.(tb)} cls={ring('t', id)} current={!edit && s.table === id}
            onDown={(e) => down(e, 't', id, 'move')} onPick={() => dispatch('table:' + id)} onOpen={() => tb && openTable(tb, dispatch)} handle={<Handle kind="t" id={id} />} />); })}
      </div>
    </div>
  );
}

function FloorTile({ id, p, b, u, tb, edit, dim, cls, current, onDown, onPick, onOpen, handle }: { id: string; p: Place; b: Box; u: number; tb?: Table; edit: boolean; dim: boolean; cls: string; current: boolean; onDown: (e: RPE<HTMLElement>) => void; onPick: () => void; onOpen: () => void; handle: ReactNode }) {
  const g = tb ? stGroup('table', tb.st) : 'closed'; const alert = g === 'action' || g === 'failed'; const quiet = !tb || tb.st === 'free' || tb.st === 'disabled';
  const big = b.w * u >= 60 && b.h * u >= 56; const amt = tb && /^(dining|late|due|shared|partial)$/.test(tb.st) ? '¥' + tb.amt : '';
  const back = p.shape === 'booth' ? ({ 0: 'left-0 top-1 bottom-1 w-1.5', 90: 'top-0 left-1 right-1 h-1.5', 180: 'right-0 top-1 bottom-1 w-1.5', 270: 'bottom-0 left-1 right-1 h-1.5' } as Record<number, string>)[p.rot] : '';
  return (
    <div style={{ left: b.x * u, top: b.y * u, width: b.w * u, height: b.h * u }} className={cn('absolute transition-opacity', dim && 'opacity-25', cls)}
      onPointerDown={edit ? onDown : undefined} onDoubleClick={edit ? undefined : onOpen}>
      <StatusTile group={g} alert={!edit && alert} quiet={edit || quiet} dim={tb?.st === 'disabled'} current={current} onClick={edit ? undefined : onPick} ariaLabel={id}
        className={cn('absolute inset-0.5 items-center justify-center gap-0.5 text-center px-1', p.shape === 'round' ? '!rounded-full' : '!rounded-rk', edit && 'cursor-grab !bg-surface shadow-[0_1px_3px_rgba(0,0,0,.18)]')}>
        <b className={cn('num leading-none tracking-[-.01em]', big ? 'text-md' : 'text-sm')}>{id}</b>
        {!edit && tb && big && <span className="inline-flex items-center gap-1 text-[11px] leading-none text-sub"><StatusIcon ent="table" k={tb.st} size={12} />{busyTable(tb.st) ? tb.ppl + '/' + tb.seats : tb.seats}</span>}
        {!edit && amt && big && <span className="a text-[11px] font-600 num leading-none">{amt}</span>}
        {edit && <span className="text-[10px] text-sub leading-none">{(p.seats ?? tb?.seats ?? 4)}</span>}
      </StatusTile>
      {back && <i className={cn('absolute rounded-full bg-[var(--c-strong)] pointer-events-none', back)} />}
      {handle}
    </div>
  );
}

const clamp = (v: number, a: number, b: number) => Math.max(a, Math.min(b, v));
const minSize = (kind: 't' | 'f') => (kind === 't' ? 2 : 1);
function legal(d: FloorData, area: string, kind: 't' | 'f', id: string, box: Box) {
  const plan = d.areas[area]; if (box.x < 0 || box.y < 0 || box.x + box.w > plan.cols || box.y + box.h > plan.rows) return false;
  if (kind === 'f') { const f = plan.fixtures.find((x) => x.id === id); if (f && !SOLID.includes(f.kind)) return true; }
  return fits(d, area, box, id);
}
function applyBox(n: FloorData, area: string, kind: 't' | 'f', id: string, box: Box) {
  if (kind === 't') n.place[id] = { ...n.place[id], ...box };
  else n.areas[area].fixtures = n.areas[area].fixtures.map((f: Fixture) => (f.id === id ? { ...f, ...box } : f));
}
export function removeSel(area: string) {
  const s = useStore.getState(); const cur = s.fsel; if (!cur) return;
  change((n) => { if (cur.kind === 't') delete n.place[cur.id]; else n.areas[area].fixtures = n.areas[area].fixtures.filter((f) => f.id !== cur.id); });
  s.set({ fsel: null });
}
/** 双击桌台：按状态走主操作（和桌台详情底部主按钮一致） */
function openTable(tb: Table, dispatch: (a: string) => void) {
  dispatch('table:' + tb.id);
  if (tb.st === 'due' || tb.st === 'partial') dispatch('gopay');
  else if (busyTable(tb.st)) dispatch('goorder');
  else if (tb.st === 'free' || tb.st === 'booked') dispatch('seat');
}
export { legal };
