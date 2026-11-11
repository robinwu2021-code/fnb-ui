// 设置（W4）：左一列菜单（「管理」进会员 / 菜品 / 班次 + 各组设置项）· 右设置页；「界面与显示」= StyleControls
import { useStore } from '@/store';
import { CATS, CAT_ALL, CK, QUICK, type SetGroup, type SetItem } from '@/data/sample';
import { useBizDef, goModule } from '@/biz';
import { useLang, useT } from '@/i18n';
import { Ico } from '@/lib/icon';
import { Rail, type RailItem } from '@/layout/rail';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Filter } from '@/components/ui/toggle';
import { Chip, StatusTag } from '@/components/ui/badge';
import { Cells, Cell } from '@/components/ui/description-list';
import { Table, Tr, Td, Th, Nm2 } from '@/components/ui/table';
import { Note } from '@/components/ui/feedback';
import { Stat } from '@/components/ui/panel';
import { StyleControls } from '@/dev/StyleControls';
import { DevicesPage } from './DevicesPage';
import { PlatformsPage } from './PlatformsPage';
import { beginEdit } from '@/modules/tables/floor-ops';
import { REGIONS } from '@/data/platforms';
import { Seg } from '@/components/ui/toggle-group';
import type { ReactNode } from 'react';

const Val = ({ v, label }: { v: string; label: string }) => (v === 'on' || v === 'off' ? <Switch checked={v === 'on'} aria-label={label} /> : <span className={v === '›' ? '' : 'after:content-["_›"] after:text-sub'}>{v === '›' ? '›' : v}</span>);
const Bi = ({ zh, en }: { zh: ReactNode; en: ReactNode }) => { const lang = useLang(); const names = useStore((s) => s.names); return <span className="inline-flex flex-col leading-[1.25]"><b className="font-inherit">{lang === 'en' ? en : zh}</b>{names === 'bi' && <small className="text-xs text-sub font-400">{lang === 'en' ? zh : en}</small>}</span>; };
export function SettingsWB() {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const MENU = useBizDef().menu; /* 设置菜单按业态（biz/index.ts） */
  const grp = MENU.find((g) => g[0] === s.sg) || MENU[0]; const item = grp[3].find((x) => x[0] === s.si) || grp[3][0];
  const items: RailItem[] = [{ cap: t('set.manage') }, ...useBizDef().tabs.filter((x) => x.home).map((x) => ({ v: 'wb.' + x.v, label: t(x.label), em: '›' }))];
  MENU.forEach((g) => { items.push({ cap: lang === 'en' ? g[2] : g[1] }); g[3].forEach((y) => items.push({ v: g[0] + '.' + y[0], label: lang === 'en' ? y[2] : y[1], second: s.names === 'bi' ? (lang === 'en' ? y[1] : y[2]) : undefined })); });
  return (
    <>
      <Rail items={items} cur={grp[0] + '.' + item[0]} onPick={(v) => (v.startsWith('wb.') ? goModule(v.slice(3)) : dispatch('snav:' + v))} ariaLabel={t('tab.settings')} />
      <section className="bg-surface rounded-rc flex flex-col min-h-0 min-w-0 overflow-hidden" style={{ gridArea: 'content' }}>
        <div className="px-[calc(var(--pad)+4px)] pt-pad flex items-end justify-between gap-3 flex-none"><div><b className="block text-lg font-700">{lang === 'en' ? item[2] : item[1]}</b><small className="text-sub text-sm">{lang === 'en' ? grp[2] + ' › ' + item[2] : grp[1] + ' › ' + item[1]}</small></div>{item[0] === 'areas' ? <Button size="sm" onClick={() => beginEdit()}><Ico name="pencil-ruler" size={16} />{t('fl.edit')}</Button> : item[0] === 'platforms' ? <Button size="sm" onClick={() => dispatch('plat:add')}><Ico name="plus" size={16} />{t('pf.add')}</Button> : item[0] === 'devices' || item[0] === 'printers' ? <Button size="sm" onClick={() => dispatch(item[0] === 'printers' ? 'dev:add:printer' : 'dev:add')}><Ico name="plus" size={16} />{item[0] === 'printers' ? t('set.addPrinter') : t('dv.add')}</Button> : item[0] !== 'shape' && <Button size="sm" onClick={() => dispatch('toast:set.saved')}>{t('set.save')}</Button>}</div>
        <div className="flex-1 min-h-0 overflow-y-auto p-pad px-[calc(var(--pad)+4px)] flex flex-col gap-4"><Page grp={grp} item={item} /></div>
      </section>
    </>
  );
}
function Page({ grp, item }: { grp: SetGroup; item: SetItem }) {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act; const k = item[0];
  const left = (d: { n: string; s: number | null }) => (d.s == null ? null : Math.max(0, d.s - s.lines.reduce((a, l) => (l.n === d.n && l.st !== 'void' ? a + l.q : a), 0)));
  if (k === 'shape') return (
    <>
      <span className="gt"><span>{t('set.quick')}</span></span>
      <div className="flex flex-wrap gap-gap">{Object.keys(QUICK).map((q) => <Filter key={q} pressed={s.quick.includes(q)} onPressedChange={() => dispatch('qk:' + q)}><Ico name={QUICK[q][1]} size={15} />{t(QUICK[q][0])}</Filter>)}</div>
      <StyleControls />
    </>
  );
  if (k === 'overview') return <><Stat items={[{ n: '¥8,642', l: t('set.ov.revenue') }, { n: '46', l: t('set.ov.orders') }, { n: '¥187.9', l: t('set.ov.avg') }, { n: '1.6', l: t('set.ov.turns') }]} /><Cells><Cell k={t('set.ov.dining')} v={t('rp.nTables', { n: 8 })} num /><Cell k={t('set.ov.due')} v={t('rp.nTables', { n: 2 })} num /><Cell k={t('set.ov.delivery')} v={t('sheet.net.orders', { n: 2 })} num /><Cell k={t('set.ov.pending')} v={3} num go /></Cells></>;
  if (k === 'devices') return <DevicesPage />;
  if (k === 'platforms') return <PlatformsPage />;
  if (k === 'info') return <Cells>{(item[3] || []).map((r) => <Cell key={r[0]} k={<Bi zh={r[0]} en={r[1]} />} strong><Val v={r[2]} label={r[0]} /></Cell>)}<Cell k={<Bi zh="地区" en="Region" />} strong><span className="flex items-center gap-2"><span className="help">{t('pf.regionStoreHint')}</span><Seg value={s.region} onChange={(v) => dispatch('plat:region:' + v)} items={REGIONS.map((r) => ({ v: r, label: t('pf.region.' + r) }))} /></span></Cell></Cells>;
  if (k === 'printers') return <DevicesPage kind="printer" />;
  if (k === 'routing') return <Table cols="minmax(0,1fr) minmax(0,1fr) 72px" head={[t('set.th.cat'), t('set.th.printTo'), <span className="block text-right">{t('set.th.copies')}</span>]}>{CATS.slice(1).map((c) => <Tr key={c.key}><Td><Bi zh={c.key} en={c.en} /></Td><Td className="after:content-['_›'] after:text-sub">{t(({ 凉菜: 'md.st.cold', 饮品: 'md.st.bar' } as Record<string, string>)[c.key] || 'md.st.hot')}</Td><Td r>1</Td></Tr>)}</Table>;
  if (k === 'methods') { const rows: [string, string, string][] = [[t('pay.wx'), t('set.pm.wx'), 'on'], [t('pay.ali'), t('set.pm.wx'), 'on'], [t('pay.cash'), t('set.pm.cash'), 'on'], [t('pay.card'), t('set.pm.card'), 'on'], [t('sh.sv'), t('set.pm.sv'), 'on'], [t('pay.mix'), t('set.pm.mix'), 'on'], [t('set.pm.credit'), t('set.pm.creditD'), 'off']];
    return <Cells>{rows.map((r) => <Cell key={r[0]} k={<span className="text-ink">{r[0]} <span className="help">{r[1]}</span></span>}><Val v={r[2]} label={r[0]} /></Cell>)}</Cells>; }
  if (k === 'people') { const rows: [string, string][] = [['李敏', 'cashier'], ['王芳', 'server'], ['张伟', 'manager'], ['刘洋', 'chef'], ['陈静', 'cashier']];
    return <><Table cols="minmax(0,1fr) minmax(0,1fr) minmax(0,1fr) 64px" head={[t('set.th.staffName'), t('set.th.role'), t('set.th.phone'), <span className="block text-right">{t('set.th.active')}</span>]}>{rows.map((r, i) => <Tr key={r[0]}><Td>{r[0]}</Td><Td><Chip>{t('set.role.' + r[1])}</Chip></Td><Td className="num">138****{6621 + i * 7}</Td><Td r><Val v="on" label={t('set.th.active')} /></Td></Tr>)}</Table><div><Button variant="soft">{t('set.addStaff')}</Button></div></>; }
  if (k === 'roles') { const roles = ['manager', 'cashier', 'server', 'chef', 'finance']; const rows: [string, ...string[]][] = [['order', 'on', 'on', 'on', 'off', 'off'], ['pay', 'on', 'on', 'off', 'off', 'off'], ['comp', 'on', 'off', 'off', 'off', 'off'], ['disc', 'on', 'off', 'off', 'off', 'off'], ['sold', 'on', 'on', 'off', 'on', 'off'], ['reports', 'on', 'off', 'off', 'off', 'on'], ['settings', 'on', 'off', 'off', 'off', 'off']];
    return <><Table cols="minmax(0,1fr) repeat(5,64px)" head={[t('set.th.perm'), ...roles.map((r) => <span className="block text-right">{t('set.role.' + r)}</span>)]}>{rows.map((r) => <Tr key={r[0]}><Td>{t('set.perm.' + r[0])}</Td>{r.slice(1).map((v, i) => <Td key={i} r><Val v={v} label={t('set.perm.' + r[0])} /></Td>)}</Tr>)}</Table><Note>{t('set.roleNote')}</Note></>; }
  if (k === 'items' || k === 'stock') return <Table cols="44px minmax(0,1.6fr) minmax(0,1fr) 72px 72px 52px" head={['', t('md.th.dish'), t('md.th.cat'), <span className="block text-right">{t('md.th.price')}</span>, <span className="block text-right">{t('md.todayStock')}</span>, <span className="block text-right">{t('md.th.on')}</span>]}>{s.dishes.map((d) => { const c = CAT_ALL.find((x) => x.key === d.c) || CAT_ALL[CAT_ALL.length - 1]; return <Tr key={d.n}><Td><Th img={d.img} text={lang === 'en' ? d.en[0] : d.n[0]} k={CK[d.c] || 'gray'} /></Td><Td><Nm2 a={lang === 'en' ? d.en : d.n} b={lang === 'en' ? d.n : d.en} /></Td><Td><Bi zh={c.key} en={c.en} /></Td><Td r>¥{d.p}</Td><Td r>{d.s == null ? t('md.stock.unlimited') : d.s}</Td><Td r><Val v={left(d) === 0 ? 'off' : 'on'} label={t('md.th.on')} /></Td></Tr>; })}</Table>;
  const rows = item[3] || [];
  return <Cells>{rows.map((r) => <Cell key={r[0]} k={<Bi zh={r[0]} en={r[1]} />} strong><Val v={r[2]} label={r[0]} /></Cell>)}</Cells>;
}
export { grpName };
function grpName(g: SetGroup, lang: string) { return lang === 'en' ? g[2] : g[1]; }
