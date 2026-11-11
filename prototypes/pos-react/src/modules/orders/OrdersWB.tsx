// 订单工作台（W1）：时段导航 · 状态 / 渠道筛选 + 列表 / 卡片 · 订单详情（含平台券支付记录与撤销核销）
import { useStore } from '@/store';
import { ALERT, OST, pvOf, pvUndoWhy, type OrderRow, type Redeemed } from '@/data/sample';
import { pvName } from '@/store/actions';
import { cs, money } from '@/lib/money';
import { stGroup, stName, stateOf } from '@/lib/states';
import { useLang, useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Rail } from '@/layout/rail';
import { Panel, Block, DueFoot, Acts } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { Search } from '@/components/ui/input';
import { Seg } from '@/components/ui/toggle-group';
import { Filter, FilterCount } from '@/components/ui/toggle';
import { StatusIcon, StatusTag } from '@/components/ui/badge';
import { StatusTile } from '@/components/ui/status-tile';
import { Empty, Note } from '@/components/ui/feedback';
import { CouponCard, PlatMark } from '@/components/ui/coupon';
import { OrderLine } from '@/modules/order/OrderLine';
import { livePlats, platRef } from '@/lib/channels';
import { PlatLogo } from '@/components/ui/plat-logo';

export function OrdersWB() {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const ent = s.och === '外卖' ? 'delivery' : 'order';
  const live = livePlats(s);
  const base = s.orders.filter((o) => (s.och === '全部' ? true : o[2] === s.och) && (s.och !== '外卖' || s.oplat === 'all' || platRef(o[1], s, lang).key === s.oplat));
  const shown = base.filter((o) => s.ost === 'all' || (o[7] === ent && o[8] === s.ost));
  const sum = shown.reduce((a, o) => a + o[6], 0);
  const ch = (c: string) => t('od.ch.' + c);
  const where = (o: OrderRow) => (o[2] === '外卖' ? platRef(o[1], s, lang).label : o[1] === '—' ? ch(o[2]) : o[1] + ' · ' + ch(o[2]));
  const cur = s.orders.find((o) => o[0] === s.osel) || s.orders[0];
  const head = (
    <>
      <div className="flex flex-wrap gap-gap items-center [&>label]:flex-[1_1_220px] [&>label]:w-auto">
        <Search placeholder={t('od.search')} />
        <Seg value={s.och} onChange={(v) => dispatch('och:' + v)} items={[{ v: '全部', label: t('od.all') }, { v: '堂食', label: t('od.dinein') }, { v: '外带', label: t('od.takeaway') }, { v: '外卖', label: t('od.delivery') }]} />
        {s.och === '外卖' && live.length > 1 && <Seg value={s.oplat} onChange={(v) => s.set({ oplat: v, ost: 'all' })} items={[{ v: 'all', label: t('od.allPlats') }, ...live.map((p) => ({ v: p.key, label: <><PlatLogo k={p.key} size={16} />{p.name}</> }))]} />}
        <Seg value={s.oview} onChange={(v) => s.set({ oview: v })} items={[{ v: 'list', label: t('od.list') }, { v: 'card', label: t('od.card') }]} />
      </div>
      <div className="flex flex-wrap gap-gap items-center">
        <Filter pressed={s.ost === 'all'} onPressedChange={() => dispatch('ost:all')}>{t('od.all')}<FilterCount n={base.length} /></Filter>
        {OST[ent].map((k) => { const n = base.filter((o) => o[7] === ent && o[8] === k).length; return <Filter key={k} pressed={s.ost === k} onPressedChange={() => dispatch('ost:' + k)}><StatusIcon ent={ent} k={k} size={15} /><span>{stName(ent, k, lang)}</span><FilterCount n={n} alert={!!ALERT[k] && n > 0} /></Filter>; })}
      </div>
    </>
  );
  const sec = <div className="sec"><span>{t('od.count', { n: shown.length, m: money(sum) })}</span><span>{t('od.byTime')}</span></div>;
  return (
    <>
      <Rail items={[{ cap: t('od.period') }, { v: '今天', label: t('od.today'), em: 46 }, { v: '本班', label: t('od.shift'), em: 16 }, { v: '昨天', label: t('od.yesterday'), em: 52 }, { v: '近 7 天', label: t('od.week'), em: 341 }]} cur={s.oper} onPick={(v) => dispatch('oper:' + v)} ariaLabel={t('od.period')} />
      <Panel area="content" head={head} bodyClass="flex flex-col">
        {!shown.length ? <Empty fill title={t('od.empty')} desc={t('od.emptyHint')} /> : s.oview === 'card' ? (
          <>{sec}<div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-gap w-full content-start">
            {shown.map((o) => { const g = stGroup(o[7], o[8]); return (
              <StatusTile key={o[0]} group={g} alert={!!ALERT[o[8]]} quiet={g === 'closed'} current={s.osel === o[0]} onClick={() => dispatch('osel:' + o[0])} className="py-3 px-3.5 gap-0.5 min-h-124px">
                <span className="flex items-center justify-between gap-2 mb-0.5"><b className="text-lg font-700 num">{o[0]}</b><StatusTag ent={o[7]} k={o[8]} /></span>
                <span className="help ellipsis">{where(o)}{o[3] ? ' · ' + t('od.guests', { n: o[3] }) : ''}</span>
                <span className="help ellipsis">{t('od.pcs', { n: o[4] })} · {o[5]}{o[9] ? ' · ' + t('od.served', { p: o[9] }) : ''}</span>
                <span className="a mt-auto pt-1.5 text-xl font-700 tracking-[-.01em] num">{money(o[6])}</span>
              </StatusTile>); })}
          </div></>
        ) : (
          <>{sec}<div className="flex flex-col gap-0.5 w-full">
            {shown.map((o) => (
              <button key={o[0]} type="button" aria-pressed={s.osel === o[0]} onClick={() => dispatch('osel:' + o[0])} className={cn('grid grid-cols-[112px_64px_minmax(0,1fr)_minmax(0,1fr)_96px] gap-2 items-center min-h-$row px-2.5 rounded-rk text-sm w-full text-left hover:bg-wash', s.osel === o[0] && 'bg-wash-strong')}>
                <StatusTag ent={o[7]} k={o[8]} form="dot" /><b>{o[0]}</b><span className="ellipsis">{where(o)}</span><span className="help">{t('od.pcs', { n: o[4] })} · {o[5]}</span><span className="font-600 text-right num">{money(o[6])}</span>
              </button>))}
          </div></>
        )}
      </Panel>
      <Panel area="detail" ledger head={<><div className="row-between"><b className="h-title">{cur[0]}</b><StatusTag ent={cur[7]} k={cur[8]} /></div><span className="help">{where(cur)} · {cur[5]}</span></>}
        foot={<DueFoot label={t('od.amount')} amt={money(cur[6])} note={t('od.pcsAt', { n: cur[4], t: cur[5] })}>
          <Acts><Button variant="muted" size="sm" onClick={() => dispatch('ov:x3')}>{t('od.combine')}</Button><Button variant="muted" size="sm" onClick={() => dispatch('toast:' + 'od.reprinted')}>{t('od.reprint')}</Button><Button variant="danger" size="sm" onClick={() => dispatch('ov:x2')}>{t('od.reopen')}</Button></Acts>
          <Button block onClick={() => dispatch('goorder')}>{t('od.take')}</Button>
        </DueFoot>}>
        <Block title={t('od.items', { n: s.lines.length })}><div>{s.lines.map((l, i) => <OrderLine key={i} l={l} i={i} edit={false} />)}</div></Block>
        <Block title={t('od.payments')} right={s.redeemed[cur[0]] ? <span>{t('od.pvWindow')}</span> : undefined}><PvRecords cur={cur} /></Block>
      </Panel>
    </>
  );
}
function PvRecords({ cur }: { cur: OrderRow }) {
  const t = useT(); const s = useStore(), dispatch = s.act; const rs: Redeemed[] | undefined = s.redeemed[cur[0]];
  if (!rs) return <Note className="bg-surface">{cur[8] === 'paid' ? t('od.paidWx', { m: money(cur[6]) }) : t('od.unpaid')}</Note>;
  let pvSum = 0, undone = 0;
  return (
    <div className="flex flex-col gap-2">
      {rs.map((r, i) => { const v = pvOf(r[0])!, why = pvUndoWhy(r), amt = v.face * r[1]; if (r[5] !== 'undone') pvSum += amt; else undone += amt; return (
        <div key={i} className="flex flex-col gap-1.5">
          <CouponCard off={why === 'done'} value={cs() + v.face} title={<><PlatMark k={v.plat} />{pvName(s, v.id)}</>} lines={[t('od.redeemedMeta', { t: r[2], who: r[4], n: r[1], m: money(amt) })]} className="bg-surface" />
          <div className="row-between text-sm">
            <StatusTag ent="coupon" k={why === 'done' ? 'unused' : 'used'} label={why === 'done' ? t('od.returned') : t('od.redeemed')} form="dot" />
            {why === 'done' ? <StatusTag ent="coupon" k="unused" label={t('od.undoneTag')} form="dot" /> : why === 'late' ? <span className="help">{t('od.late')}</span> : <Button variant="link" className="hit text-sm" onClick={() => dispatch('pvundo:' + cur[0] + '|' + i)}>{why === 'auth' ? t('od.undoAuth') : t('od.undo')}</Button>}
          </div>
        </div>); })}
      {cur[6] - pvSum - undone > 0 && <Note className="bg-surface">{t('od.restWx', { m: money(cur[6] - pvSum - undone) })}</Note>}
      {undone > 0 && <Note tone="warn">{t('od.toCollect', { m: money(undone) })}</Note>}
    </div>
  );
}
