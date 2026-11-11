// 结账工作台（W2）：左「账单与优惠」（440，P2 块色清单）· 右「收款」（步骤条 → 待收 → 收款方式 → 当前方式的操作区）
import { useStore, useTotals, type PosState } from '@/store';
import { PAYS, PAY_EN, MEMBER, COUPONS, pvOf } from '@/data/sample';
import { pvName } from '@/store/actions';
import { money, round2 } from '@/lib/money';
import type { Totals } from '@/lib/pricing';
import { useLang, useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Panel, Block, DueFoot, Acts } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { PromoRow } from '@/components/ui/promo-row';
import { Chip, StatusIcon, StatusTag } from '@/components/ui/badge';
import { Cells, Cell } from '@/components/ui/description-list';
import { Empty, Note } from '@/components/ui/feedback';
import { Filter } from '@/components/ui/toggle';
import { Seg } from '@/components/ui/toggle-group';
import { OrderLine } from '@/modules/order/OrderLine';
import { CashPad, PayHead, WaitBlock } from '@/components/pos/pay';
import { methodBlock } from '@/domain/payment';

export const payName = (s: PosState, k: string) => (s.lang === 'en' ? PAY_EN[k]?.[0] : PAYS.find((p) => p.key === k)?.name) || k;
/** 收款方式不可用的原因：规则在 domain/payment.methodBlock，这里只把原因 key 换成文案 */
const WHY: Record<string, string> = { offline: 'co.offlineNo', svMember: 'co.svMember', svShort: 'co.svShort', svMix: 'co.svMix' };
function methodWhy(m: string, s: PosState, t: Totals, tr: ReturnType<typeof useT>) { const b = methodBlock(m, { offline: s.banner, member: s.member, balance: s.memberBal, rest: t.rest, paidCount: s.paid.length }); return b ? tr(WHY[b.key], b.params) : ''; }

/* 现金块：组件在 components/pos/pay.tsx（零售收银共用），这里只接 store 动作 */
function CashBlock({ due, okAct, okLabel }: { due: number; okAct?: string; okLabel?: string }) {
  const got = useStore((s) => s.got), dispatch = useStore((s) => s.act);
  return <CashPad due={due} got={got} okLabel={okLabel} onOk={() => dispatch(okAct ?? 'cashok')} onKey={(k) => dispatch('kp:' + k)} onQuick={(v) => dispatch('got:' + v)} />;
}

