// 会员与营销（W1）：会员 · 标签 · 储值 · 活动 · 优惠券 · 积分
import { matchLayer } from '@/domain/member';
import { useStore } from '@/store';
import { CPNT, LAYER_EN, LAYERS, RECH, type Member } from '@/data/sample';
import { useLang, useT } from '@/i18n';
import { Ico } from '@/lib/icon';
import { stGroup, stName } from '@/lib/states';
import { Rail } from '@/layout/rail';
import { Panel, Block, Acts } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { Search } from '@/components/ui/input';
import { Filter, FilterCount } from '@/components/ui/toggle';
import { Seg } from '@/components/ui/toggle-group';
import { Switch } from '@/components/ui/switch';
import { Chip, StatusIcon, StatusTag } from '@/components/ui/badge';
import { Cells, Cell } from '@/components/ui/description-list';
import { Table, Tr, Td } from '@/components/ui/table';
import { StatusTile } from '@/components/ui/status-tile';
import { Note, Empty } from '@/components/ui/feedback';
import { CouponCard } from '@/components/ui/coupon';
import { cn } from '@/lib/cn';
import { useState } from 'react';
import { cs, cur, money } from '@/lib/money';

const useName = () => { const t = useT(); return (m: Member) => (m[9] ? t('mb.leadName', { n: m[0] }) : m[0]); };
const useLayer = () => { const lang = useLang(); return (l: string) => (lang === 'en' ? LAYER_EN[l] || l : l); };
export function MembersWB() {
  const t = useT(); const s = useStore(), dispatch = s.act;
  const nav = [{ cap: t('mb.nav.members') }, { v: '会员', icon: 'users', label: t('mb.nav.members'), em: s.members.length }, { v: '标签', icon: 'tags', label: t('mb.nav.tags'), em: s.mtags.length }, { cap: t('mb.nav.marketing') }, { v: '储值', icon: 'wallet', label: t('mb.nav.stored') }, { v: '活动', icon: 'megaphone', label: t('mb.nav.camps'), em: s.camps.filter((c) => c[5] === 'running').length }, { v: '优惠券', icon: 'ticket', label: t('mb.nav.coupons') }, { v: '积分', icon: 'coins', label: t('mb.nav.points') }];
  const Sub = ({ 会员: List, 标签: Tags, 储值: Stored, 活动: Camps, 优惠券: Coupons, 积分: Points } as Record<string, () => JSX.Element>)[s.msec] || List;
  return <><Rail items={nav} cur={s.msec} onPick={(v) => dispatch('msec:' + v)} ariaLabel={t('tab.members')} /><Sub /></>;
}
function MCard({ bal, spent, visits }: { bal: number; spent: number; visits: number }) {
  const t = useT();
  return <div className="bg-wash rounded-rt py-2.5 px-3 grid grid-cols-3 gap-2"><span><small className="block text-xs text-sub">{t('sheet.member.bal')}</small><b className="num">{money(bal)}</b></span><span><small className="block text-xs text-sub">{t('mb.spent')}</small><b className="num">{cs()}{spent.toLocaleString()}</b></span><span><small className="block text-xs text-sub">{t('mb.visits')}</small><b className="num">{t('mb.visitsN', { n: visits })}</b></span></div>;
}
function List() {
  const t = useT(); const s = useStore(), dispatch = s.act, name = useName(), layer = useLayer(); const [seg, setSeg] = useState('all');
  const match = (m: Member, l: string) => matchLayer(l, m[2], !!m[9]); /* 规则在 domain/member */
  const list = s.members.filter((m) => match(m, s.mlayer)); const m = s.members[s.msel] || s.members[0];
  const ledger: [string, string, string, string][] = [['10-23 12:02', t('mb.l1'), cur('+¥330.00'), cur('¥520.00')], ['10-20 19:14', t('mb.l2'), cur('−¥186.00'), cur('¥190.00')], ['10-12 18:40', t('mb.l3'), cur('+¥200.00'), cur('¥376.00')]];
  return (
    <>
      <Panel area="content" head={<>
        <div className="flex flex-wrap gap-gap items-center [&>label]:flex-[1_1_220px] [&>label]:w-auto"><Search placeholder={t('mb.search')} /><Button variant="soft" size="sm" onClick={() => dispatch('ov:mnew')}><Ico name="user-plus" />{t('mb.new')}</Button></div>
        <div className="flex flex-wrap gap-gap">{['全部', ...LAYERS, '线索'].map((l) => <Filter key={l} pressed={s.mlayer === l} onPressedChange={() => dispatch('mlayer:' + l)}>{layer(l)}<FilterCount n={s.members.filter((x) => match(x, l)).length} /></Filter>)}</div></>}>
        <Table cols="minmax(0,1.2fr) 72px 64px minmax(0,1.4fr) 88px 72px 80px" head={[t('mb.th.member'), t('mb.th.phone'), t('mb.th.layer'), t('mb.th.tags'), <span className="block text-right">{t('mb.th.bal')}</span>, <span className="block text-right">{t('mb.th.visits')}</span>, <span className="block text-right">{t('mb.th.last')}</span>]}>
          {list.map((x) => { const i = s.members.indexOf(x); return <Tr key={i} on={s.msel === i} onClick={() => dispatch('msel:' + i)}><Td><b>{name(x)}</b></Td><Td className="num">****{x[1]}</Td><Td>{x[9] ? <StatusTag ent="member" k="lead" label={t('mb.lead')} form="dot" /> : <Chip>{layer(x[2])}</Chip>}</Td><Td><span className="flex flex-wrap gap-1">{x[3].map((g) => <Chip key={g}>{g}</Chip>)}</span></Td><Td r>{cs()}{x[4]}</Td><Td r>{t('mb.visitsN', { n: x[5] })}</Td><Td r>{x[7]}</Td></Tr>; })}
        </Table>
        <p className="help mt-3">{t('mb.listHelp')}</p>
      </Panel>
      <Panel area="detail" head={<><div className="row-between"><b className="h-title">{name(m)}</b>{m[9] ? <StatusTag ent="member" k="lead" label={t('mb.leadTag')} /> : <Chip>{layer(m[2])}</Chip>}</div><span className="help">{t('mb.meta', { p: m[1], s: m[8] })}</span></>}
        foot={<Acts><Button variant="muted" onClick={() => dispatch('ov:mnew')}>{t('mb.editProfile')}</Button><Button block onClick={() => dispatch('ov:recharge')}>{t('mb.recharge')}</Button></Acts>}>
        {m[9] ? <Note tone="warn" className="mb-3">{t('mb.leadNote')}</Note> : null}
        <MCard bal={m[4]} spent={m[6]} visits={m[5]} />
        <Block title={t('mb.tagsMax')} right={<Button variant="link" className="hit text-sm" onClick={() => dispatch('ov:mtag')}>{t('md.edit')}</Button>} className="mt-4"><div className="flex flex-wrap gap-gap">{m[3].length ? m[3].map((g) => <Chip key={g}>{g}</Chip>) : <span className="help">{t('mb.noTags')}</span>}</div></Block>
        <Block title={t('mb.ledger')} right={<Seg value={seg} onChange={setSeg} items={[{ v: 'all', label: t('mb.all') }, { v: 'sv', label: t('mb.topup') }, { v: 'use', label: t('mb.redeemK') }]} />}>
          <Cells>{ledger.map((r, i) => <Cell key={i} k={<span>{r[0]}<br /><small className="text-xs">{r[1]}</small></span>}><span className="num text-right">{r[2]}<br /><small className="help">{t('mb.balAfter', { m: r[3] })}</small></span></Cell>)}</Cells>
        </Block>
      </Panel>
    </>
  );
}
function Tags() {
  const t = useT(); const s = useStore(), dispatch = s.act, name = useName(), layer = useLayer(); const tg = s.mtags[s.mtsel] || s.mtags[0];
  return (
    <>
      <Panel area="content" head={<><div className="row-between"><b className="h-title">{t('mb.tags')}</b><Button variant="soft" size="sm" onClick={() => dispatch('ov:mtagnew')}><Ico name="plus" />{t('mb.newTag')}</Button></div><span className="help">{t('mb.tagHelp')}</span></>}>
        <Block title={t('mb.customTags', { n: s.mtags.length })}><Cells>{s.mtags.map((x, i) => <button key={x[0]} type="button" className={cn('w-full text-left rounded-rk px-2 -mx-2 hover:bg-wash', s.mtsel === i && 'bg-wash-strong')} onClick={() => dispatch('mtsel:' + i)}><Cell k={<span className="inline-flex items-center gap-1.5 text-ink"><Ico name="tag" size={16} /><b>{x[0]}</b></span>} v={t('mb.people', { n: x[1] })} /></button>)}</Cells></Block>
        <Block title={t('mb.sysLayers')}><Cells>{LAYERS.map((l, i) => <Cell key={l} k={layer(l)} v={t('mb.ly' + (i + 1))} />)}</Cells></Block>
      </Panel>
      <Panel area="detail" head={<><b className="h-title">{tg[0]}</b><span className="help">{t('mb.tagMembers', { n: tg[1] })}</span></>} foot={<Acts><Button variant="muted" onClick={() => dispatch('toast:mb.renamed')}>{t('mb.rename')}</Button><Button variant="danger" onClick={() => dispatch('toast:mb.tagDeleted')}>{t('md.delete')}</Button></Acts>}>
        <Cells>{s.members.filter((m) => m[3].includes(tg[0])).map((m, i) => <Cell key={i} k={name(m)} v={'****' + m[1]} num />)}</Cells>
        <Note className="mt-3">{t('mb.tagNote')}</Note>
      </Panel>
    </>
  );
}
function Stored() {
  const t = useT(); const s = useStore(), dispatch = s.act;
  return (
    <>
      <Panel area="content" head={<><div className="row-between"><b className="h-title">{t('mb.stored')}</b><Button variant="soft" size="sm" onClick={() => dispatch('ov:plannew')}><Ico name="plus" />{t('mb.newPlan')}</Button></div><span className="help">{t('mb.storedHelp')}</span></>}>
        <Block title={t('mb.plans')}>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(180px,1fr))] gap-gap">{s.plans.map((p, i) => <div key={p.n} className={cn('flex flex-col gap-1 py-3 px-3.5 rounded-rt bg-wash', !p.on && 'opacity-60')}><b className="text-md">{p.n}</b><span className="help">{t('mb.planLine', { p: p.pay, g: p.gift, t: p.pay + p.gift })}</span><span className="row-between"><StatusTag ent="campaign" k={p.on ? 'running' : 'paused'} label={p.on ? t('mb.enabled') : t('mb.disabled')} form="dot" /><Switch checked={p.on} onCheckedChange={() => dispatch('plan:' + i)} aria-label={t('mb.enable')} /></span></div>)}</div>
        </Block>
        <Block title={t('mb.todayRech')} right={<span>{t('mb.rechHelp')}</span>}>
          <Table cols="64px minmax(0,1fr) minmax(0,1.2fr) 80px 120px 56px" head={[t('mb.th.no'), t('mb.th.member'), t('mb.th.plan'), <span className="block text-right">{t('mb.th.paid')}</span>, t('mb.th.status'), <span className="block text-right">{t('mb.th.time')}</span>]}>
            {RECH.map((r) => <Tr key={r[0]}><Td className="num">{r[0]}</Td><Td>{r[1]}</Td><Td>{r[2]}</Td><Td r>{cs()}{r[3]}</Td><Td><StatusTag ent="recharge" k={r[4]} form="dot" /></Td><Td r>{r[5]}</Td></Tr>)}
          </Table>
        </Block>
      </Panel>
      <Panel area="detail" head={<><b className="h-title">{t('mb.doRecharge')}</b><span className="help">{t('mb.rechHint')}</span></>} foot={<Button block onClick={() => dispatch('ov:recharge')}>{t('mb.doRecharge')}</Button>}>
        <Cells><Cell k={t('mb.todayPaid')} v={cur("¥2,300.00")} num /><Cell k={t('mb.todayGift')} v={cur("¥270.00")} num /><Cell k={t('mb.toVerify')} v={t('mb.n1')} num /></Cells>
        <Note className="mt-3">{t('mb.rechNote')}</Note>
      </Panel>
    </>
  );
}
function Camps() {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act; const c = s.camps[s.csel] || s.camps[0];
  const list = s.camps.filter((x) => s.cst === 'all' || x[5] === s.cst);
  return (
    <>
      <Panel area="content" head={<><div className="row-between"><b className="h-title">{t('mb.camps')}</b><Button variant="soft" size="sm" onClick={() => dispatch('toast:mb.tplDraft')}><Ico name="plus" />{t('mb.fromTpl')}</Button></div>
        <div className="flex flex-wrap gap-gap"><Filter pressed={s.cst === 'all'} onPressedChange={() => dispatch('cst:all')}>{t('mb.all')}<FilterCount n={s.camps.length} /></Filter>{['running', 'paused', 'draft', 'ended'].map((k) => <Filter key={k} pressed={s.cst === k} onPressedChange={() => dispatch('cst:' + k)}><StatusIcon ent="campaign" k={k} size={15} /><span>{stName('campaign', k, lang)}</span><FilterCount n={s.camps.filter((x) => x[5] === k).length} /></Filter>)}</div></>}>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-gap content-start">
          {list.map((x) => { const i = s.camps.indexOf(x); return <StatusTile key={x[0]} group={stGroup('campaign', x[5])} quiet={x[5] === 'ended'} current={s.csel === i} onClick={() => dispatch('csel:' + i)} className="py-3 px-3.5 gap-0.5 min-h-124px"><span className="flex items-center justify-between gap-2 mb-0.5"><b className="text-lg font-700">{x[1]}</b><StatusTag ent="campaign" k={x[5]} form="dot" /></span><span className="help ellipsis">{x[2]} · {x[3]}</span><span className="help ellipsis">{x[4]}</span><span className="help ellipsis">{x[6]}</span></StatusTile>; })}
        </div>
      </Panel>
      <Panel area="detail" head={<><div className="row-between"><b className="h-title">{c[1]}</b><StatusTag ent="campaign" k={c[5]} /></div><span className="help">{c[0]} · {c[2]}</span></>}
        foot={c[5] === 'running' ? <Acts><Button variant="muted" onClick={() => dispatch('camp:paused')}>{t('mb.pause')}</Button><Button variant="danger" onClick={() => dispatch('camp:ended')}>{t('mb.end')}</Button></Acts> : c[5] === 'paused' ? <Acts><Button variant="danger" onClick={() => dispatch('camp:ended')}>{t('mb.end')}</Button><Button block onClick={() => dispatch('camp:running')}>{t('mb.resume')}</Button></Acts> : c[5] === 'draft' ? <Acts><Button variant="danger" onClick={() => dispatch('toast:mb.draftDeleted')}>{t('md.delete')}</Button><Button block onClick={() => dispatch('camp:running')}>{t('mb.publish')}</Button></Acts> : undefined}>
        <Cells><Cell k={t('mb.audience')} v={c[3]} /><Cell k={t('mb.schedule')} v={c[4]} /><Cell k={t('mb.progress')} v={c[6]} /><Cell k={t('mb.withCoupon')} v={t('mb.exclusive')} /><Cell k={t('mb.manual')} v={t('mb.noMore')} /></Cells>
        <Note className="mt-3">{t('mb.campNote')}</Note>
      </Panel>
    </>
  );
}
function Coupons() {
  const t = useT(); const s = useStore(), dispatch = s.act; const v = s.redeem;
  return (
    <>
      <Panel area="content" head={<><b className="h-title">{t('mb.coupons')}</b><span className="help">{t('mb.couponHelp')}</span></>}>
        <Table cols="minmax(0,1.4fr) 80px 88px 72px 72px 72px" head={[t('mb.th.coupon'), t('mb.th.type'), t('mb.th.how'), <span className="block text-right">{t('mb.th.issued')}</span>, <span className="block text-right">{t('mb.th.used')}</span>, <span className="block text-right">{t('mb.th.rate')}</span>]}>
          {CPNT.map((c) => <Tr key={c[0]}><Td><b>{c[0]}</b></Td><Td>{c[1]}</Td><Td>{c[2]}</Td><Td r>{c[3]}</Td><Td r>{c[4]}</Td><Td r>{Math.round((c[4] / c[3]) * 100)}%</Td></Tr>)}
        </Table>
        <Block title={t('mb.couponSt')} className="mt-4"><div className="flex flex-wrap gap-gap">{['unused', 'used', 'expired', 'revoked'].map((k) => <StatusTag key={k} ent="coupon" k={k} />)}</div></Block>
      </Panel>
      <Panel area="detail" head={<><b className="h-title">{t('mb.redeemTitle')}</b><span className="help">{t('mb.redeemHint')}</span></>} foot={v === 'show' ? <Acts><Button variant="muted" onClick={() => dispatch('redeem:')}>{t('common.cancel')}</Button><Button variant="danger-solid" block onClick={() => dispatch('redeem:done')}>{t('mb.confirmRedeem')}</Button></Acts> : undefined}>
        <Search placeholder={t('mb.scanCode')} /><div className="mt-2"><Button variant="muted" size="sm" onClick={() => dispatch('redeem:show')}>{t('mb.simScan')}</Button></div>
        <div className="mt-3 flex flex-col gap-2">
          {v ? <><CouponCard off={v === 'done'} value="兑" title={t('mb.drinkCoupon')} lines={[t('mb.drinkDesc')]} right={<StatusTag ent="coupon" k={v === 'done' ? 'used' : 'unused'} form="dot" />} />{v === 'done' ? <Note>{t('mb.redeemedNote')}</Note> : <Note tone="warn">{t('mb.redeemWarn')}</Note>}</> : <Empty title="" desc={t('mb.waiting')} />}
        </div>
      </Panel>
    </>
  );
}
function Points() {
  const t = useT();
  return (
    <>
      <Panel area="content" head={<><b className="h-title">{t('mb.points')}</b><span className="help">{t('mb.pointsHelp')}</span></>}>
        <Note tone="warn">{t('mb.pointsRule')}</Note>
        <Block title={t('mb.buckets')} className="mt-4"><div className="flex flex-wrap gap-gap">{['pending', 'available', 'held', 'refund_reserved'].map((k) => <StatusTag key={k} ent="points" k={k} />)}</div></Block>
        <Block title={t('mb.onlineRules')}><Cells>{[1, 2, 3, 4, 5].map((i) => <Cell key={i} k={t('mb.pr' + i)} v={t('mb.pr' + i + 'v')} />)}</Cells></Block>
      </Panel>
      <Panel area="detail" head={<b className="h-title">{t('mb.tbc')}</b>}><Note>{t('mb.tbcNote')}</Note></Panel>
    </>
  );
}
