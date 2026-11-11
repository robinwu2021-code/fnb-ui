// 设置 › 设备 › 设备列表（方案 §1.4）：类型筛选 · 已添加设备表 · 附近发现（局域网广播的副 POS / 厨房屏）· 右侧详情 340
// 「打印机」页 = 同一组件传 kind="printer"，不维护两份。添加设备走侧滑 ov=devadd（overlays/DeviceAdd.tsx）。
import { useEffect, useState } from 'react';
import { useStore } from '@/store';
import { KINDS, kindIcon, type Device, type DeviceKind } from '@/data/devices';
import { stGroup } from '@/lib/states';
import { useLang, useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Button } from '@/components/ui/button';
import { Filter, FilterCount } from '@/components/ui/toggle';
import { StatusTag, Chip } from '@/components/ui/badge';
import { Table, Tr, Td, Nm2 } from '@/components/ui/table';
import { Cells, Cell } from '@/components/ui/description-list';
import { Input } from '@/components/ui/input';
import { Note, Empty } from '@/components/ui/feedback';

export const devAlert = (d: Device) => { const g = stGroup('device', d.st); return !d.todo && (g === 'action' || g === 'failed'); };
export function connText(d: Device) { return d.ip ? d.ip + (d.port ? ':' + d.port : '') : d.conn || '—'; }

/** 每台设备当前最该做的一件事（表格行末 + 详情主按钮） */
function MainAct({ d, size = 'sm', block }: { d: Device; size?: 'sm' | 'md'; block?: boolean }) {
  const t = useT(); const dispatch = useStore((s) => s.act);
  if (d.todo) return <StatusTag ent="feature" k="todo" />;
  if (d.st === 'pairing') return <Button variant="muted" size={size} block={block} disabled><Ico name="loader-circle" size={16} className="animate-spin" />{t('dv.a.connect')}</Button>;
  if (d.st === 'no_paper') return <Button variant="soft" size={size} block={block} onClick={() => dispatch('dev:paper:' + d.id)}>{t('dv.loadPaper')}</Button>;
  if (d.st === 'offline' || d.st === 'unplugged') return <Button variant="soft" size={size} block={block} onClick={() => dispatch('dev:reconnect:' + d.id)}>{t('dv.reconnect')}</Button>;
  if (d.st === 'to_sync' || d.st === 'sync_failed') return <Button variant="soft" size={size} block={block} onClick={() => dispatch('dev:sync:' + d.id)}>{t('dv.syncNow')}</Button>;
  if (d.kind === 'printer') return <Button variant="muted" size={size} block={block} onClick={() => dispatch('dev:test:' + d.id)}>{t('dv.test')}</Button>;
  if (d.kind === 'cds') return <Button variant="muted" size={size} block={block} onClick={() => dispatch('dev:test:' + d.id)}>{t('dv.preview')}</Button>;
  return null;
}
const KindIcon = ({ k, className }: { k: DeviceKind; className?: string }) => <span className={cn('inline-grid place-items-center w-8 h-8 rounded-rk bg-wash text-ink flex-none', className)}><Ico name={kindIcon(k)} size={16} /></span>;

