// 报表（W4）：概况 · 时段 · 菜品排行 · 支付方式 · 会员与储值 · 优惠 · 退菜与赠送
import { useStore } from '@/store';
import { RP } from '@/data/sample';
import { useT } from '@/i18n';
import { Ico } from '@/lib/icon';
import { Rail } from '@/layout/rail';
import { Panel, Block } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { Seg } from '@/components/ui/toggle-group';
import { StatusTag } from '@/components/ui/badge';
import { Table, Tr, Td } from '@/components/ui/table';
import { Kpi, Kpis, VBars, HBars, yuan } from '@/components/ui/chart';
import { cur } from '@/lib/money';

export function ReportsWB() {
  const t = useT(); const s = useStore(), dispatch = s.act;
  const nav = [{ cap: t('rp.nav') }, { v: '概况', icon: 'layout-dashboard', label: t('rp.overview') }, { v: '时段', icon: 'clock', label: t('rp.hours') }, { v: '菜品', icon: 'soup', label: t('rp.dishes') }, { v: '支付', icon: 'wallet', label: t('rp.pays') }, { v: '会员', icon: 'users', label: t('rp.members') }, { v: '优惠', icon: 'ticket-percent', label: t('rp.promos') }, { v: '退赠', icon: 'undo-2', label: t('rp.voids') }];
  const link = (sec: string) => <Button variant="link" className="hit text-xs" onClick={() => dispatch('rsec:' + sec)}>{t('rp.detail')}</Button>;
  const pcs = (v: number) => t('rp.nPcs', { n: v }), ppl = (v: number) => t('rp.nPeople', { n: v });
  const Pct = ({ v }: { v: number }) => <>{Math.round((v / 8642) * 100)}%</>;
  let body: React.ReactNode;
  const sec = s.rsec;
  if (sec === '时段') body = <><Block title={t('rp.byHour')} right={<span>{t('rp.peak')}</span>}><VBars data={RP.hours} fmt={yuan} label={t('rp.byHour')} /></Block><Block title={t('rp.data')}><Table cols="repeat(13,minmax(0,1fr))" head={RP.hours.map((h) => <span className="block text-right">{t('rp.hourN', { n: h[0] })}</span>)}><Tr>{RP.hours.map((h) => <Td key={h[0]} r>{h[1]}</Td>)}</Tr></Table></Block></>;
  else if (sec === '菜品') { const by = s.rby === 'amt' ? 2 : 1, list = RP.dishes.slice().sort((a, b) => b[by] - a[by]);
    body = <><Block title={t('rp.rank', { b: by === 2 ? t('rp.byAmt') : t('rp.byQty') })} right={<Seg value={s.rby} onChange={(v) => dispatch('rby:' + v)} items={[{ v: 'qty', label: t('rp.qty') }, { v: 'amt', label: t('rp.amt') }]} />}><HBars data={list.map((d) => [d[0], d[by]])} fmt={by === 2 ? yuan : pcs} /></Block>
      <Block title={t('rp.data')}><Table cols="32px minmax(0,1fr) 80px 96px 80px" head={['#', t('md.th.dish'), <span className="block text-right">{t('rp.qty')}</span>, <span className="block text-right">{t('rp.amt')}</span>, <span className="block text-right">{t('rp.share')}</span>]}>{list.map((d, i) => <Tr key={d[0]}><Td>{i + 1}</Td><Td>{d[0]}</Td><Td r>{d[1]}</Td><Td r>{yuan(d[2])}</Td><Td r><Pct v={d[2]} /></Td></Tr>)}</Table></Block></>; }
  else if (sec === '支付') body = <><Block title={t('rp.payAmt')} right={<span>{t('rp.storedNote')}</span>}><HBars data={RP.pays.map((p) => [p[0], p[1]])} fmt={yuan} /></Block><Block title={t('rp.data')}><Table cols="minmax(0,1fr) 96px 72px 72px" head={[t('rp.method'), <span className="block text-right">{t('rp.amt')}</span>, <span className="block text-right">{t('rp.count')}</span>, <span className="block text-right">{t('rp.share')}</span>]}>{RP.pays.map((p) => <Tr key={p[0]}><Td>{p[0]}</Td><Td r>{yuan(p[1])}</Td><Td r>{p[2]}</Td><Td r><Pct v={p[1]} /></Td></Tr>)}</Table></Block></>;
  else if (sec === '会员') body = <><Kpis><Kpi label={t('rp.memberShare')} value="38%" note={t('rp.memberShareD')} st="up" /><Kpi label={t('rp.newMembers')} value="6" note={t('rp.leads2')} /><Kpi label={t('rp.storedIn')} value={cur("¥2,300")} note={t('rp.notRevenue')} /><Kpi label={t('rp.storedSpend')} value={cur("¥1,180")} note={t('rp.n13')} /><Kpi label={t('rp.storedBal')} value={cur("¥48,320")} note={t('rp.n126')} /></Kpis><Block title={t('rp.layersToday')}><HBars data={RP.layers} fmt={ppl} /></Block><Block title={t('rp.note')}><p className="help m-0">{t('rp.pointsNote')}</p></Block></>;
  else if (sec === '优惠') body = <><Kpis><Kpi label={t('rp.discTotal')} value={cur("¥612")} note={t('rp.discShare')} /><Kpi label={t('rp.camp')} value={cur("¥412")} note={t('rp.n31')} /><Kpi label={t('rp.coupon')} value={cur("¥90")} note={t('rp.n6')} /><Kpi label={t('rp.manualRound')} value={cur("¥110")} note={t('rp.n44')} /><Kpi label={t('rp.pvDeduct')} value={cur("¥1,936")} note={t('rp.pvNote')} /></Kpis>
    <Block title={t('rp.promoMix')}><HBars data={RP.promos.map((p) => [p[0], p[1]])} fmt={yuan} /></Block><Block title={t('rp.data')}><Table cols="minmax(0,1fr) 96px 72px" head={[t('rp.source'), <span className="block text-right">{t('rp.amt')}</span>, <span className="block text-right">{t('rp.orders')}</span>]}>{RP.promos.map((p) => <Tr key={p[0]}><Td>{p[0]}</Td><Td r>{yuan(p[1])}</Td><Td r>{p[2]}</Td></Tr>)}</Table></Block>
    <Block title={t('rp.pvTitle')} right={<span>{t('rp.pvFoot')}</span>}><HBars data={RP.pvs.map((p) => [p[0], p[1]])} fmt={yuan} /></Block></>;
  else if (sec === '退赠') body = <><Kpis><Kpi label={t('rp.void')} value={t('rp.nBi', { n: 2 })} note={cur("¥18.00")} /><Kpi label={t('rp.comp')} value={t('rp.nBi', { n: 1 })} note={cur("¥88.00")} /><Kpi label={t('rp.manualDisc')} value={t('rp.nBi', { n: 1 })} note={cur("¥20.00")} /><Kpi label={t('rp.approvals')} value={t('rp.nBi', { n: 3 })} note={t('rp.allAuth')} /></Kpis>
    <Block title={t('rp.details')}><Table cols="56px minmax(0,1fr) 80px 96px 64px 64px" head={[t('rp.th.time'), t('rp.th.what'), t('rp.th.type'), t('rp.th.why'), t('rp.th.who'), t('rp.th.auth')]}>{RP.voids.map((v) => <Tr key={v[0]}>{v.map((x, i) => <Td key={i}>{i === 2 ? <StatusTag ent="item" k={x === '退菜' ? 'voided' : x === '赠送' ? 'comp' : 'repriced'} label={x} form="dot" /> : x}</Td>)}</Tr>)}</Table></Block></>;
  else body = <><Kpis><Kpi label={t('rp.revenue')} value={cur("¥8,642")} note={t('rp.vsYesterday')} st="up" /><Kpi label={t('rp.orders')} value="46" note="+3" /><Kpi label={t('rp.avg')} value={cur("¥187.9")} note="+2.1%" st="up" /><Kpi label={t('rp.turns')} value="1.6" note="−0.1" st="down" /><Kpi label={t('rp.discTotal')} value={cur("¥612")} note="7.1%" /><Kpi label={t('rp.storedIn')} value={cur("¥2,300")} note={t('rp.notRevenue')} /></Kpis>
    <div className="grid grid-cols-2 gap-x-6 gap-y-4 [&>div]:mt-0 [&>div]:mb-4 [&>div]:min-w-0"><Block title={t('rp.byHour')} right={link('时段')}><VBars data={RP.hours} fmt={yuan} label={t('rp.byHour')} /></Block><Block title={t('rp.pays')} right={link('支付')}><HBars data={RP.pays.map((p) => [p[0], p[1]])} fmt={yuan} /></Block>
      <Block title={t('rp.top5')} right={link('菜品')}><HBars data={RP.dishes.slice().sort((a, b) => b[1] - a[1]).slice(0, 5).map((d) => [d[0], d[1]])} fmt={pcs} /></Block><Block title={t('rp.tables')}><Kpis sm><Kpi label={t('rp.dining')} value={t('rp.nTables', { n: 8 })} /><Kpi label={t('rp.due')} value={t('rp.nTables', { n: 3 })} /><Kpi label={t('rp.avgDining')} value={t('rp.nMin', { n: 52 })} /><Kpi label={t('rp.late')} value={t('rp.nTables', { n: 1 })} st="down" /></Kpis></Block></div></>;
  return (
    <>
      <Rail items={nav} cur={s.rsec} onPick={(v) => dispatch('rsec:' + v)} ariaLabel={t('rp.nav')} />
      <Panel area="content" head={<div className="flex flex-wrap gap-gap items-center"><Seg value={s.rrange} onChange={(v) => dispatch('rrange:' + v)} items={['今天', '昨天', '本周', '本月'].map((r) => ({ v: r, label: t('rp.range.' + r) }))} /><span className="help">{t('rp.meta')}</span><span className="flex-1" /><Button variant="muted" size="sm" onClick={() => dispatch('toast:rp.printed')}><Ico name="printer" />{t('rp.print')}</Button><Button variant="muted" size="sm" onClick={() => dispatch('toast:rp.exported')}><Ico name="download" />{t('rp.export')}</Button></div>}>{body}</Panel>
    </>
  );
}
