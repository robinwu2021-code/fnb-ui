// 设置 › 外卖与渠道 › 平台对接（方案 §2.3）：按门店地区列平台卡 · 其他平台（未实现）· 接单规则；点卡片进平台详情。
// 平台详情：接单（暂停方式按平台不同）· 门店（门店 ID 只读、备餐时长、平台价加价）· 分类映射 · 平台要求 · 同步概况 · 断开连接。
import { useEffect, useState, type ReactNode } from 'react';
import { useStore } from '@/store';
import { CAT_ALL, DISHES } from '@/data/sample';
import { PLAT_CATS, platDef, platsOf, REGIONS, type PlatDef, type PlatKey } from '@/data/platforms';
import { pauseLabel } from '@/lib/channels';
import { useLang, useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Button } from '@/components/ui/button';
import { Seg } from '@/components/ui/toggle-group';
import { Switch } from '@/components/ui/switch';
import { Input } from '@/components/ui/input';
import { StatusTag } from '@/components/ui/badge';
import { Cells, Cell } from '@/components/ui/description-list';
import { Table, Tr, Td } from '@/components/ui/table';
import { Note, Empty } from '@/components/ui/feedback';
import { Block } from '@/components/ui/panel';
import { Menu, MenuTrigger, MenuContent, MenuItem, MenuSep } from '@/components/ui/dropdown-menu';
import { PlatLogo } from '@/components/ui/plat-logo';

export function PlatStatus({ k }: { k: PlatKey }) {
  const t = useT(); const def = platDef(k)!; const ps = useStore((s) => s.plats[k]);
  if (!def.impl) return <StatusTag ent="feature" k="todo" />;
  if (ps.st === 'paused') return <StatusTag ent="campaign" k="paused" label={pauseLabel(ps, t)} />;
  if (ps.st === 'connected') return <StatusTag ent="device" k="online" label={t('md.connected')} />;
  return <StatusTag ent="device" k="unplugged" label={t('pf.st.disconnected')} />;
}
function useCounts(k: PlatKey) {
  const sync = useStore((s) => s.sync);
  const c = (st: string) => Object.keys(sync).filter((n) => sync[n][k] === st).length;
  return { ok: c('synced'), pend: c('pending'), fail: c('failed') };
}
function Counts({ k }: { k: PlatKey }) {
  const t = useT(); const { ok, pend, fail } = useCounts(k);
  return <span className="flex flex-wrap gap-2"><StatusTag ent="sync" k="synced" label={t('md.syncedN', { n: ok })} form="dot" /><StatusTag ent="sync" k="pending" label={t('md.pendingN', { n: pend })} form="dot" />{fail > 0 && <StatusTag ent="sync" k="failed" label={t('md.failedN', { n: fail })} form="dot" />}</span>;
}

export function PlatformsPage() {
  const t = useT(); const s = useStore(), dispatch = s.act;
  const defs = platsOf(s.region), live = defs.filter((p) => p.impl), todo = defs.filter((p) => !p.impl);
  const [rules, setRules] = useState({ auto: false, kitchen: true, sound: true, rider: true });
  if (s.psel && platDef(s.psel)?.region === s.region) return <PlatDetail k={s.psel} />;
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3 flex-wrap"><span className="text-sm text-sub">{t('pf.region')}</span><Seg value={s.region} onChange={(v) => dispatch('plat:region:' + v)} items={REGIONS.map((r) => ({ v: r, label: t('pf.region.' + r) }))} /><span className="help">{t('pf.regionHint')}</span></div>
      <div className="grid grid-cols-2 gap-gap">{live.map((p) => <PlatCard key={p.key} p={p} />)}</div>
      {todo.length > 0 && <div className="flex flex-col gap-2">
        <span className="sec mb-0"><span>{t('pf.others')}</span></span>
        <div className="grid grid-cols-3 gap-gap">{todo.map((p) => { const ps = s.plats[p.key]; return (
          <button key={p.key} type="button" onClick={() => dispatch('plat:sel:' + p.key)} className="flex items-center gap-3 p-3 rounded-rt bg-wash hover:bg-wash-strong text-left">
            <PlatLogo k={p.key} size={28} className="opacity-70" /><span className="flex flex-col leading-tight flex-1 min-w-0"><b className="font-600 text-sub">{p.name}</b><small className="text-xs text-sub">{ps.credSaved ? t('pf.credOn') : t('pf.enterCred')}</small></span><StatusTag ent="feature" k="todo" form="dot" />
          </button>); })}</div>
      </div>}
      <Block title={t('pf.intakeRules')}>
        <Cells>{(['auto', 'kitchen', 'sound', 'rider'] as const).map((r) => <Cell key={r} k={t('pf.r.' + r)} strong><Switch checked={rules[r]} onCheckedChange={(v) => setRules((x) => ({ ...x, [r]: v }))} aria-label={t('pf.r.' + r)} /></Cell>)}</Cells>
      </Block>
    </div>
  );
}