function BillSide({ t: tot }: { t: Totals }) {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const c = tot.coupon, act = tot.act > 0, pvN = s.pv.reduce((a, x) => a + x.n, 0);
  const pvs = s.pv.map((x) => pvName(s, x.id) + (x.n > 1 ? ' ×' + x.n : '')).join(lang === 'en' ? ', ' : '、'), pvNo = t('co.pv.noShare');
  const mem = s.member ? (
    <div className="bg-surface rounded-rt py-2.5 px-3 flex flex-col gap-2">
      <div className="flex items-center gap-1.5 flex-wrap"><StatusIcon ent="member" k="linked" /><b>{MEMBER.name}</b><Chip>{MEMBER.layer}</Chip>{MEMBER.tags.map((x) => <Chip key={x}>{x}</Chip>)}<Button variant="link" size="sm" className="hit ml-auto text-sm" onClick={() => dispatch('sheet:member')}>{t('co.switchMember')}</Button></div>
      <div className="grid grid-cols-3 gap-2"><span><small className="block text-xs text-sub">{t('sheet.member.bal')}</small><b className="num">¥{s.memberBal.toFixed(2)}</b></span><span><small className="block text-xs text-sub">{t('sheet.member.coupons')}</small><b className="num">{t('sheet.member.couponsV', { n: MEMBER.coupons })}</b></span><span><small className="block text-xs text-sub">{t('co.points')}</small><b className="num">2,310</b></span></div>
    </div>
  ) : (
    <><div className="flex flex-wrap gap-gap"><Button variant="muted" size="sm" onClick={() => dispatch('scan:member')}><Ico name="scan-line" size={16} />{t('co.scanMember')}</Button><Button variant="muted" size="sm" onClick={() => dispatch('sheet:member')}>{t('co.phone')}</Button><Button variant="muted" size="sm" onClick={() => dispatch('mnew')}>{t('co.newMember')}</Button></div><span className="help block mt-1.5">{t('co.memberHelp')}</span></>
  );
  return (
    <Panel area="detail" ledger
      head={<div className="row-between"><b className="h-title">{t('co.bill', { t: s.table })}</b><span className="flex items-center gap-gap"><span className="help">{t('co.billMeta')}</span><Button variant="muted" size="sm" onClick={() => dispatch('goorder')}>{t('co.edit')}</Button></span></div>}
      foot={<DueFoot label={t('co.due')} amt={money(tot.due)} note={t('order.dueNote', { n: s.lines.filter((l) => l.st !== 'void').length, m: money(tot.mem + tot.round) })} />}>
      <Block title={t('co.items', { n: s.lines.length, m: money(tot.sub) })} right={<Button variant="link" className="hit text-sm" onClick={() => dispatch('lines:' + (s.showLines ? 'off' : 'on'))}>{s.showLines ? t('co.collapse') : t('co.expand')}</Button>}>
        {s.showLines && <div>{s.lines.map((l, i) => <OrderLine key={i} l={l} i={i} edit={false} />)}</div>}
      </Block>
      <Block title={t('co.member')}>{mem}</Block>
      <Block title={t('co.fees')} right={<span>{t('co.feesOrder')}</span>}>
        <div className="flex flex-col gap-0.5">
          <PromoRow on icon="receipt" title={t('co.svc')} desc={t('co.svcDesc')} amt={'+' + money(tot.svc)} />
          <PromoRow on={tot.pv > 0} title={tot.pv ? t('co.pv.n', { n: pvN }) : t('co.pv.idle')} desc={tot.pv ? pvs : t('co.pv.desc')} amt={tot.pv ? '−' + money(tot.pv) : undefined} onClick={() => dispatch('ov:pvoucher')} why={s.banner && !tot.pv ? t('co.pv.offline') : ''} />
          <PromoRow on={act} title={t('co.act')} desc={t('co.actDesc')} amt={act ? '−' + money(tot.act) : undefined} onClick={() => dispatch('pact')} why={tot.pv ? pvNo : ''} />
          {s.member && <PromoRow title={t('co.vip')} why={t('co.vipWhy')} />}
          <PromoRow on={!!c} title={c ? c.kind + ' · ' + c.name : t('co.coupon')} desc={c ? t('co.couponOn') : s.member || s.cpScan ? t('co.couponAvail') : t('co.couponNeed')} amt={c ? '−' + money(tot.cp) : undefined} onClick={() => dispatch('ov:coupons')} why={tot.pv ? pvNo : act ? t('co.couponNoAct') : ''} />
          <PromoRow title={t('co.memberPrice')} why={t('co.memberPriceWhy')} />
          <PromoRow title={t('co.points')} why={t('co.pointsWhy')} />
          <PromoRow on={tot.manual > 0} title={t('co.manual')} desc={tot.manual ? t(s.mwhy) : t('co.manualDesc')} amt={tot.manual ? '−' + money(tot.manual) : undefined} onClick={() => dispatch('ov:manual')} why={tot.pv ? pvNo : act || c ? t('co.manualNo') : ''} />
          <PromoRow on={tot.round > 0} title={t('co.round')} desc={tot.round ? t('co.roundOff', { m: money(tot.round) }) : ''} amt={tot.round ? '−' + money(tot.round) : undefined} why={tot.pv ? t('co.roundPv') : c ? t('co.roundCp') : tot.round ? '' : t('co.roundNone')} />
        </div>
      </Block>
    </Panel>
  );
}