export function DevicesPage({ kind }: { kind?: DeviceKind }) {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const nm = (d: Device) => (lang === 'en' ? d.en : d.name);
  const added = s.devices.filter((d) => d.st !== 'found' && (!kind || d.kind === kind));
  const found = s.devices.filter((d) => d.st === 'found' || (d.st === 'pairing' && !d.addedAt));
  const cur = kind || s.dkind;
  const list = added.filter((d) => cur === 'all' || d.kind === cur);
  const issues = added.filter(devAlert).length;
  const sel = s.devices.find((d) => d.id === s.dsel && (!kind || d.kind === kind)) || list[0];
  useEffect(() => { if (sel && sel.id !== s.dsel) s.set({ dsel: sel.id }); }, [sel?.id]);
  const kinds = KINDS.filter(([k]) => added.some((d) => d.kind === k));
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_340px] gap-4 items-start">
      <div className="flex flex-col gap-3 min-w-0">
        {!kind && (
          <div className="flex flex-wrap gap-gap items-center">
            <Filter pressed={cur === 'all'} onPressedChange={() => dispatch('dev:kind:all')}>{t('dv.all')}<FilterCount n={added.length} /></Filter>
            {kinds.map(([k]) => { const n = added.filter((d) => d.kind === k), bad = n.filter(devAlert).length;
              return <Filter key={k} pressed={cur === k} onPressedChange={() => dispatch('dev:kind:' + k)}><Ico name={kindIcon(k)} size={15} />{t('dv.kind.' + k)}<FilterCount n={n.length} alert={bad > 0} /></Filter>; })}
            {issues > 0 && <span className="ml-auto inline-flex items-center gap-1 text-sm text-danger-text font-600"><Ico name="triangle-alert" size={15} />{t('dv.issues', { n: issues })}</span>}
          </div>
        )}
        {list.length === 0 ? <Empty title={t('dv.empty')} /> : (
          <Table cols="minmax(0,1.4fr) minmax(0,.9fr) minmax(0,1.2fr) 96px 96px" head={[t('set.th.name'), t('dv.th.type'), t('set.th.conn'), t('set.th.status'), '']}>
            {list.map((d) => (
              <Tr key={d.id} on={sel?.id === d.id} onClick={() => dispatch('dev:sel:' + d.id)}>
                <Td><span className="flex items-center gap-2 min-w-0"><KindIcon k={d.kind} className={cn(devAlert(d) && 'bg-danger-tint text-danger-text')} /><Nm2 a={<span className="ellipsis block">{nm(d)}</span>} b={d.model} /></span></Td>
                <Td className="text-sub">{t('dv.kind.' + d.kind)}</Td>
                <Td><span className="flex flex-col leading-[1.3] min-w-0"><span className="num ellipsis">{connText(d)}</span>{d.proto && <small className="text-xs text-sub">{t('dv.proto.' + d.proto)}</small>}</span></Td>
                <Td>{d.todo ? <StatusTag ent="feature" k="todo" form="dot" /> : <StatusTag ent="device" k={d.st} />}</Td>
                <Td><span onClick={(e) => e.stopPropagation()} className="inline-flex">{d.todo ? null : <MainAct d={d} />}</span></Td>
              </Tr>
            ))}
          </Table>
        )}
        {kind !== 'printer' && (cur === 'all' || cur === 'pos' || cur === 'kds') && (
          <div className="flex flex-col gap-2 mt-2">
            <div className="sec mb-0"><span className="inline-flex items-center gap-1.5"><Ico name="radar" size={14} />{t('dv.nearby', { t: '12:31' })}</span><Button variant="link" className="hit text-sm" onClick={() => dispatch('toast:dv.a.nearbyNone')}>{t('dv.a.rescan')}</Button></div>
            <span className="help -mt-1">{t('dv.nearbyHint')}</span>
            {found.length === 0 ? <span className="text-sm text-placeholder py-2">{t('dv.a.nearbyNone')}</span> : found.map((d) => (
              <div key={d.id} className="flex items-center gap-3 rounded-rt bg-st-bk px-3 py-2" style={{ ['--bk' as string]: 'var(--c-st-booked-block)' }}>
                <KindIcon k={d.kind} className="bg-surface" />
                <span className="flex flex-col leading-tight min-w-0 flex-1"><b className="font-600">{nm(d)}</b><small className="text-xs text-sub">{t('dv.kind.' + d.kind)} · <span className="num">{d.ip}</span> · {d.model}</small></span>
                <StatusTag ent="device" k={d.st} />
                <Button size="sm" disabled={d.st === 'pairing'} onClick={() => dispatch('dev:pair:' + d.id)}>{d.st === 'pairing' ? <Ico name="loader-circle" size={16} className="animate-spin" /> : <Ico name="plus" size={16} />}{t('dv.pair')}</Button>
              </div>
            ))}
          </div>
        )}
      </div>
      {sel ? <Detail d={sel} /> : <div className="rounded-rt bg-wash"><Empty title={t('dv.pickHint')} /></div>}
    </div>
  );
}

