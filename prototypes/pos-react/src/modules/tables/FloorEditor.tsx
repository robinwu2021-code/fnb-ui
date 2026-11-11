// 编辑平面（方案 §4.5）：工具条（桌区 · 撤销 / 重做 · 自动排布 · 恢复默认 · 取消 / 保存）+ 桌台库 / 固定物库 · 属性面板（详情栏）
import { useEffect, useState } from 'react';
import { useStore } from '@/store';
import { AREAS, autoArrange, FIX_ICON, FIX_LIB, freeSpot, TABLE_LIB, type FixKind, type FloorData, type Place, type Shape } from '@/data/floors';
import { notify } from '@/store/actions';
import { useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Button } from '@/components/ui/button';
import { Seg } from '@/components/ui/toggle-group';
import { Stepper } from '@/components/ui/stepper';
import { Input } from '@/components/ui/input';
import { StatusTag } from '@/components/ui/badge';
import { Cells, Cell } from '@/components/ui/description-list';
import { Note } from '@/components/ui/feedback';
import { Panel, Block } from '@/components/ui/panel';
import { cancelEdit, change, nextTableId, redo, resetDraft, saveEdit, undo } from './floor-ops';
import { legal, removeSel } from './FloorCanvas';

const SHAPE_ICON: Record<Shape, string> = { square: 'square', rect: 'rectangle-horizontal', round: 'circle', booth: 'sofa' };
const LIB_ICON: Record<string, string> = { t2: 'square', t4: 'square', t6: 'rectangle-horizontal', r8: 'circle', booth: 'sofa' };

export function EditorHead({ area }: { area: string }) {
  const t = useT(); const s = useStore();
  const d = s.fdraft!;
  const addTable = (k: string) => {
    const [, shape, w, h, seats] = TABLE_LIB.find((x) => x[0] === k)!;
    const spot = freeSpot(d, area, w, h); if (!spot) { notify(useStore.getState, 'fl.noRoom'); return; }
    const id = nextTableId(d, area, s.tables.map((x) => x.id));
    change((n) => { n.place[id] = { area, ...spot, w, h, shape, rot: 0, seats }; }); s.set({ fsel: { kind: 't', id } }); notify(useStore.getState, 'fl.added', { n: id });
  };
  const addFix = (kind: FixKind, w: number, h: number) => {
    const solid = kind === 'pillar' || kind === 'plant' || kind === 'bar' || kind === 'cashier';
    const spot = solid ? freeSpot(d, area, w, h) : { x: 1, y: 1 }; if (!spot) { notify(useStore.getState, 'fl.noRoom'); return; }
    const id = kind + '-' + Date.now().toString(36);
    change((n) => { n.areas[area].fixtures.push({ id, kind, x: spot.x, y: spot.y, w, h, label: kind === 'zone' ? t('fl.fix.zone') : undefined }); }); s.set({ fsel: { kind: 'f', id } });
  };
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-gap flex-wrap">
        <b className="h-title mr-1 inline-flex items-center gap-2"><Ico name="pencil-ruler" size={18} />{t('fl.editing')}</b>
        <Seg value={area} onChange={(v) => s.set({ farea: v, fsel: null, tst: v })} items={AREAS.map((a) => ({ v: a, label: a === '大厅' ? t('tables.hall') : t('tables.room') }))} />
        <span className="flex gap-1"><Button variant="muted" size="icon-sm" aria-label={t('fl.undo')} title={t('fl.undo')} disabled={!s.fhist.length} onClick={undo}><Ico name="undo-2" size={16} /></Button><Button variant="muted" size="icon-sm" aria-label={t('fl.redo')} title={t('fl.redo')} disabled={!s.ffut.length} onClick={redo}><Ico name="redo-2" size={16} /></Button></span>
        <Seg value={s.fiso ? 'iso' : 'plan'} onChange={(v) => s.set({ fiso: v === 'iso', fsel: null })} items={[{ v: 'plan', label: t('fl.view.floor') }, { v: 'iso', label: t('fl.preview') }]} />
        <Button variant="muted" size="sm" disabled={s.fiso} onClick={() => { change((n) => autoArrange(n, area)); s.set({ fsel: null }); }}><Ico name="layout-grid" size={16} />{t('fl.auto')}</Button>
        <Button variant="muted" size="sm" onClick={resetDraft}><Ico name="rotate-ccw" size={16} />{t('fl.reset')}</Button>
        <span className="ml-auto flex gap-gap"><Button variant="muted" size="sm" onClick={cancelEdit}>{t('fl.cancel')}</Button><Button size="sm" onClick={saveEdit}><Ico name="check" size={16} />{t('fl.save')}</Button></span>
      </div>
      <div className="flex items-center gap-1.5 flex-wrap">
        <span className="text-xs text-sub mr-0.5">{t('fl.addTable')}</span>
        {TABLE_LIB.map(([k]) => <button key={k} type="button" onClick={() => addTable(k)} className="inline-flex items-center gap-1 h-8 px-2 rounded-rb bg-wash hover:bg-wash-strong text-sm"><Ico name={LIB_ICON[k]} size={14} />{t('fl.lib.' + k)}</button>)}
        <span className="text-xs text-sub ml-2 mr-0.5">{t('fl.addFix')}</span>
        {FIX_LIB.map(([k, w, h]) => <button key={k} type="button" onClick={() => addFix(k, w, h)} className="inline-flex items-center gap-1 h-8 px-2 rounded-rb bg-wash hover:bg-wash-strong text-sm"><Ico name={FIX_ICON[k]} size={14} />{t('fl.fix.' + k)}</button>)}
      </div>
      <span className="help">{t('fl.hint')}</span>
    </div>
  );
}