export function CheckoutWB() {
  const t = useT(); const s = useStore(), dispatch = s.act, tot = useTotals(), paid = s.paid;
  let body: React.ReactNode, foot: React.ReactNode = null;
  if (!tot.rest && paid.length) body = <Empty fill icon={<Ico name="circle-check" size={44} className="text-success-text" />} title={t('co.allPaid')} desc={t('co.tapDone')} />;
  else if (!tot.due && tot.pv) body = <Empty fill icon={<Ico name="ticket-check" size={44} className="text-success-text" />} title={t('co.pvAll')} desc={(tot.pvOver ? t('co.pvOver', { m: money(tot.pvOver) }) : '') + t('co.pvRedeem')} />;
  else if (!s.pay) body = <Empty fill icon={<Ico name="hand-coins" size={44} className="text-success-text" />} title={t('co.pickMethod')} desc={t('co.pickMethodHint')} />;
  else if (s.pay === 'cash') body = <CashBlock due={tot.rest} />;
  else if (s.pay === 'mix') body = (
    <>
      <Block title={t('co.mixPaid', { n: paid.length })} right={<span>{t('co.mixRest', { m: money(tot.rest) })}</span>}>
        {paid.length ? <Cells>{paid.map((p, i) => <Cell key={i} k={<span className="inline-flex items-center gap-1.5 text-ink"><StatusIcon ent="payment" k="paid" />{payName(s, p[0])}</span>}><span className="flex items-center gap-gap"><b className="num">{money(p[1])}</b><Button variant="link" className="hit text-sm" onClick={() => dispatch('undo:' + i)}>{t('co.undo')}</Button></span></Cell>)}</Cells> : <Note>{t('co.mixNote')}</Note>}
      </Block>
      <Block><div className="row-between"><Seg value={s.mixm} onChange={(v) => dispatch('mixm:' + v)} items={['cash', 'wx', 'ali', 'card'].map((m) => ({ v: m, label: payName(s, m) }))} /><span className="help">{t('co.thisOne', { m: payName(s, s.mixm) })}</span></div></Block>
      <div className="mt-4 flex flex-col flex-1 min-h-0"><CashBlock due={Math.min(tot.rest, s.got || tot.rest)} okAct="mixadd" okLabel={t('co.takeThis')} /></div>
    </>
  );
  else if (s.pay === 'card') { body = <WaitBlock title={t('co.cardWait')} hint={t('co.cardHint')} dev="online" devLabel={t('co.cardDev')} />; foot = <><Button variant="muted" onClick={() => dispatch('pay:cash')}>{t('co.cancelPay')}</Button><Button variant="soft" block onClick={() => dispatch('payok')}>{t('co.manualOk')}</Button></>; }
  else if (s.pay === 'sv') {
    body = s.svWait ? <WaitBlock title={t('co.svWait')} hint={t('co.svWaitHint')} dev="online" devLabel={t('co.svPushed', { p: MEMBER.phone })} />
      : <><Cells><Cell k={t('co.svMemberK')} v={MEMBER.name + ' · ' + MEMBER.phone} /><Cell k={t('co.svBalK')} v={'¥' + s.memberBal.toFixed(2)} num /><Cell k={t('co.svCharge')}><span className="num text-primary-text font-700">{money(tot.rest)}</span></Cell><Cell k={t('co.svAfter')} v={money(round2(s.memberBal - tot.rest))} num /></Cells><Note className="mt-2">{t('co.svNote')}</Note></>;
    foot = s.svWait ? <><Button variant="muted" onClick={() => dispatch('svcancel')}>{t('co.svCancel')}</Button><Button variant="soft" block onClick={() => dispatch('svok')}>{t('co.svConfirmed')}</Button></>
      : <><Button variant="muted" onClick={() => dispatch('pay:cash')}>{t('common.cancel')}</Button><Button block onClick={() => dispatch('svgo')}>{t('co.svGo', { m: money(tot.rest) })}</Button></>;
  }
  else { body = <WaitBlock title={t('co.qrWait')} hint={t('co.qrHint')} dev="online" devLabel={t('co.qrDev')} />; foot = <><Button variant="muted" onClick={() => dispatch('pay:cash')}>{t('co.cancelPay')}</Button><Button variant="soft" block onClick={() => dispatch('payok')}>{t('co.manualOk')}</Button></>; }
  const done = !tot.rest && (paid.length > 0 || tot.pv > 0);
  if (done) foot = <Button block onClick={() => dispatch('ov:p3')}>{t('co.done')}</Button>;
  const step = done ? 4 : s.pay ? 3 : 2;
  const methods: Record<string, { desc?: string; why?: string }> = {};
  PAYS.forEach((p) => { const why = methodWhy(p.key, s, tot, t);
    methods[p.key] = { why, desc: p.key === 'sv' && s.member ? t('co.svBal', { b: s.memberBal }) : undefined }; });
  const head = <PayHead step={step} rest={tot.rest} due={tot.due} paid={tot.paid} settled={!tot.rest} value={s.pay} onChange={(v) => dispatch('pay:' + v)} methods={methods} pickHint={step === 2} />;
  return (
    <>
      <BillSide t={tot} />
      <Panel area="content" head={head} bodyClass="flex flex-col" foot={foot ? <Acts>{foot}</Acts> : undefined}>{body}</Panel>
    </>
  );
}
