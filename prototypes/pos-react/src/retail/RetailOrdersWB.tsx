// 零售订单工作台（W1，方案 §4.1）：时段导航 · 订单列表（状态筛选）· 订单详情（商品、优惠、支付、退货记录 · 退货 / 换货 / 补打）。
// 退货规则在 domain/aftersale.ts；列表行、清单行、费用行与餐饮共用组件。
import { useStore } from '@/store';
import { money } from '@/lib/money';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Rail } from '@/layout/rail';
import { Panel, Block, DueFoot, Acts } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { Search } from '@/components/ui/input';
import { Filter, FilterCount } from '@/components/ui/toggle';
import { StatusTag } from '@/components/ui/badge';
import { Cells, Cell } from '@/components/ui/description-list';
import { Empty, Note } from '@/components/ui/feedback';
import { LineRow } from '@/components/pos/line-row';
import { FeeRow } from '@/components/pos/fee-row';
import { payLabel } from '@/components/pos/pay';
import { channelBlock, refundableQty } from '@/domain/aftersale';
import { toast } from 'sonner';
import { item } from './data';
import { openReturn, salePaid, salePcs, saleOf, statusOf, useSales, type Sale } from './sales';
import { useRT } from './dict';

const FILTERS = ['all', 'paid', 'partial_return', 'returned'] as const;
export function RetailOrdersWB() {
  const t = useRT(), lang = useStore((s) => s.lang), en = lang === 'en'; const st = useSales();
  const shown = st.sales.filter((s) => st.filter === 'all' || statusOf(s) === st.filter);
  const cur = saleOf(st.sales, st.sel) || st.sales[0];
  const nav = [{ v: 'today', label: t('od.today'), em: st.sales.length }, { v: 'yday', label: t('od.yday') }, { v: 'week', label: t('od.week') }];
  return (
    <>
      <Rail items={nav} cur={st.period} onPick={(v) => st.set({ period: v })} ariaLabel={t('od.title')} />
      <Panel area="content" head={<>
        <Search placeholder={t('od.search')} />
        <div className="flex flex-wrap gap-gap">{FILTERS.map((f) => { const n = f === 'all' ? st.sales.length : st.sales.filter((s) => statusOf(s) === f).length;
          return <Filter key={f} pressed={st.filter === f} onPressedChange={() => st.set({ filter: f })}>{f === 'all' ? t('od.all') : <StatusTag ent="order" k={f} form="dot" className="text-inherit" />}<FilterCount n={n} /></Filter>; })}</div>
      </>}>
        <div className="sec"><span>{t('od.count', { n: shown.length, m: money(shown.reduce((a, s) => a + salePaid(s), 0)) })}</span></div>
        {!shown.length ? <Empty fill title={t('od.all')} /> : <div className="flex flex-col gap-0.5 w-full">
          {shown.map((s) => (
            <button key={s.no} type="button" aria-pressed={st.sel === s.no} onClick={() => st.set({ sel: s.no })}
              className={cn('grid grid-cols-[96px_64px_minmax(0,1fr)_minmax(0,1fr)_88px] gap-2 items-center min-h-$row px-2.5 rounded-rk text-sm w-full text-left hover:bg-wash', st.sel === s.no && 'bg-wash-strong')}>
              <StatusTag ent="order" k={statusOf(s)} form="dot" /><b className="num">{s.no}</b>
              <span className="ellipsis">{t('ch.' + s.ch)}{s.member ? ' · ' + s.member : ''}</span><span className="help num">{t('od.pcs', { n: salePcs(s) })} · {s.time}</span>
              <span className="font-600 text-right num">{money(salePaid(s))}</span>
            </button>))}
        </div>}
      </Panel>
      {cur && <SaleDetail s={cur} en={en} />}
    </>
  );
}

function SaleDetail({ s, en }: { s: Sale; en: boolean }) {
  const t = useRT(), lang = useStore((x) => x.lang); const st = statusOf(s), block = channelBlock(s.ch);
  const why = block ? t('od.noReturnPlatform') : st === 'returned' ? t('od.noReturnAll') : '';
  return (
    <Panel area="detail" ledger
      head={<><div className="row-between"><b className="h-title num">{s.no}</b><StatusTag ent="order" k={st} /></div><span className="help">{t('ch.' + s.ch)}{s.member ? ' · ' + s.member : ''} · {s.time}</span></>}
      foot={<DueFoot label={t('od.paid')} amt={money(salePaid(s))} note={t('od.pcs', { n: salePcs(s) })}>
        {why && <Note className="bg-wash">{why}</Note>}
        <Acts><Button variant="muted" size="sm" onClick={() => toast(t('od.reprint'))}>{t('od.reprint')}</Button><Button variant="muted" size="sm" disabled={!!why} onClick={() => openReturn(s.no, 2, 'exchange')}><Ico name="arrow-left-right" />{t('od.exchange')}</Button><Button size="sm" block disabled={!!why} onClick={() => openReturn(s.no, 2)}><Ico name="undo-2" />{t('od.return')}</Button></Acts>
      </DueFoot>}>
      <Block title={t('od.lines', { n: salePcs(s) })}>
        {s.lines.map((l) => { const it = item(l.id), ret = l.returned || 0;
          const meta = <>{l.weighed ? <StatusTag ent="item" k="weighed" form="dot" label={`${l.w!.toFixed(3)} kg × ¥${l.p.toFixed(2)}`} /> : <span>¥{l.p.toFixed(2)} × {l.q}</span>}{l.sku && <span>{en ? l.sku[1] : l.sku[0]}</span>}{ret > 0 && <StatusTag ent="item" k="returned" form="dot" label={t('od.returnedN', { n: l.weighed ? '' : ret }).trim()} />}</>;
          return <LineRow key={l.key} name={en ? it.en : it.n} amt={money(l.amt)} meta={meta} side={l.weighed ? '' : '×' + l.q} voided={refundableQty(l) === 0} />; })}
      </Block>
      {s.disc > 0 && <Block title={t('od.disc')}><FeeRow icon="badge-percent" name={t('od.disc')} amt={'−' + money(s.disc)} neg /></Block>}
      <Block title={t('od.payments')}><Cells>{s.pay.map(([m, a]) => <Cell key={m} k={<span className="inline-flex items-center gap-1.5 text-ink"><StatusTag ent="payment" k="paid" form="dot" label={payLabel(m, lang)} /></span>} v={money(a)} num />)}</Cells></Block>
      {s.returns.length > 0 && <Block title={t('od.returns')}><Cells>{s.returns.map((r, i) => <Cell key={i} k={<span className="inline-flex items-center gap-1.5 text-ink">{r.method === 'exchange' ? <StatusTag ent="item" k="moved" form="dot" label={t('ex.block')} /> : <StatusTag ent="return" k="refunded" form="dot" />}{t('od.retRow', { t: r.time, n: r.pcs, w: t('rt.r.' + r.why) })}</span>}><span className="num text-saving font-600">−{money(r.amt)}</span></Cell>)}</Cells></Block>}
    </Panel>
  );
}
