// 桌台 2.5D 空间视图（方案 §4.4）：同一份平面坐标做 CSS 等轴变换，不用 WebGL。
// 只画空间：地面分区、墙、门、吧台 / 厨房 / 收银 / 柱子有厚度；桌台是贴地的状态色块，上面只有桌号（正对视线）。
// 不画椅子、桌腿、人；人数、金额点开后在详情栏看。固定一个视角，两档缩放；切进来时从平面翻转过来。
import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { useStore } from '@/store';
import { busyTable, type Table } from '@/data/sample';
import { FIX_ICON, type Fixture, type FloorData, type Place } from '@/data/floors';
import { stGroup, stName, stv } from '@/lib/states';
import { useLang, useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';

const TILT = 58, SPIN = -40; // 视角：向后倒 58°、逆时针转 40°
const H = { wall: 2.6, curb: 0.35, room: 1.3, kitchen: 1.6, bar: 1.2, cashier: 1.1, pillar: 2.6, plant: 0.9, table: 0.22 } as const;
const mix = (c: string, p: number) => `color-mix(in srgb, ${c} ${p}%, var(--c-ink))`;

/** 一个有厚度的盒子：顶面 + 前面（+y）+ 右面（+x），这两个侧面朝向观察者 */
function Box({ x, y, w, h, z, u, top, side, round, alpha = 1, children, className, onClick, onDoubleClick, onHover, title, style }: { x: number; y: number; w: number; h: number; z: number; u: number; top: string; side?: string; round?: boolean; alpha?: number; children?: ReactNode; className?: string; onClick?: () => void; onDoubleClick?: () => void; onHover?: (on: boolean) => void; title?: string; style?: CSSProperties }) {
  const Z = z * u; const sd = side || top;
  const face: CSSProperties = { position: 'absolute', opacity: alpha, backfaceVisibility: 'hidden' };
  return (
    <div className={cn('absolute [transform-style:preserve-3d]', className)} title={title} onClick={onClick} onDoubleClick={onDoubleClick} onMouseEnter={onHover && (() => onHover(true))} onMouseLeave={onHover && (() => onHover(false))}
      style={{ left: x * u, top: y * u, width: w * u, height: h * u, ...style }}>
      {!round && <i style={{ ...face, left: 0, top: '100%', width: '100%', height: Z, transformOrigin: 'top', transform: 'rotateX(90deg)', background: mix(sd, 78) }} />}
      {!round && <i style={{ ...face, left: '100%', top: 0, width: Z, height: '100%', transformOrigin: 'left', transform: 'rotateY(-90deg)', background: mix(sd, 88) }} />}
      {round && <i style={{ ...face, inset: 0, borderRadius: '50%', transform: `translateZ(${Z * 0.5}px)`, background: mix(sd, 75) }} />}
      <div style={{ ...face, inset: 0, transform: `translateZ(${Z}px)`, background: top, borderRadius: round ? '50%' : 3, transformStyle: 'preserve-3d' }} className="grid place-items-center">{children}</div>
    </div>
  );
}
/** 正对视线的标签：把舞台的旋转反着转回来 */
const Billboard = ({ children, lift = 0, className }: { children: ReactNode; lift?: number; className?: string }) => (
  <span className={cn('whitespace-nowrap pointer-events-none', className)} style={{ transform: `translateZ(${lift}px) rotateZ(${-SPIN}deg) rotateX(${-TILT}deg)`, transformOrigin: 'center bottom', display: 'inline-flex' }}>{children}</span>
);

export function IsoCanvas({ area, data, dimOf, interactive = true }: { area: string; data: FloorData; dimOf?: (t: Table) => boolean; interactive?: boolean }) {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const plan = data.areas[area];
  const wrap = useRef<HTMLDivElement>(null); const [W, setW] = useState(700);
  useLayoutEffect(() => { const el = wrap.current; if (!el) return; const f = () => setW(el.clientWidth); f(); const ro = new ResizeObserver(f); ro.observe(el); return () => ro.disconnect(); }, []);
  // 翻转动画：先平放，下一帧倒下去
  const [flat, setFlat] = useState(true);
  useEffect(() => { const h = requestAnimationFrame(() => requestAnimationFrame(() => setFlat(false))); return () => cancelAnimationFrame(h); }, [area]);
  const [tip, setTip] = useState<{ id: string } | null>(null);
  if (!plan) return null;
  const cos = Math.cos((Math.abs(SPIN) * Math.PI) / 180), sin = Math.sin((Math.abs(SPIN) * Math.PI) / 180);
  const spanU = plan.cols * cos + plan.rows * sin; // 转过之后的投影宽（格）
  const u = Math.max(8, (W - 24) / spanU) * s.fzoom;
  const flatH = (plan.cols * sin + plan.rows * cos) * u * Math.cos((TILT * Math.PI) / 180); // 地面投影高
  const rise = H.wall * u * Math.sin((TILT * Math.PI) / 180); // 远墙往上立起的高度
  const projH = flatH + rise + 32;
  const tables = Object.entries(data.place).filter(([, p]) => p.area === area);
  const fx = plan.fixtures;
  const wallC = 'var(--c-strong)';
  const doors = fx.filter((f) => f.kind === 'door');
  // 近侧（下、右）外墙只画矮边；门处留缺口
  const nearSeg = (side: 'bottom' | 'right') => {
    const len = side === 'bottom' ? plan.cols : plan.rows; const gaps = doors.filter((d) => (side === 'bottom' ? d.y + d.h >= plan.rows : d.x + d.w >= plan.cols)).map((d) => (side === 'bottom' ? [d.x, d.x + d.w] : [d.y, d.y + d.h]));
    const segs: [number, number][] = []; let a = 0; gaps.sort((p, q) => p[0] - q[0]).forEach(([g0, g1]) => { if (g0 > a) segs.push([a, g0]); a = Math.max(a, g1); }); if (a < len) segs.push([a, len]); return segs;
  };
  const openTable = (tb: Table) => { dispatch('table:' + tb.id); if (tb.st === 'due' || tb.st === 'partial') dispatch('gopay'); else if (busyTable(tb.st)) dispatch('goorder'); else if (tb.st === 'free' || tb.st === 'booked') dispatch('seat'); };
  const T = 0.3; // 墙厚（格）
  return (
    <div ref={wrap} className={cn('relative w-full', s.fzoom === 2 ? 'overflow-auto' : 'overflow-hidden')} style={{ height: flat ? plan.rows * u + 16 : projH }}>
      <div className="absolute left-1/2 top-1/2 [transform-style:preserve-3d] transition-transform duration-500 ease-out"
        style={{ width: plan.cols * u, height: plan.rows * u, top: `calc(50% + ${flat ? 0 : rise / 2}px)`, transform: `translate(-50%,-50%) ${flat ? 'rotateX(0deg) rotateZ(0deg)' : `rotateX(${TILT}deg) rotateZ(${SPIN}deg)`}` }}>
        {/* 地面 */}
        <div className="absolute inset-0 rounded-[3px]" style={{ background: 'var(--c-surface)', boxShadow: '0 0 0 1px var(--c-strong)' }} />
        {fx.filter((f) => f.kind === 'zone').map((f) => <div key={f.id} className="absolute fl-zone rounded-[3px] grid place-items-start p-1" style={{ left: f.x * u, top: f.y * u, width: f.w * u, height: f.h * u }}><span className="text-[10px] text-sub">{f.label}</span></div>)}
        {doors.map((d) => <div key={d.id} className="absolute grid place-items-center" style={{ left: d.x * u, top: d.y * u, width: d.w * u, height: d.h * u, background: 'repeating-linear-gradient(45deg, var(--c-wash-strong) 0 4px, transparent 4px 8px)' }}><Billboard lift={2}><span className="inline-flex items-center gap-0.5 text-[10px] text-sub"><Ico name="door-open" size={11} />{d.label}</span></Billboard></div>)}
        {/* 远侧外墙（上、左）：全高，半透明 */}
        <Box x={0} y={-T} w={plan.cols} h={T} z={H.wall} u={u} top={wallC} alpha={0.55} />
        <Box x={-T} y={-T} w={T} h={plan.rows + T} z={H.wall} u={u} top={wallC} alpha={0.55} />
        {/* 近侧外墙：矮边 */}
        {nearSeg('bottom').map(([a, b]) => <Box key={'b' + a} x={a} y={plan.rows} w={b - a} h={T} z={H.curb} u={u} top={wallC} />)}
        {nearSeg('right').map(([a, b]) => <Box key={'r' + a} x={plan.cols} y={a} w={T} h={b - a} z={H.curb} u={u} top={wallC} />)}
        {/* 包间：四面矮墙 + 房号 */}
        {fx.filter((f) => f.kind === 'room').map((f) => <Room key={f.id} f={f} u={u} T={T} />)}
        {/* 实体固定物 */}
        {fx.filter((f) => ['kitchen', 'bar', 'cashier', 'pillar', 'plant'].includes(f.kind)).map((f) => {
          const z = H[f.kind as keyof typeof H]; const c = f.kind === 'pillar' ? wallC : f.kind === 'plant' ? 'var(--c-success-tint)' : 'var(--c-wash-strong)';
          return <Box key={f.id} x={f.x} y={f.y} w={f.w} h={f.h} z={z} u={u} top={c} round={f.kind === 'plant'}>
            {f.kind !== 'pillar' && <Billboard lift={2}><span className="inline-flex items-center gap-1 text-[11px] text-sub font-500"><Ico name={FIX_ICON[f.kind]} size={12} />{f.kind !== 'plant' && (f.label || t('fl.fix.' + f.kind))}</span></Billboard>}
          </Box>;
        })}
        {/* 桌台：贴地色块 + 桌号 */}
        {tables.map(([id, p]) => { const tb = s.tables.find((x) => x.id === id); return <IsoTable key={id} id={id} p={p} u={u} tb={tb} current={s.table === id} dim={!!tb && !!dimOf?.(tb)} interactive={interactive}
          onPick={() => { dispatch('table:' + id); setTip({ id }); }} onOpen={() => tb && openTable(tb)} hover={tip?.id === id} onHover={(on) => setTip(on ? { id } : null)} lang={lang} />; })}
      </div>
    </div>
  );
}

/** 包间：远侧（上、左）半透明矮墙 · 近侧（下、右）矮边 · 朝走廊的一面留门 */
function Room({ f, u, T }: { f: Fixture; u: number; T: number }) {
  const c = 'var(--c-strong)', z = H.room, gap = Math.min(3, f.w - 2), g0 = f.x + Math.floor((f.w - gap) / 2);
  const doorBottom = f.y < 12; // 上排房间门朝下（走廊在 y 12–13），下排朝上
  const hwall = (y: number, far: boolean, door: boolean) => (door
    ? <><Box x={f.x} y={y} w={g0 - f.x} h={T} z={far ? z : H.curb} u={u} top={c} alpha={far ? 0.45 : 1} /><Box x={g0 + gap} y={y} w={f.x + f.w - g0 - gap} h={T} z={far ? z : H.curb} u={u} top={c} alpha={far ? 0.45 : 1} /></>
    : <Box x={f.x} y={y} w={f.w} h={T} z={far ? z : H.curb} u={u} top={c} alpha={far ? 0.45 : 1} />);
  return <>
    {hwall(f.y, true, !doorBottom)}
    {hwall(f.y + f.h - T, false, doorBottom)}
    <Box x={f.x} y={f.y} w={T} h={f.h} z={z} u={u} top={c} alpha={0.45} />
    <Box x={f.x + f.w - T} y={f.y} w={T} h={f.h} z={H.curb} u={u} top={c} />
    <div className="absolute [transform-style:preserve-3d]" style={{ left: (f.x + 0.8) * u, top: (f.y + 0.8) * u }}><Billboard><b className="text-[11px] text-ink font-700">{f.label}</b></Billboard></div>
  </>;
}

function IsoTable({ id, p, u, tb, current, dim, interactive, onPick, onOpen, hover, onHover, lang }: { id: string; p: Place; u: number; tb?: Table; current: boolean; dim: boolean; interactive: boolean; onPick: () => void; onOpen: () => void; hover: boolean; onHover: (on: boolean) => void; lang: 'zh' | 'en' }) {
  const t = useT(); const g = tb ? stGroup('table', tb.st) : 'closed'; const alert = g === 'action' || g === 'failed'; const quiet = !tb || tb.st === 'free' || tb.st === 'disabled';
  const vars = stv(g) as Record<string, string>;
  const top = current ? 'var(--c-sel-bg)' : quiet ? 'var(--c-wash-strong)' : alert ? vars['--bks'] : vars['--bk'];
  return (
    <Box x={p.x + 0.15} y={p.y + 0.15} w={p.w - 0.3} h={p.h - 0.3} z={H.table} u={u} top={top} side={quiet ? 'var(--c-strong)' : vars['--d']} round={p.shape === 'round'}
      alpha={dim ? 0.22 : 1} className={cn(interactive && 'cursor-pointer')}
      onClick={interactive ? onPick : undefined} onDoubleClick={interactive ? onOpen : undefined} onHover={interactive ? onHover : undefined}>
      <Billboard lift={1}>
        <span className={cn('relative flex flex-col items-center gap-0.5', dim && 'opacity-30')}>
          <b className={cn('num font-700 leading-none', u >= 16 ? 'text-sm' : 'text-xs')}>{id}</b>
          {hover && tb && <span className="pointer-events-none absolute bottom-full mb-1 px-2 py-1 rounded-rk bg-inverse text-on-inverse text-[11px] leading-tight whitespace-nowrap">{id} · {stName('table', tb.st, lang)} · {busyTable(tb.st) ? t('tables.guests', { a: tb.ppl, b: tb.seats }) : t('tables.seats', { n: tb.seats })}</span>}
        </span>
      </Billboard>
      {current && <i className="absolute -inset-1 rounded-[4px] pointer-events-none" style={{ boxShadow: '0 0 0 2px var(--c-ink)', borderRadius: p.shape === 'round' ? '50%' : 4, transform: `translateZ(${H.table * u + 0.5}px)` }} />}
    </Box>
  );
}
