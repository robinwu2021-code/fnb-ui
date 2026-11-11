// 菜品管理（W1）：菜品 · 分类 · 规格 · 套餐 · 渠道同步
import { useStore } from '@/store';
import { CATS, CAT_ALL, CK, MODS, SETS, TAGS, type Dish } from '@/data/sample';
import { MENUS, SYNCWHY, type PlatKey } from '@/data/platforms';
import { channelsOf, livePlats, pauseLabel } from '@/lib/channels';
import { PlatLogo } from '@/components/ui/plat-logo';
import { Empty } from '@/components/ui/feedback';
import { useLang, useT } from '@/i18n';
import { Ico } from '@/lib/icon';
import { Rail } from '@/layout/rail';
import { Panel, Block, Acts } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { Search } from '@/components/ui/input';
import { Filter } from '@/components/ui/toggle';
import { Seg } from '@/components/ui/toggle-group';
import { Switch } from '@/components/ui/switch';
import { Chip, StatusIcon, StatusTag, CatMark } from '@/components/ui/badge';
import { Cells, Cell } from '@/components/ui/description-list';
import { Table, Tr, Td, Th, Nm2 } from '@/components/ui/table';
import { StatusTile } from '@/components/ui/status-tile';
import { Note } from '@/components/ui/feedback';
import { stGroup } from '@/lib/states';
import { cn } from '@/lib/cn';
import { cs } from '@/lib/money';