export function FloorProps({ area }: { area: string }) {
  const t = useT(); const s = useStore(); const d = s.fdraft!; const sel = s.fsel;
  const p = sel?.kind === 't' ? d.place[sel.id] : undefined;
  const f = sel?.kind === 'f' ? d.areas[area].fixtures.find((x) => x.id === sel.id) : undefined;
  const head = <b className="h-title">{p ? sel!.id : f ? f.label || t('fl.fix.' + f.kind) : t('fl.props')}</b>;
  return (
    <Panel area="detail" head={head}>
      {p && sel ? <TableProps id={sel.id} p={p} area={area} d={d} /> : f && sel ? <FixProps id={sel.id} area={area} d={d} /> : <AreaInfo area={area} d={d} />}
    </Panel>
  );
}

/** 尺寸 / 形状改动前先试放：放不下提示，不改 */
function tryBox(d: FloorData, area: string, kind: 't' | 'f', id: string, patch: Partial<Place>, apply: (n: FloorData) => void) {
  const cur = kind === 't' ? d.place[id] : d.areas[area].fixtures.find((x) => x.id === id)!;
  const box = { x: cur.x, y: cur.y, w: patch.w ?? cur.w, h: patch.h ?? cur.h };
  if (!legal(d, area, kind, id, box)) { notify(useStore.getState, 'fl.noRoom'); return; }
  change(apply);
}

function TableProps({ id, p, area, d }: { id: string; p: Place; area: string; d: FloorData }) {
  const t = useT(); const s = useStore(); const tb = s.tables.find((x) => x.id === id);
  const seats = p.seats ?? tb?.seats ?? 4; const tie = p.shape === 'round' || p.shape === 'square';
  const [name, setName] = useState(id); useEffect(() => setName(id), [id]);
  const rename = () => {
    const v = name.trim().toUpperCase(); if (!v || v === id) { setName(id); return; }
    if (d.place[v] || s.tables.some((x) => x.id === v)) { notify(useStore.getState, 'fl.idDup'); setName(id); return; }
    change((n) => { n.place[v] = n.place[id]; delete n.place[id]; }); s.set({ fsel: { kind: 't', id: v } });
  };
  const setShape = (sh: Shape) => { const side = Math.max(p.w, p.h); const w = sh === 'rect' ? Math.max(p.w, p.h + 2) : sh === 'booth' ? p.w : side, h = sh === 'rect' ? Math.min(p.w, p.h) : sh === 'booth' ? p.h : side; tryBox(d, area, 't', id, { w, h }, (n) => { n.place[id] = { ...n.place[id], shape: sh, w, h }; }); };
  const size = (dw: number, dh: number) => { const w = Math.max(2, p.w + dw), h = tie ? w : Math.max(2, p.h + dh); tryBox(d, area, 't', id, { w, h: tie ? w : h }, (n) => { n.place[id] = { ...n.place[id], w, h: tie ? w : h }; }); };
  const rotate = () => { const w = p.h, h = p.w; tryBox(d, area, 't', id, { w, h }, (n) => { n.place[id] = { ...n.place[id], w, h, rot: ((p.rot + 90) % 360) as Place['rot'] }; }); };
  return (
    <div className="flex flex-col gap-3">
      <label className="flex flex-col gap-1"><span className="text-xs text-sub">{t('fl.tableNo')}</span>
        <Input value={name} disabled={!!tb} onChange={(e) => setName(e.target.value)} onBlur={rename} onKeyDown={(e) => e.key === 'Enter' && rename()} className="h-ctl-sm num font-600" />
        {tb && <small className="help">{t('fl.idFixed')}</small>}</label>
      <Cells>
        {tb && <Cell k={t('fl.status')}><StatusTag ent="table" k={tb.st} /></Cell>}
        <Cell k={t('fl.seats')}><Stepper q={seats} onDec={() => seats > 1 && change((n) => { n.place[id] = { ...n.place[id], seats: seats - 1 }; })} onInc={() => seats < 20 && change((n) => { n.place[id] = { ...n.place[id], seats: seats + 1 }; })} /></Cell>
      </Cells>
      <div className="flex flex-col gap-1.5"><span className="text-xs text-sub">{t('fl.shape')}</span>
        <div className="grid grid-cols-4 gap-1">{(['square', 'rect', 'round', 'booth'] as Shape[]).map((sh) => <button key={sh} type="button" onClick={() => setShape(sh)} aria-pressed={p.shape === sh} className={cn('flex flex-col items-center gap-1 py-2 rounded-rk text-xs', p.shape === sh ? 'sel' : 'bg-wash hover:bg-wash-strong')}><Ico name={SHAPE_ICON[sh]} size={18} />{t('fl.shape.' + sh)}</button>)}</div>
      </div>
      <Cells>
        <Cell k={t('fl.w')}><Stepper q={p.w} onDec={() => size(-1, 0)} onInc={() => size(1, 0)} /></Cell>
        <Cell k={t('fl.h')}>{tie ? <span className="help">{t('fl.tied')}</span> : <Stepper q={p.h} onDec={() => size(0, -1)} onInc={() => size(0, 1)} />}</Cell>
        <Cell k={t('fl.pos')} v={<span className="num">{p.x}, {p.y}</span>} />
      </Cells>
      <div className="flex gap-gap"><Button variant="muted" size="sm" className="flex-1" onClick={rotate}><Ico name="rotate-cw" size={16} />{t('fl.rotate')}</Button><Button variant="danger" size="sm" className="flex-1" onClick={() => { removeSel(area); notify(useStore.getState, 'fl.removedT', { n: id }); }}><Ico name="square-x" size={16} />{t('fl.removeT')}</Button></div>
    </div>
  );
}