function PlatCard({ p }: { p: PlatDef }) {
  const t = useT(); const s = useStore(), dispatch = s.act; const ps = s.plats[p.key];
  if (ps.st === 'disconnected') return (
    <div className="flex flex-col gap-2 p-4 rounded-rt bg-wash">
      <div className="flex items-center gap-3"><PlatLogo k={p.key} size={36} /><b className="text-md font-700 flex-1">{p.name}</b><PlatStatus k={p.key} /></div>
      <span className="help">{t(p.auth === 'oauth' ? 'pf.authOauth' : 'pf.authCred')} · {t('pf.mode.' + p.menuMode)}</span>
      <ul className="m-0 pl-4 text-xs text-sub flex flex-col gap-0.5">{p.rules.slice(0, 2).map((r, i) => <li key={i}><LangPick r={r} /></li>)}</ul>
      <div className="flex gap-gap mt-auto pt-1"><Button size="sm" onClick={() => dispatch('plat:add:' + p.key)}><Ico name="link" size={16} />{t('pf.connect')}</Button><Button variant="link" className="hit text-sm" onClick={() => dispatch('plat:sel:' + p.key)}>{t('pf.details')}</Button></div>
    </div>
  );
  return (
    <div className="flex flex-col gap-2 p-4 rounded-rt bg-st-bk" style={{ ['--bk' as string]: ps.st === 'paused' ? 'var(--c-st-held-block)' : 'var(--c-st-done-block)' }}>
      <div className="flex items-center gap-3"><PlatLogo k={p.key} size={36} /><span className="flex flex-col leading-tight flex-1 min-w-0"><b className="text-md font-700">{p.name}</b><small className="text-xs text-sub num">{t('pf.storeId')} {ps.storeId}</small></span><PlatStatus k={p.key} /></div>
      <Counts k={p.key} />
      <span className="help">{t('pf.lastPush', { t: ps.lastSync || '—' })} · {t('pf.mode.' + p.menuMode)}</span>
      <div className="flex gap-gap mt-1 flex-wrap">
        <Button variant="muted" size="sm" onClick={() => dispatch('plat:push:' + p.key)}><Ico name="upload" size={16} />{t('pf.push')}</Button>
        <Button variant="muted" size="sm" onClick={() => dispatch('plat:sel:' + p.key)}>{t('pf.settings')}</Button>
      </div>
    </div>
  );
}
function useLangPick(r: [string, string]) { const lang = useLang(); return lang === 'en' ? r[1] : r[0]; }
const LangPick = ({ r }: { r: [string, string] }) => <>{useLangPick(r)}</>;

