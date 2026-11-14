// 结账 / 订单的浮层：收款成功 p3 · 支付失败 p4 · 选券 coupons（侧滑）· 平台券验券 pvoucher（侧滑）· 整单折扣 manual · 撤销核销 pvundo
import { useStore, useTotals } from '@/store';
import { COUPONS, PLATS, PVERR, pvOf, pvUndoWhy, type PVoucher } from '@/data/sample';
import { pvName } from '@/store/actions';
import { cpWhy, pvWhy } from '@/lib/pricing';
import { cs, money } from '@/lib/money';
import { useLang, useT } from '@/i18n';
import { Dialog, DialogTitle, DialogDesc, DialogActs } from '@/components/ui/dialog';
import { Sheet } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Cells, Cell } from '@/components/ui/description-list';
import { StatusIcon, StatusTag } from '@/components/ui/badge';
import { Note, Empty } from '@/components/ui/feedback';
import { Result, Amount } from '@/components/ui/result';
import { CouponCard, PlatMark } from '@/components/ui/coupon';
import { Filter, FilterCount } from '@/components/ui/toggle';
import { OptionGroup, Option } from '@/components/ui/radio-cards';
import { Stepper } from '@/components/ui/stepper';
import { Keypad, Pins } from '@/components/ui/keypad';
import { Ico } from '@/lib/icon';
import { payName } from '@/modules/checkout/CheckoutWB';
import { useState } from 'react';