function FixProps({ id, area, d }: { id: string; area: string; d: FloorData }) {
  const t = useT(); const f = d.areas[area].fixtures.find((x) => x.id === id)!;
  const [label, setLabel] = useState(f.label || ''); useEffect(() => setLabel(f.label || ''), [id]);
  const size = (dw: number, dh: number) => { const w = Math.max(1, f.w + dw), h = Math.max(1, f.h + dh); tryBox(d, area, 'f', id, { w, h }, (n) => { n.areas[area].fixtures = n.areas[area].fixtures.map((x) => (x.id === id ? { ...x, w, h } : x)); }); };
  const named = f.kind !== 'pillar' && f.kind !== 'plant';
  return (
    <div className="flex flex-col gap-3">
      <Cells><Cell k={t('fl.kind')}><span className="inline-flex items-center gap-1.5"><Ico name={FIX_ICON[f.kind]} size={16} />{t('fl.fix.' + f.kind)}</span></Cell></Cells>
      {named && <label className="flex flex-col gap-1"><span className="text-xs text-sub">{t('fl.label')}</span><Input value={label} onChange={(e) => setLabel(e.target.value)} onBlur={() => label !== (f.label || '') && change((n) => { n.areas[area].fixtures = n.areas[area].fixtures.map((x) => (x.id === id ? { ...x, label } : x)); })} className="h-ctl-sm" /></label>}
      <Cells>
        <Cell k={t('fl.w')}><Stepper q={f.w} onDec={() => size(-1, 0)} onInc={() => size(1, 0)} /></Cell>
        <Cell k={t('fl.h')}><Stepper q={f.h} onDec={() => size(0, -1)} onInc={() => size(0, 1)} /></Cell>
      </Cells>
      {(f.kind === 'zone' || f.kind === 'room' || f.kind === 'door') && <Note>{t('fl.groundNote')}</Note>}
      <Button variant="danger" size="sm" onClick={() => removeSel(area)}><Ico name="trash-2" size={16} />{t('fl.removeF')}</Button>
    </div>
  );
}

/** 没选中时：本桌区统计 · 未摆放的桌台（点一下放到空位）· 操作提示 */
function AreaInfo({ area, d }: { area: string; d: FloorData }) {
  const t = useT(); const s = useStore();
  const here = Object.entries(d.place).filter(([, p]) => p.area === area);
  const seats = here.reduce((a, [id, p]) => a + (p.seats ?? s.tables.find((x) => x.id === id)?.seats ?? 4), 0);
  const pre = area === '包间' ? 'V' : 'A';
  const unplaced = s.tables.filter((x) => x.id.startsWith(pre) && !d.place[x.id]);
  const place = (id: string, seatsN: number) => {
    const [w, h, shape]: [number, number, Shape] = pre === 'V' ? [6, 6, 'round'] : [4, 4, 'square'];
    const spot = freeSpot(d, area, w, h); if (!spot) { notify(useStore.getState, 'fl.noRoom'); return; }
    change((n) => { n.place[id] = { area, ...spot, w, h, shape, rot: 0, seats: seatsN }; }); s.set({ fsel: { kind: 't', id } });
  };
  return (
    <div className="flex flex-col gap-3">
      <span className="text-sm">{t('fl.areaStats', { a: here.length, b: seats })}</span>
      <Block title={t('fl.unplaced', { n: unplaced.length })}>
        {unplaced.length === 0 ? <span className="help">{t('fl.allPlaced')}</span> : <div className="flex flex-wrap gap-1.5">{unplaced.map((x) => <Button key={x.id} variant="muted" size="sm" onClick={() => place(x.id, x.seats)}><Ico name="plus" size={14} />{x.id}</Button>)}</div>}
      </Block>
      <Note>{t('fl.tips')}</Note>
    </div>
  );
}