/* ── 平台详情 ── */
function PlatDetail({ k }: { k: PlatKey }) {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act; const p = platDef(k)!, ps = s.plats[k];
  const [confirm, setConfirm] = useState(false);
  useEffect(() => { if (!confirm) return; const h = setTimeout(() => setConfirm(false), 3000); return () => clearTimeout(h); }, [confirm]);
  const head = (
    <div className="flex items-center gap-3">
      <Button variant="muted" size="sm" onClick={() => dispatch('plat:back')}><Ico name="chevron-left" size={16} />{t('pf.back')}</Button>
      <PlatLogo k={k} size={40} /><span className="flex flex-col leading-tight flex-1"><b className="text-lg font-700">{p.name}</b><small className="text-xs text-sub">{t('pf.region.' + p.region)}{ps.storeId ? ' · ' + t('pf.storeId') + ' ' + ps.storeId : ''}</small></span>
      <PlatStatus k={k} />
    </div>
  );
  if (!p.impl) return <div className="flex flex-col gap-4">{head}<CredForm p={p} /></div>;
  if (ps.st === 'disconnected') return (
    <div className="flex flex-col gap-4">{head}
      <div className="rounded-rt bg-wash"><Empty icon={<Ico name="link-2-off" size={40} className="text-sub" />} title={t('pf.notConnected', { n: p.name })} desc={t(p.auth === 'oauth' ? 'pf.authOauth' : 'pf.authCred')} /><div className="flex justify-center pb-5"><Button onClick={() => dispatch('plat:add:' + k)}><Ico name="link" size={16} />{t('pf.connect')}</Button></div></div>
      <Rules p={p} />
    </div>
  );
  const cats = CAT_ALL; const unmapped = cats.filter((c) => !ps.map[c.key]);
  return (
    <div className="flex flex-col gap-4">{head}
      <div className="grid grid-cols-[minmax(0,1fr)_300px] gap-4 items-start">
        <div className="flex flex-col gap-4 min-w-0">
          <Block title={t('pf.intake')}><div className="rounded-rk bg-wash px-pad py-2"><PauseControl p={p} /></div></Block>
          <Block title={t('pf.store')}>
            <Cells>
              <Cell k={t('pf.storeId')}><span className="flex flex-col items-end"><span className="num">{ps.storeId}</span><small className="help">{t('pf.byPlatform')}</small></span></Cell>
              <Cell k={t('pf.prep')}><Seg value={String(ps.prep)} onChange={(v) => dispatch('plat:prep:' + k + ':' + v)} items={['10', '15', '20', '30'].map((v) => ({ v, label: t('pf.min', { n: v }) }))} /></Cell>
              <Cell k={<span className="flex flex-col"><span>{t('pf.markup')}</span><small className="help">{t('pf.markupHint')}</small></span>}><Seg value={String(ps.markup)} onChange={(v) => dispatch('plat:markup:' + k + ':' + v)} items={['0', '5', '10', '15', '20'].map((v) => ({ v, label: v === '0' ? t('pf.noMarkup') : '+' + v + '%' }))} /></Cell>
            </Cells>
          </Block>
          <Block title={t('pf.mapping')} right={unmapped.length ? <span className="text-danger-text">{t('pf.unmappedN', { n: unmapped.length })}</span> : <span>{t('pf.allMapped')}</span>}>
            <MapTable k={k} map={ps.map} onPick={(c, v) => dispatch('plat:map:' + k + ':' + c + ':' + v)} />
            {unmapped.length > 0 && <Note tone="warn" className="mt-2">{t('pf.unmappedNote')}</Note>}
          </Block>
        </div>
        <aside className="flex flex-col gap-3 rounded-rt bg-wash p-pad">
          <b className="text-sm font-600">{t('pf.syncOverview')}</b>
          <Counts k={k} />
          <span className="help">{t('pf.lastPush', { t: ps.lastSync || '—' })} · {t('pf.mode.' + p.menuMode)}</span>
          <Button block onClick={() => dispatch('plat:push:' + k)}><Ico name="upload" size={16} />{t('pf.push')}</Button>
          <Button variant="link" className="hit text-sm self-start" onClick={() => dispatch('plat:gosync')}>{t('pf.goSync')}</Button>
          <Rules p={p} />
          <Button variant={confirm ? 'danger-solid' : 'danger'} size="sm" onClick={() => (confirm ? (setConfirm(false), dispatch('plat:disconnect:' + k)) : setConfirm(true))}><Ico name="unlink" size={16} />{confirm ? t('pf.disconnectConfirm') : t('pf.disconnect')}</Button>
        </aside>
      </div>
    </div>
  );
}
function Rules({ p }: { p: PlatDef }) {
  const t = useT(); if (!p.rules.length) return null;
  return <div className="flex flex-col gap-1.5"><b className="text-sm font-600">{t('pf.rules')}</b><ul className="m-0 p-0 list-none flex flex-col gap-1.5 text-sm">{p.rules.map((r, i) => <li key={i} className="flex gap-1.5 items-start"><Ico name="info" size={15} className="text-sub mt-0.5 flex-none" /><LangPick r={r} /></li>)}</ul></div>;
}
/** 分类映射表：本店分类 → 平台类目（下拉选），未映射标「待处理」 */
export function MapTable({ k, map, onPick, auto }: { k: string; map: Record<string, string>; onPick: (cat: string, v: string) => void; auto?: Record<string, string> }) {
  const t = useT(), lang = useLang();
  return (
    <Table cols="minmax(0,1fr) 20px minmax(0,1.2fr) 56px" head={[t('pf.ourCat'), '', t('pf.platCat'), <span className="block text-right">{t('pf.dishN')}</span>]}>
      {CAT_ALL.map((c) => { const v = map[c.key]; const n = DISHES.filter((d) => d.c === c.key).length; return (
        <Tr key={c.key}>
          <Td>{lang === 'en' ? c.en : c.key}</Td><Td><Ico name="arrow-right" size={14} className="text-sub" /></Td>
          <Td>
            <Menu>
              <MenuTrigger asChild><button type="button" className={cn('inline-flex items-center justify-between gap-2 w-full h-ctl-sm px-3 rounded-rk text-sm text-left', v ? 'bg-wash hover:bg-wash-strong' : 'bg-warning-tint text-warning-text')}>
                <span className="ellipsis">{v || t('pf.unmapped')}</span>{auto && v && auto[c.key] === v && <small className="text-xs text-sub">{t('pa.auto')}</small>}<Ico name="chevrons-up-down" size={14} className="flex-none opacity-60" /></button></MenuTrigger>
              <MenuContent align="start">{PLAT_CATS.map((x) => <MenuItem key={x} onSelect={() => onPick(c.key, x)}>{x}{x === v ? ' ✓' : ''}</MenuItem>)}<MenuSep /><MenuItem onSelect={() => onPick(c.key, '')}>{t('pf.noPush')}</MenuItem></MenuContent>
            </Menu>
          </Td>
          <Td r>{n}</Td>
        </Tr>); })}
    </Table>
  );
}
/** 暂停接单：Uber Eats 选暂停到何时 · talabat 必须选原因 · 其他直接暂停 */
function PauseControl({ p }: { p: PlatDef }) {
  const t = useT(); const s = useStore(), dispatch = s.act; const ps = s.plats[p.key];
  const [ask, setAsk] = useState(false);
  useEffect(() => setAsk(false), [ps.st]);
  const row = (left: ReactNode, right: ReactNode) => <div className="flex items-center justify-between gap-3 min-h-$row flex-wrap">{left}{right}</div>;
  if (ps.st === 'paused') return row(<span className="inline-flex items-center gap-2"><StatusTag ent="campaign" k="paused" label={pauseLabel(ps, t)} /></span>, <Button size="sm" onClick={() => dispatch('plat:resume:' + p.key)}><Ico name="play" size={16} />{t('pf.resume')}</Button>);
  if (!ask) return row(<span className="inline-flex items-center gap-2"><StatusTag ent="device" k="online" label={t('sy.accepting')} /></span>, <Button variant="muted" size="sm" onClick={() => (p.pause === 'plain' ? dispatch('plat:pause:' + p.key) : setAsk(true))}><Ico name="pause" size={16} />{t('pf.pause')}</Button>);
  const opts: [string, string][] = p.pause === 'until' ? [['13:00', t('pf.p30')], ['13:30', t('pf.p60')], ['close', t('pf.untilClose')]] : [['busy', t('pf.why.busy')], ['stock', t('pf.why.stock')], ['device', t('pf.why.device')]];
  return (
    <div className="flex flex-col gap-2 py-1">
      <span className="text-sm">{p.pause === 'until' ? t('pf.pauseFor') : t('pf.pauseWhyReq')}</span>
      <div className="flex gap-gap flex-wrap">{opts.map(([v, l]) => <Button key={v} variant="soft" size="sm" onClick={() => dispatch('plat:pause:' + p.key + ':' + v)}>{l}</Button>)}<Button variant="link" className="hit text-sm" onClick={() => setAsk(false)}>{t('common.cancel')}</Button></div>
    </div>
  );
}
/** 未实现平台：可以先录账号 */
function CredForm({ p }: { p: PlatDef }) {
  const t = useT(); const s = useStore(), dispatch = s.act; const ps = s.plats[p.key];
  const [v, setV] = useState<string[]>(p.cred.map(() => ''));
  return (
    <div className="flex flex-col gap-3 max-w-520px">
      <Note>{t('pf.todoDesc', { n: p.name })}</Note>
      {p.cred.map(([lk, ph], i) => <label key={lk} className="flex flex-col gap-1.5"><span className="gt"><span>{t(lk)}</span></span><Input value={v[i]} placeholder={ph} onChange={(e) => setV((x) => x.map((y, j) => (j === i ? e.target.value : y)))} /></label>)}
      <div className="flex items-center gap-3"><Button disabled={v.some((x) => !x.trim())} onClick={() => dispatch('plat:cred:' + p.key)}>{t('pf.saveCred')}</Button>{ps.credSaved && <StatusTag ent="sync" k="pending" label={t('pf.credOn')} form="dot" />}</div>
      <Cells className="opacity-50 pointer-events-none"><Cell k={t('pf.intake')} v="—" /><Cell k={t('pf.mapping')} v="—" /><Cell k={t('pf.push')} v="—" /></Cells>
    </div>
  );
}