function Detail({ d }: { d: Device }) {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const [confirm, setConfirm] = useState(false);
  useEffect(() => { setConfirm(false); }, [d.id]);
  useEffect(() => { if (!confirm) return; const h = setTimeout(() => setConfirm(false), 3000); return () => clearTimeout(h); }, [confirm]);
  const rename = (v: string) => s.set((st) => ({ devices: st.devices.map((x) => (x.id === d.id ? (lang === 'en' ? { ...x, en: v } : { ...x, name: v }) : x)) }));
  const note = d.todo ? t('dv.todoDesc') : d.st === 'no_paper' ? t('dv.noPaperNote') : d.st === 'offline' ? t('dv.offNote') : d.role === 'sub' ? t('dv.subNote') : '';
  return (
    <aside className="sticky top-0 flex flex-col gap-3 rounded-rt bg-wash p-pad">
      <div className="flex items-center gap-3">
        <KindIcon k={d.kind} className="w-11 h-11 bg-surface" />
        <span className="flex flex-col min-w-0 flex-1 leading-tight"><b className="text-md font-600 ellipsis">{lang === 'en' ? d.en : d.name}</b><small className="text-xs text-sub">{t('dv.kind.' + d.kind)}{d.model ? ' · ' + d.model : ''}</small></span>
        {d.todo ? <StatusTag ent="feature" k="todo" /> : <StatusTag ent="device" k={d.st} form="chip" />}
      </div>
      {note && <Note tone={d.todo ? undefined : devAlert(d) ? 'warn' : undefined}>{note}</Note>}
      <label className="flex flex-col gap-1"><span className="text-xs text-sub">{t('dv.d.name')}</span><Input value={lang === 'en' ? d.en : d.name} onChange={(e) => rename(e.target.value)} className="bg-surface h-ctl-sm" /></label>
      <Cells className="bg-surface rounded-rk px-pad [&>div]:min-h-10 text-sm">
        <Cell k={t('dv.d.conn')} v={<span className="num">{connText(d)}</span>} />
        {d.proto && <Cell k={t('dv.d.proto')} v={t('dv.proto.' + d.proto)} />}
        {d.via && <Cell k={t('dv.d.via')} v={t('dv.via.' + d.via)} />}
        {d.use && d.use.length > 0 && <Cell k={d.kind === 'kds' ? t('dv.d.stations') : t('dv.d.use')}><span className="flex flex-wrap justify-end gap-1">{d.use.map((u) => <Chip key={u}>{t((d.kind === 'kds' ? 'dv.stn.' : 'dv.use.') + u)}</Chip>)}</span></Cell>}
        {d.drawer && <Cell k={t('dv.d.drawer')} v={t('dv.d.drawerOn')} />}
        {d.role && <Cell k={t('dv.d.role')} v={t('dv.d.roleSub')} />}
        {d.lastSync && <Cell k={t('dv.d.sync')} v={<span className="num">{t('dv.d.lastSync', { t: d.lastSync })}</span>} />}
        {d.fw && <Cell k={t('dv.d.fw')} v={<span className="num">{d.fw}</span>} />}
        {d.addedAt && <Cell k={t('dv.d.added')} v={<span className="num">{d.addedAt}</span>} />}
      </Cells>
      {!d.todo && <MainAct d={d} size="md" block />}
      <div className="flex gap-gap">
        {d.kind === 'printer' && <Button variant="muted" size="sm" className="flex-1" onClick={() => dispatch('dev:edit:' + d.id)}><Ico name="settings-2" size={16} />{t('dv.changeConn')}</Button>}
        <Button variant={confirm ? 'danger-solid' : 'danger'} size="sm" className="flex-1" onClick={() => (confirm ? dispatch('dev:remove:' + d.id) : setConfirm(true))}><Ico name="trash-2" size={16} />{confirm ? t('dv.removeConfirm') : t('dv.remove')}</Button>
      </div>
    </aside>
  );
}