const useLeft = () => { const lines = useStore((s) => s.lines); return (d: Dish) => (d.s == null ? null : Math.max(0, d.s - lines.reduce((a, l) => (l.n === d.n && l.st !== 'void' ? a + l.q : a), 0))); };
function DishStock({ d }: { d: Dish }) {
  const t = useT(); const rest = useLeft()(d);
  return d.timed ? <StatusTag ent="stock" k="timed" label={d.timed} form="dot" /> : rest === 0 ? <StatusTag ent="stock" k="sold_out" form="dot" /> : rest != null && rest <= 5 ? <StatusTag ent="stock" k="low" label={t('order.stock.low', { n: rest })} form="dot" /> : <StatusTag ent="stock" k="in_stock" label={rest == null ? t('md.stock.unlimited') : t('md.stock.n', { n: rest })} form="dot" />;
}
export function MenuWB() {
  const t = useT(); const s = useStore(), dispatch = s.act;
  const pk = livePlats(s).map((p) => p.key); const failed = Object.keys(s.sync).filter((k) => pk.some((c) => s.sync[k][c] === 'failed')).length;
  const nav = [{ cap: t('md.nav.dishes') }, { v: '菜品', icon: 'soup', label: t('md.nav.dishes'), em: s.dishes.length }, { v: '分类', icon: 'list-tree', label: t('md.nav.cats'), em: CATS.length }, { v: '规格', icon: 'sliders-horizontal', label: t('md.nav.mods'), em: MODS.length }, { v: '套餐', icon: 'layers', label: t('md.nav.sets'), em: SETS.length },
    { cap: t('md.nav.channel') }, { v: '同步', icon: 'refresh-cw', label: t('md.nav.sync'), em: failed || undefined }];
  const Sub = ({ 菜品: Dishes, 分类: Cats, 规格: Mods, 套餐: Sets, 同步: Sync } as Record<string, () => JSX.Element>)[s.msub] || Dishes;
  return <><Rail items={nav} cur={s.msub} onPick={(v) => dispatch('msub:' + v)} ariaLabel={t('tab.menu')} /><Sub /></>;
}
function ChIcons({ n }: { n: string }) { const s = useStore(); const lang = useLang(); return <span className="flex gap-1">{channelsOf(s).map((c) => <span key={c.key} title={lang === 'en' ? c.en : c.name} className="inline-flex"><StatusIcon ent="sync" k={s.sync[n]?.[c.key] || 'pending'} size={15} /></span>)}</span>; }
const whyOf = (k: string, dish: string, lang: string) => { const w = SYNCWHY[k as PlatKey]?.[dish]; return w ? w[lang === 'en' ? 1 : 0] : ''; };
const ChName = ({ c }: { c: ReturnType<typeof channelsOf>[number] }) => { const lang = useLang(); return <span className="inline-flex items-center gap-1.5">{c.plat ? <PlatLogo k={c.key} size={18} /> : <Ico name={c.icon} size={16} />}{lang === 'en' ? c.en : c.name}</span>; };
function Dishes() {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act, left = useLeft();
  const list = s.dishes.filter((d) => { const r = left(d), f = s.mdf; return (s.mdc === '全部' || d.c === s.mdc) && (f === 'all' || (f === 'sold_out' ? r === 0 : f === 'timed' ? !!d.timed : f === 'low' ? r != null && r > 0 && r <= 5 : f === 'sync' ? ['failed', 'pending'].includes(s.sync[d.n].mt) || ['failed', 'pending'].includes(s.sync[d.n].elm) : true)); });
  const d = s.dishes[s.mdsel] || s.dishes[0], catOf = (c: string) => { const x = CAT_ALL.find((y) => y.key === c); return lang === 'en' && x ? x.en : c; };
  return (
    <>
      <Panel area="content" head={<>
        <div className="flex flex-wrap gap-gap items-center [&>label]:flex-[1_1_220px] [&>label]:w-auto"><Search placeholder={t('md.search')} /><Button variant="soft" size="sm" onClick={() => dispatch('mdnew')}><Ico name="plus" />{t('md.new')}</Button></div>
        <div className="flex flex-wrap gap-gap items-center">
          {([['all', 'md.f.all', ''], ['low', 'md.f.low', 'stock:low'], ['sold_out', 'md.f.sold', 'stock:sold_out'], ['timed', 'md.f.timed', 'stock:timed'], ['sync', 'md.f.sync', 'sync:failed']] as [string, string, string][]).map(([k, l, ic]) => <Filter key={k} pressed={s.mdf === k} onPressedChange={() => dispatch('mdf:' + k)}>{ic && <StatusIcon ent={ic.split(':')[0]} k={ic.split(':')[1]} size={15} />}{t(l)}</Filter>)}
          <Seg value={s.mdc} onChange={(v) => dispatch('mdc:' + v)} items={[{ v: '全部', label: t('md.f.all') }, ...CAT_ALL.map((c) => ({ v: c.key, label: lang === 'en' ? c.en : c.key }))]} />
        </div></>}>
        <Table cols="44px minmax(0,1.6fr) 72px 72px 112px 104px 52px" head={['', t('md.th.dish'), t('md.th.cat'), <span className="block text-right">{t('md.th.price')}</span>, t('md.th.sale'), t('md.th.ch'), <span className="block text-right">{t('md.th.on')}</span>]}>
          {list.map((x) => { const i = s.dishes.indexOf(x); return (
            <Tr key={x.n} on={s.mdsel === i} onClick={() => dispatch('mdsel:' + i)}>
              <Td><Th img={x.img} text={lang === 'en' ? x.en[0] : x.n[0]} k={CK[x.c] || 'gray'} /></Td>
              <Td><Nm2 a={<>{lang === 'en' ? x.en : x.n} {(x.tg || []).map((g) => <Ico key={g} name={TAGS[g]} size={13} className="text-sub align-[-2px]" />)}</>} b={lang === 'en' ? x.n : x.en} /></Td>
              <Td>{catOf(x.c)}</Td><Td r>{cs()}{x.p}{x.spec ? (lang === 'en' ? '+' : ' 起') : ''}</Td><Td><DishStock d={x} /></Td><Td><ChIcons n={x.n} /></Td><Td r><Switch checked={true} aria-label={t('md.th.on')} /></Td>
            </Tr>); })}
        </Table>
        <p className="help mt-3">{t('md.chHelp')}</p>
      </Panel>
      <Panel area="detail" head={<><div className="row-between"><b className="h-title">{lang === 'en' ? d.en : d.n}</b><DishStock d={d} /></div><span className="help">{lang === 'en' ? d.n : d.en} · {catOf(d.c)}</span></>}
        foot={<Acts><Button variant="muted" onClick={() => dispatch('toast:md.unlisted')}>{t('md.unlist')}</Button><Button block onClick={() => dispatch('mdsave')}>{t('md.save')}</Button></Acts>}>
        <div className="relative aspect-4/3 rounded-rt overflow-hidden bg-slot-bg grid place-items-center text-2xl font-700 mb-3" style={{ color: `var(--cat-${CK[d.c] || 'gray'}-fg)` }}>{d.img ? <img src={d.img} alt="" className="absolute inset-0 w-full h-full object-cover" /> : <span>{d.n.slice(0, 2)}</span>}<Chip onCard className="absolute right-2 bottom-2">{t('md.change')}</Chip></div>
        <Cells><Cell k={t('md.zh')} v={d.n} go /><Cell k={t('md.en')} v={d.en} go /><Cell k={t('md.th.cat')} v={catOf(d.c)} go /><Cell k={t('md.th.price')} v={cs() + d.p + (d.spec ? (lang === 'en' ? '+' : ' 起') : '')} go /><Cell k={t('md.memberPrice')} v={t('md.tbd')} /></Cells>
        <Block title={t('md.modsSec')} right={<Button variant="link" className="hit text-sm" onClick={() => dispatch('msub:规格')}>{t('md.manage')}</Button>}><div className="flex flex-wrap gap-gap">{(d.spec ? [0, 1, 2] : [3]).map((i) => <Chip key={i}>{lang === 'en' ? MODS[i][4] : MODS[i][0]}</Chip>)}</div></Block>
        <Block title={t('md.tags')}><div className="flex flex-wrap gap-gap">{Object.keys(TAGS).map((g) => <Filter key={g} pressed={(d.tg || []).includes(g)} onPressedChange={() => dispatch('mdtag:' + g)}><Ico name={TAGS[g]} size={15} />{g}</Filter>)}</div></Block>
        <Block title={t('md.sale')}><Cells><Cell k={t('md.todayStock')} v={d.s == null ? t('md.stock.unlimited') : d.s} go /><Cell k={t('md.timed')} v={d.timed || t('md.allDay')} go /><Cell k={t('md.soldOutK')}><Switch checked={left(d) === 0} onCheckedChange={() => dispatch('mdsold')} aria-label={t('md.soldOutK')} /></Cell></Cells></Block>
        <Block title={t('md.channels')} right={<Button variant="link" className="hit text-sm" onClick={() => dispatch('msub:同步')}>{t('md.goSync')}</Button>}>
          <Cells>{channelsOf(s).map((c) => { const st = s.sync[d.n]?.[c.key] || 'pending', why = st === 'failed' ? whyOf(c.key, d.n, lang) : ''; const mk = c.plat ? s.plats[c.plat.key].markup : 0; return <Cell key={c.key} k={<span className="flex flex-col"><ChName c={c} />{c.plat && <small className="help num pl-6">{t('sy.platPrice')} {cs()}{Math.round(d.p * (1 + mk / 100))}{mk ? ' · +' + mk + '%' : ''}</small>}</span>}><span className="text-right"><StatusTag ent="sync" k={st} form="dot" />{why && <><br /><small className="help">{why}</small></>}</span></Cell>; })}</Cells>
        </Block>
      </Panel>
    </>
  );
}
function Cats() {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act; const c = CAT_ALL[s.mcsel] || CAT_ALL[0];
  const printTo = (k: string) => t(({ 凉菜: 'md.st.cold', 饮品: 'md.st.bar' } as Record<string, string>)[k] || 'md.st.hot');
  return (
    <>
      <Panel area="content" head={<><div className="row-between"><b className="h-title">{t('md.cats')}</b><Button variant="soft" size="sm" onClick={() => dispatch('toast:md.catNew')}><Ico name="plus" />{t('md.newCat')}</Button></div><span className="help">{t('md.catHelp')}</span></>}>
        <Table cols="24px 24px minmax(0,1fr) minmax(0,1fr) 64px 120px 52px" head={['', '', t('md.zh'), t('md.th.en'), <span className="block text-right">{t('md.th.count')}</span>, t('md.th.platCat'), <span className="block text-right">{t('md.th.show')}</span>]}>
          {CAT_ALL.map((x, i) => <Tr key={x.key} on={s.mcsel === i} onClick={() => dispatch('mcsel:' + i)}><Td><Ico name="grip-vertical" size={16} className="text-placeholder" /></Td><Td><CatMark k={x.k} /></Td><Td>{x.key}</Td><Td>{x.en}</Td><Td r>{s.dishes.filter((d) => d.c === x.key).length}</Td><Td>{x.key === '主食' ? <StatusTag ent="sync" k="failed" label={t('md.unmapped')} form="dot" /> : <StatusTag ent="sync" k="synced" label={t('md.mapped')} form="dot" />}</Td><Td r><Switch checked aria-label={t('md.th.show')} /></Td></Tr>)}
        </Table>
      </Panel>
      <Panel area="detail" head={<><b className="h-title">{lang === 'en' ? c.en : c.key}</b><span className="help">{lang === 'en' ? c.key : c.en}</span></>} foot={<Acts><Button variant="danger" onClick={() => dispatch('toast:md.catHasDishes')}>{t('md.delete')}</Button><Button block onClick={() => dispatch('toast:md.catSaved')}>{t('md.save')}</Button></Acts>}>
        <Cells><Cell k={t('md.zh')} v={c.key} go /><Cell k={t('md.en')} v={c.en} go /><Cell k={t('md.printTo')} v={printTo(c.key)} go /><Cell k={t('md.mtCat')} v={c.key === '主食' ? t('md.unmapped') : t('md.cn', { c: lang === 'en' ? c.en : c.key })} go /><Cell k={t('md.elmCat')} v={t('md.cn', { c: lang === 'en' ? c.en : c.key })} go /></Cells>
        <span className="gt mt-2"><span>{t('md.catColor')}</span></span>
        <div className="flex flex-wrap gap-gap mt-1.5">{['red', 'orange', 'yellow', 'green', 'teal', 'blue', 'purple', 'brown'].map((k) => <button key={k} type="button" aria-label={k} aria-pressed={k === c.k} className={cn('hit w-32px h-32px rounded-[min(var(--rb),16px)]', k === c.k && 'shadow-[0_0_0_2px_var(--c-surface),0_0_0_4px_var(--c-ink)]')} style={{ background: `var(--cat-${k}-solid)` }} />)}</div>
      </Panel>
    </>
  );
}
function Mods() {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act; const m = MODS[s.mmsel] || MODS[0];
  return (
    <>
      <Panel area="content" head={<><div className="row-between"><b className="h-title">{t('md.mods')}</b><Button variant="soft" size="sm" onClick={() => dispatch('toast:md.modNew')}><Ico name="plus" />{t('md.newMod')}</Button></div><span className="help">{t('md.modHelp')}</span></>}>
        <Table cols="minmax(0,1fr) 120px minmax(0,2fr) 72px" head={[t('md.th.group'), t('md.th.rule'), t('md.th.opts'), <span className="block text-right">{t('md.th.count')}</span>]}>
          {MODS.map((x, i) => <Tr key={x[0]} on={s.mmsel === i} onClick={() => dispatch('mmsel:' + i)}><Td><b>{lang === 'en' ? x[4] : x[0]}</b></Td><Td className="help">{lang === 'en' ? x[5] : x[1]}</Td><Td><span className="flex flex-wrap gap-1">{x[2].map((o) => <Chip key={o}>{o}</Chip>)}</span></Td><Td r>{t('md.nDishes', { n: x[3] })}</Td></Tr>)}
        </Table>
      </Panel>
      <Panel area="detail" head={<><b className="h-title">{lang === 'en' ? m[4] : m[0]}</b><span className="help">{lang === 'en' ? m[5] : m[1]} · {t('md.usedIn', { n: m[3] })}</span></>} foot={<Button block onClick={() => dispatch('toast:md.modSaved')}>{t('md.save')}</Button>}>
        <span className="gt"><span>{t('md.optPrice')}</span></span>
        <Cells>{m[2].map((o) => { const p = o.split(' '); return <Cell key={o} k={<span className="inline-flex items-center gap-1.5 text-ink"><Ico name="grip-vertical" size={16} className="text-placeholder" />{p[0]}</span>} v={p[1] || t('md.noExtra')} go />; })}</Cells>
        <div className="mt-2"><Button variant="muted" size="sm" onClick={() => dispatch('toast:md.optAdded')}><Ico name="plus" />{t('md.addOpt')}</Button></div>
        <span className="gt mt-4"><span>{t('md.rules')}</span></span>
        <Cells><Cell k={t('md.required')}><Switch checked={/必选/.test(m[1])} aria-label={t('md.required')} /></Cell><Cell k={t('md.multi')}><Switch checked={/多选/.test(m[1])} aria-label={t('md.multi')} /></Cell><Cell k={t('md.syncPlat')}><Switch checked aria-label={t('md.syncPlat')} /></Cell></Cells>
      </Panel>
    </>
  );
}
function Sets() {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act; const x = SETS[s.mssel] || SETS[0];
  return (
    <>
      <Panel area="content" head={<><div className="row-between"><b className="h-title">{t('md.sets')}</b><Button variant="soft" size="sm" onClick={() => dispatch('toast:md.setNew')}><Ico name="plus" />{t('md.newSet')}</Button></div><span className="help">{t('md.setHelp')}</span></>}>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-gap content-start">
          {SETS.map((y, i) => <StatusTile key={y.n} group="progress" current={s.mssel === i} onClick={() => dispatch('mssel:' + i)} className="py-3 px-3.5 gap-0.5 min-h-124px"><span className="flex items-center justify-between gap-2 mb-0.5"><b className="text-lg font-700">{lang === 'en' ? y.en : y.n}</b><StatusTag ent="stock" k="in_stock" label={t('md.onSale')} form="dot" /></span><span className="help ellipsis">{y.groups.map((g) => g[0]).join(' · ')}</span><span className="help">{y.swap ? t('md.swap') : t('md.noSwap')}</span><span className="a mt-auto pt-1.5 text-xl font-700 num">{cs()}{y.p}</span></StatusTile>)}
        </div>
      </Panel>
      <Panel area="detail" head={<><div className="row-between"><b className="h-title">{lang === 'en' ? x.en : x.n}</b><b className="num">{cs()}{x.p}</b></div><span className="help">{lang === 'en' ? x.n : x.en}</span></>} foot={<Acts><Button variant="muted" onClick={() => dispatch('toast:md.groupAdded')}><Ico name="plus" />{t('md.addGroup')}</Button><Button block onClick={() => dispatch('toast:md.setSaved')}>{t('md.save')}</Button></Acts>}>
        {x.groups.map((g) => <Block key={g[0]} title={g[0]} right={<Button variant="link" className="hit text-sm" onClick={() => dispatch('toast:md.groupEdit')}>{t('md.edit')}</Button>}><div className="flex flex-wrap gap-gap">{g[1].map((n) => <Chip key={n}>{n}</Chip>)}</div></Block>)}
        <Cells className="mt-4"><Cell k={t('md.allowSwap')}><Switch checked={x.swap} aria-label={t('md.allowSwap')} /></Cell><Cell k={t('md.swapFee')}><Switch checked={x.swap} aria-label={t('md.swapFee')} /></Cell><Cell k={t('md.channels')} v={t('md.setCh')} /></Cells>
        <Note className="mt-2">{t('md.setNote')}</Note>
      </Panel>
    </>
  );
}
function Sync() {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const plats = livePlats(s), chs = channelsOf(s);
  if (!plats.length) return <>
    <Panel area="content" bodyClass="flex flex-col"><Empty fill icon={<Ico name="bike" size={40} className="text-sub" />} title={t('sy.noPlat')} desc={t('sy.noPlatDesc')} /><div className="flex justify-center pb-6"><Button onClick={() => dispatch('plat:page')}>{t('sy.goConnect')}</Button></div></Panel>
    <Panel area="detail" head={<b className="h-title">{t('md.syncLog')}</b>}><Note>{t('md.syncNote')}</Note></Panel></>;
  const cnt = (ch: string, st: string) => Object.keys(s.sync).filter((k) => s.sync[k][ch] === st).length;
  const issues = Object.keys(s.sync).filter((k) => plats.some((p) => ['failed', 'pending'].includes(s.sync[k][p.key])));
  const log: [string, string, string, string][] = [...plats.map((p) => { const f = cnt(p.key, 'failed'); return [s.plats[p.key].lastSync || '—', p.name, f ? t('md.logPush', { a: cnt(p.key, 'synced') + f, b: f }) : t('md.logOk', { a: cnt(p.key, 'synced') }), f ? 'failed' : 'synced'] as [string, string, string, string]; }), ['09:02', lang === 'en' ? 'QR ordering' : '扫码点餐', t('md.logOk', { a: 15 }), 'synced'], [t('md.yesterday'), plats[0].name, t('md.log3'), 'unlisted']];
  const ph = (p: (typeof plats)[number]) => <span className="inline-flex items-center gap-1.5"><PlatLogo k={p.key} size={18} />{p.name}</span>;
  return (
    <>
      <Panel area="content" head={<>
        <div className="row-between"><b className="h-title">{t('md.sync')}</b><span className="flex items-center gap-gap"><Seg value={s.syncObj} onChange={(v) => s.set({ syncObj: v })} items={(['dish', 'menu', 'store'] as const).map((v) => ({ v, label: t('sy.obj.' + v) }))} /><Button size="sm" disabled={s.syncing} onClick={() => dispatch('syncall')}><Ico name="refresh-cw" />{s.syncing ? t('md.syncing') : t('md.syncAll')}</Button></span></div>
        <span className="help">{t('md.syncHelp')}</span></>}>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-gap content-start">
          {chs.slice(1).map((c) => { const f = cnt(c.key, 'failed'), p = cnt(c.key, 'pending'), ok = cnt(c.key, 'synced'); const ps = c.plat ? s.plats[c.plat.key] : null; return (
            <StatusTile key={c.key} group={f ? 'failed' : p ? 'waiting' : 'done'} alert={f > 0} className="py-3 px-3.5 gap-0.5 min-h-124px">
              <span className="flex items-center justify-between gap-2 mb-0.5"><b className="text-lg font-700 inline-flex items-center gap-2">{c.plat ? <PlatLogo k={c.key} size={22} /> : <Ico name={c.icon} size={18} />}{lang === 'en' ? c.en : c.name}</b>
                {ps?.st === 'paused' ? <StatusTag ent="campaign" k="paused" label={t('pf.st.paused')} form="dot" /> : <StatusTag ent="device" k="online" label={t('md.connected')} form="dot" />}</span>
              <span className="help">{t('md.lastSync', { t: ps?.lastSync || (s.synced ? t('md.justNow') : '09:02') })}{c.plat ? ' · ' + t('pf.mode.' + c.plat.menuMode) : ''}</span>
              <span className="flex flex-wrap gap-2 mt-auto pt-1.5"><StatusTag ent="sync" k="synced" label={t('md.syncedN', { n: ok })} form="dot" /><StatusTag ent="sync" k="pending" label={t('md.pendingN', { n: p })} form="dot" />{f > 0 && <StatusTag ent="sync" k="failed" label={t('md.failedN', { n: f })} form="dot" />}</span>
            </StatusTile>); })}
        </div>
        {s.syncObj === 'dish' && <Block title={t('md.issues', { n: issues.length })} right={<span>{t('md.fixFirst')}</span>} className="mt-4">
          <Table cols={`minmax(0,1fr) ${plats.map(() => '104px').join(' ')} minmax(0,1.4fr) 88px`} head={[t('md.th.dish'), ...plats.map(ph), t('md.th.reason'), '']}>
            {issues.map((k) => { const d = s.dishes.find((y) => y.n === k)!; const why = plats.map((p) => (s.sync[k][p.key] === 'failed' ? whyOf(p.key, k, lang) : '')).find(Boolean);
              return <Tr key={k}><Td><b>{lang === 'en' ? d.en : k}</b></Td>{plats.map((p) => <Td key={p.key}><StatusTag ent="sync" k={s.sync[k][p.key] || 'pending'} form="dot" /></Td>)}<Td className="help">{why || t('md.changed')}</Td><Td r><Button variant="muted" size="sm" onClick={() => dispatch(why ? 'mdgo:' + s.dishes.indexOf(d) : 'sync1:' + k)}>{why ? t('md.goFix') : t('md.sync1')}</Button></Td></Tr>; })}
          </Table>
        </Block>}
        {s.syncObj === 'menu' && <Block title={t('sy.menus')} right={<span>{t('sy.menusHint')}</span>} className="mt-4">
          <Table cols="minmax(0,1fr) minmax(0,1fr) 112px 108px 88px 80px" head={[t('sy.th.plat'), t('sy.th.menu'), t('sy.th.hours'), t('sy.th.mode'), t('sy.th.lastPush'), '']}>
            {plats.flatMap((p) => MENUS.map((m) => { const at = s.plats[p.key].menuPush[m[0]]; return <Tr key={p.key + m[0]}><Td>{ph(p)}</Td><Td>{lang === 'en' ? m[2] : m[1]}</Td><Td className="num">{m[3]}</Td><Td className="help">{t('pf.mode.' + p.menuMode)}</Td><Td>{at ? <span className="num">{at}</span> : <StatusTag ent="sync" k="pending" label={t('sy.never')} form="dot" />}</Td><Td r><Button variant="muted" size="sm" onClick={() => dispatch('plat:push:' + p.key + ':' + m[0])}>{t('sy.push')}</Button></Td></Tr>; }))}
          </Table>
        </Block>}
        {s.syncObj === 'store' && <Block title={t('sy.storeTitle')} right={<span>{t('sy.storeHint')}</span>} className="mt-4">
          <Table cols={`minmax(0,1fr) 120px ${plats.map(() => 'minmax(0,1fr)').join(' ')}`} head={[t('sy.th.item'), t('sy.local'), ...plats.map(ph)]}>
            {(['status', 'hours', 'prep'] as const).map((r) => { const local = r === 'status' ? t('sy.open') : r === 'hours' ? '10:00–22:00' : t('pf.min', { n: 15 });
              return <Tr key={r}><Td>{t('sy.store.' + r)}</Td><Td className="num">{local}</Td>{plats.map((p) => { const ps = s.plats[p.key]; const v = r === 'status' ? (ps.st === 'paused' ? pauseLabel(ps, t) : t('sy.accepting')) : r === 'hours' ? ps.hours || '10:00–22:00' : t('pf.min', { n: ps.prep }); const diff = r === 'status' ? ps.st === 'paused' : r === 'hours' ? !!ps.hours && ps.hours !== '10:00–22:00' : ps.prep !== 15;
                return <Td key={p.key}>{diff ? <StatusTag ent="sync" k="pending" label={v} form="dot" /> : <span className="num">{v}</span>}</Td>; })}</Tr>; })}
          </Table>
          <Note className="mt-3">{t('sy.storeNote')}</Note>
        </Block>}
      </Panel>
      <Panel area="detail" head={<b className="h-title">{t('md.syncLog')}</b>}>
        <Cells>{log.map((r, i) => <Cell key={i} k={<span>{r[0]}<br /><small className="text-xs">{r[1]} · {r[2]}</small></span>}><StatusTag ent="sync" k={r[3]} form="dot" /></Cell>)}</Cells>
        <Note className="mt-3">{t('md.syncNote')}</Note>
      </Panel>
    </>
  );
}
export { stGroup };