const useClose = () => { const dispatch = useStore((s) => s.act); return () => dispatch('ov:none'); };
export function CheckoutOverlays() {
  const ov = useStore((s) => s.ov);
  return <><PaidDialog open={ov === 'p3'} /><FailDialog open={ov === 'p4'} /><CouponsSheet open={ov === 'coupons'} /><PVoucherSheet open={ov === 'pvoucher'} /><ManualDialog open={ov === 'manual'} /><PvUndoDialog open={ov === 'pvundo'} /></>;
}
function PaidDialog({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore(), tot = useTotals(); const pl: [string, number][] = s.paid.length ? s.paid : [[s.pay, tot.due]];
  return (
    <Dialog open={open} onClose={close} ariaLabel={t('p3.title', { m: money(tot.due) })}>
      <Result title={<DialogTitle>{t('p3.title', { m: money(tot.due) })}</DialogTitle>}>{s.change > 0 && <Amount sm label={t('co.change')} value={money(s.change)} className="w-auto gap-3" />}</Result>
      <Cells>
        {pl.map((p, i) => <Cell key={i} k={<span className="inline-flex items-center gap-1.5 text-ink"><StatusIcon ent="payment" k="paid" />{payName(s, p[0]) || t('p3.received')}</span>} v={money(p[1])} num />)}
        {tot.mem > 0 && <Cell k={t('p3.discount')} v={'−' + money(tot.mem + tot.round)} num />}{s.member && <Cell k={t('p3.svBal')} v={money(s.memberBal)} num />}
      </Cells>
      <span className="help">{t('p3.help')}</span>
      <DialogActs><Button variant="muted" onClick={() => s.act('toast:p3.printing')}>{t('p3.reprint')}</Button><Button variant="muted" onClick={() => s.act('toast:p3.invoiceQr')}>{t('p3.invoice')}</Button><Button onClick={() => s.act('paid')}>{t('p3.next')}</Button></DialogActs>
    </Dialog>
  );
}
function FailDialog({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore(), tot = useTotals();
  return (
    <Dialog open={open} onClose={close} ariaLabel={t('p4.title')}>
      <Result bad title={<DialogTitle>{t('p4.title')}</DialogTitle>}><span className="help">{t('p4.desc', { h: payName(s, s.pay || 'wx'), m: money(tot.due) })}</span></Result>
      <Note>{t('p4.note')}</Note>
      <DialogActs><Button variant="muted" onClick={() => s.act('payother')}>{t('p4.other')}</Button><Button onClick={() => s.act('toast:p4.retried')}>{t('p4.retry')}</Button></DialogActs>
    </Dialog>
  );
}
function CouponsSheet({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore(), tot = useTotals();
  return (
    <Sheet open={open} onClose={close} title={t('cp.title')} closeLabel={t('common.close')}>
      <div className="flex items-center gap-2 h-ctl-sm px-px rounded-rb bg-wash text-base text-hint"><Ico name="scan-line" size={16} className="text-sub" />{t('cp.scan')}</div>
      <span className="help">{t('cp.help')}</span>
      {COUPONS.map((c) => { const why = cpWhy(c, tot.sub, s.lines), on = s.cpn === c.id; return <CouponCard key={c.id} on={on} off={!!why} onClick={() => s.act('cpn:' + c.id)} value={c.disc ? t('cp.d8') : cs() + c.amt} title={c.name} lines={[c.kind + ' · ' + (why || t('cp.till'))]} right={on ? <Ico name="circle-check" /> : undefined} />; })}
      <div><Button variant="muted" onClick={() => s.act('cpn:none')}>{t('cp.none')}</Button></div>
    </Sheet>
  );
}
function PvCard({ v, foot }: { v: PVoucher; foot?: React.ReactNode }) {
  const t = useT(); const s = useStore(); const why = pvWhy(v, s.lines);
  const d = t(v.items ? 'pv.kind.set' : 'pv.kind.cash') + ' · ' + t('pv.sold', { p: v.price }) + ' · ' + (why ? t('pv.missing', { i: v.items!.join('、') }) : (v.items ? t('pv.includes', { i: v.items.join('、') }) : t('pv.noChange')) + ' · ' + t('pv.till', { d: v.till }));
  return <CouponCard off={!!why} value={cs() + v.face} title={<><PlatMark k={v.plat} />{pvName(s, v.id)}</>} lines={[d, <span className="num">{t('pv.code', { c: v.code, n: v.left })}</span>]} right={foot} />;
}
function PVoucherSheet({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore(), tot = useTotals(); const dispatch = s.act;
  const last = s.pvLast, err = last ? PVERR[last] : undefined, v = last ? pvOf(last) : undefined, used = s.pv.find((x) => x.id === last), pvN = s.pv.reduce((a, x) => a + x.n, 0);
  const authKey = (a: string) => (a === '已授权' ? 'plat.auth' : a === '未授权' ? 'plat.noauth' : 'plat.off');
  return (
    <Sheet open={open} onClose={close} title={t('pv.title')} closeLabel={t('common.close')}>
      <div className="flex flex-wrap gap-gap">{PLATS.map((p) => <Filter key={p.key} disabled={p.auth !== '已授权'} pressed={false}><PlatMark k={p.key} />{s.lang === 'en' ? p.en : p.name}<FilterCount n={t(authKey(p.auth))} /></Filter>)}</div>
      <div className="flex items-center gap-2 h-ctl-sm px-px rounded-rb bg-wash text-base text-hint"><Ico name="scan-line" size={16} className="text-sub" />{t('pv.scan')}</div>
      <span className="gt"><span>{t('pv.sim')}</span></span>
      <div className="flex flex-wrap gap-gap">{[['mt100', 'pv.sim.mt'], ['dy116', 'pv.sim.dy'], ['used', 'pv.sim.used'], ['ks', 'pv.sim.ks']].map(([k, l]) => <Button key={k} variant="muted" size="sm" onClick={() => dispatch('pvscan:' + k)}>{t(l)}</Button>)}</div>
      {s.banner ? <Note tone="warn">{t('pv.offline')}</Note>
        : err ? <Note tone="warn"><PlatMark k={err[0]} /><b>{t('pv.err.' + last)}</b> · {t('pv.err.' + last + 'Desc')}</Note>
        : v ? <><PvCard v={v} foot={used ? <StatusTag ent="coupon" k="unused" label={t('pv.addedN', { n: used.n })} form="dot" /> : undefined} />{!used && <><div className="row-between"><span className="help">{t('pv.useN')}</span><Stepper q={s.pvN || 1} onDec={() => dispatch('pvn:-1')} onInc={() => dispatch('pvn:1')} /></div><Button block onClick={() => dispatch('pvadd:' + v.id)}>{t('pv.add', { m: v.face * (s.pvN || 1) })}</Button></>}</>
        : <Empty icon={<Ico name="scan-line" size={44} className="text-success-text" />} title={t('pv.empty')} desc={t('pv.emptyHint')} />}
      {s.pv.length > 0 && <>
        <span className="gt"><span>{t('pv.onOrder', { n: pvN, m: money(tot.pv) })}</span></span>
        {s.pv.map((x) => <PvCard key={x.id} v={pvOf(x.id)!} foot={<Button variant="link" className="hit text-sm" onClick={() => dispatch('pvdel:' + x.id)}>{t('pv.remove')}</Button>} />)}
        {tot.pvOver > 0 && <Note tone="warn">{t('pv.over', { m: money(tot.pvOver) })}</Note>}
      </>}
      <span className="help">{t('pv.rules')}</span>
      <div className="flex gap-gap acts">{s.pv.length > 0 && <Button variant="muted" onClick={() => dispatch('pvdel:all')}>{t('pv.removeAll')}</Button>}<Button block onClick={close}>{t('co.done')}</Button></div>
    </Sheet>
  );
}
function ManualDialog({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore(); const need = s.mval > 20 || s.mval < 0;
  return (
    <Dialog open={open} onClose={close} ariaLabel={t('manual.title')}>
      <DialogTitle>{t('manual.title')}</DialogTitle><DialogDesc>{t('manual.desc')}</DialogDesc>
      <div className="flex flex-wrap gap-gap">{[5, 10, 20, 50].map((v) => <Filter key={v} pressed={s.mval === v} onPressedChange={() => s.act('mval:' + v)}>{t('manual.off', { v })}</Filter>)}<Filter pressed={s.mval === -1} onPressedChange={() => s.act('mval:-1')}>{t('manual.free')}</Filter></div>
      <span className="gt"><span>{t('rp.why')}</span></span><div className="flex flex-wrap gap-gap">{['rp.w1', 'rp.w2', 'rp.w3', 'rp.w4', 'rp.w5'].map((k) => <Filter key={k} pressed={s.mwhy === k} onPressedChange={() => s.act('mwhy:' + k)}>{t(k)}</Filter>)}</div>
      <DialogActs><Button variant="muted" onClick={close}>{t('common.cancel')}</Button>{s.mwhy && s.mval ? <Button onClick={() => s.act(need ? 'ov:x2' : 'manualok')}>{need ? t('manual.apply') : t('manual.ok')}</Button> : <Button disabled>{t('manual.pick')}</Button>}</DialogActs>
    </Dialog>
  );
}
/* 撤销核销：选原因（必填）→ 别人核销的要主管授权码 → 平台撤销成功后券退回顾客，本单变「部分支付」待补收 */
function PvUndoDialog({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore(); const [pin, setPin] = useState(2);
  const [no, idx] = s.pvu.split('|'); const r = s.redeemed[no]?.[+idx]; if (!r) return null;
  const v = pvOf(r[0])!, amt = v.face * r[1], auth = pvUndoWhy(r) === 'auth', why = s.pvuWhy;
  return (
    <Dialog open={open} onClose={close} ariaLabel={t('pvundo.title')}>
      <DialogTitle>{t('pvundo.title')}{auth ? t('pvundo.auth') : ''}</DialogTitle>
      <DialogDesc><PlatMark k={v.plat} />{pvName(s, v.id)} ×{r[1]} · {money(amt)} · {t('pvundo.meta', { t: r[2], who: r[4] })}</DialogDesc>
      <span className="gt"><span>{t('pvundo.why')}</span></span>
      <OptionGroup value={why} onChange={(w) => s.act('pvuwhy:' + w)}>{['pvundo.w1', 'pvundo.w2', 'pvundo.w3', 'pvundo.w4'].map((k) => <Option key={k} value={k} label={t(k)} />)}</OptionGroup>
      {auth && <><span className="help">{t('pvundo.authHelp', { who: r[4] })}</span><Pins n={pin} /><Keypad keys={['1', '2', '3', '4', '5', '6', '7', '8', '9', 'cancel', '0', 'back'].map((k) => ({ k, fn: k === 'cancel' || k === 'back', label: k === 'cancel' ? t('common.cancel') : undefined, aria: k === 'back' ? t('auth.back') : undefined }))} onKey={(k) => (k === 'cancel' ? close() : k === 'back' ? setPin((p) => Math.max(0, p - 1)) : setPin((p) => Math.min(4, p + 1)))} /></>}
      <Note tone="warn">{t('pvundo.note', { m: money(amt) })}</Note>
      <DialogActs><Button variant="muted" onClick={close}>{t('common.cancel')}</Button><Button variant="danger" disabled={!why} onClick={() => s.act('pvundook')}>{t('pvundo.ok')}</Button></DialogActs>
      <span className="help">{t('pvundo.log', { a: auth ? t('pvundo.logAuth') : '' })}</span>
    </Dialog>
  );
}
