// 退货弹窗（方案 §4.2，宽版 760）：找原单 → 选商品 → 原因与金额 → 退款；找不到原单走「无单退货」（主管授权 · 只退现金 / 储值）。
// 可退数量、优惠分摊、退款金额、默认回库存、渠道限制都来自 domain/aftersale.ts；收款方式条、键盘、结果与餐饮共用组件。
import { Fragment } from 'react';
import { useStore } from '@/store';
import { money } from '@/lib/money';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Dialog, DialogActs, DialogDesc, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Steps } from '@/components/ui/steps';
import { Search } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Stepper } from '@/components/ui/stepper';
import { Switch } from '@/components/ui/switch';
import { OptionGroup, Option } from '@/components/ui/radio-cards';
import { PayGroup, PayItem } from '@/components/ui/pay-row';
import { StatusTag } from '@/components/ui/badge';
import { Note } from '@/components/ui/feedback';
import { Keypad, Pins } from '@/components/ui/keypad';
import { Amount, Result } from '@/components/ui/result';
import { WaitBlock, payDesc, payLabel } from '@/components/pos/pay';
import { PAYS } from '@/data/sample';
import { NO_RECEIPT, channelBlock, pickedPieces, refundableQty, type ReturnReason } from '@/domain/aftersale';
import { toast } from 'sonner';
import { item } from './data';
import { confirmExchange, confirmRefund, retAmount, retLines, retNet, salePaid, salePcs, setReason, statusOf, useSales } from './sales';
import { useRT } from './dict';

const REASONS: ReturnReason[] = ['quality', 'unwanted', 'wrong', 'other'];
export function ReturnDialog() {
  const t = useRT(), lang = useStore((s) => s.lang), en = lang === 'en'; const st = useSales(), r = st.ret, set = st.setRet;
  const { lines, disc, sale } = retLines(st); const amt = retAmount(st), pcs = pickedPieces(lines, r.pick), noReceipt = !r.no;
  const close = () => set({ open: false });
  const ex = r.mode === 'exchange';
  const steps = ex ? [t('rt.s1'), t('ex.s2'), t('rt.s3'), t('ex.s4')] : [t('rt.s1'), t('rt.s2'), t('rt.s3'), t('rt.s4')];
  const name = (id: string) => (en ? item(id).en : item(id).n);

  /* ---------- 退款中 / 已完成 ---------- */
  if (r.phase) {
    const done = r.result || { amt, pcs }, restocked = r.restock ? done.pcs : 0;
    return (
      <Dialog open={r.open} onClose={() => r.phase === 'done' && close()} ariaLabel={t('rt.title')}>
        {r.phase === 'refunding' ? <div className="min-h-240px flex"><WaitBlock title={t('rt.refunding')} hint={t('rt.refundingHint', { m: payLabel(r.method, lang) })} devLabel={payLabel(r.method, lang)} dev="online" /></div> : <>
          <Result title={t('rt.done', { m: money(done.amt) })}><span className="help">{t('rt.doneHint', { n: done.pcs, m: payLabel(r.method, lang) })}</span></Result>
          <Note className="flex items-center gap-2"><StatusTag ent="return" k="refunded" form="dot" />{restocked ? t('rt.stockNote', { n: restocked }) : t('rt.lossNote', { n: done.pcs })}{sale && <span className="ml-auto num">{sale.no}</span>}</Note>
          <DialogActs><Button variant="muted" onClick={() => toast(t('rt.print'))}><Ico name="printer" />{t('rt.print')}</Button><Button onClick={close}>{t('rt.finish')}</Button></DialogActs>
        </>}
      </Dialog>
    );
  }

  /* ---------- ① 找原单 ---------- */
  let body: React.ReactNode;
  if (r.step === 1) body = (
    <>
      <Search placeholder={t('rt.find')} />
      <div className="sec mt-1"><span>{t('rt.recent')}</span></div>
      <div className="flex flex-col gap-0.5 max-h-300px overflow-y-auto">
        {st.sales.map((s) => { const status = statusOf(s), why = channelBlock(s.ch) ? t('od.noReturnPlatform') : status === 'returned' ? t('od.noReturnAll') : '';
          return (
            <button key={s.no} type="button" disabled={!!why} onClick={() => set({ no: s.no, method: s.pay[0][0], pick: {}, step: 2 })}
              className={cn('grid grid-cols-[96px_64px_minmax(0,1fr)_96px_80px] gap-2 items-center min-h-$row px-2.5 rounded-rk text-sm text-left hover:bg-wash disabled:(text-placeholder hover:bg-transparent cursor-default)')}>
              <StatusTag ent="order" k={status} form="dot" /><b className="num">{s.no}</b>
              <span className="ellipsis">{why || t('ch.' + s.ch) + (s.member ? ' · ' + s.member : '')}</span><span className="help num">{t('od.pcs', { n: salePcs(s) })} · {s.time}</span><span className="font-600 text-right num">{money(salePaid(s))}</span>
            </button>); })}
      </div>
      {!ex && <button type="button" onClick={() => set({ no: null, method: 'cash', pick: {}, step: 2 })} className="hit flex items-center gap-3 rounded-rk bg-wash py-2.5 px-3 text-left hover:bg-wash-strong">
        <Ico name="receipt-text" size={20} className="flex-none" /><span className="flex flex-col"><b>{t('rt.noReceipt')}</b><small className="help">{t('rt.noReceiptHint')}</small></span><StatusTag ent="approval" k="pending" label={t('rt.auth')} className="ml-auto" />
      </button>}
      {ex && <Note>{t('ex.findHint')}</Note>}
    </>
  );

  /* ---------- ② 选商品 ---------- */
  else if (r.step === 2) {
    const net = retNet(st);
    body = (
      <>
        <div className="row-between"><span className="help">{noReceipt ? t('rt.pickNo') : t('rt.pick')}</span>{sale && <span className="help num">{sale.no} · {t('ch.' + sale.ch)} · {sale.time}</span>}</div>
        {noReceipt && <Search placeholder={t('od.search').split(' / ')[0]} />}
        <div className="flex flex-col rounded-rt bg-wash py-1 px-3">
          {lines.map((l) => { const left = noReceipt ? 99 : refundableQty(l), q = r.pick[l.key] || 0, on = q > 0;
            const toggle = () => set({ pick: { ...r.pick, [l.key]: on ? 0 : l.weighed ? 1 : noReceipt ? 1 : left } });
            const unit = noReceipt ? l.p : l.weighed ? net[l.key] : net[l.key] / l.q;
            return (
              <div key={l.key} className={cn('grid grid-cols-[22px_minmax(0,1fr)_auto_88px] gap-x-3 items-center py-2.5 [&+&]:border-t-2 [&+&]:border-surface', !left && 'opacity-50')}>
                <Checkbox checked={on} disabled={!left} onCheckedChange={toggle} aria-label={name(l.id)} />
                <span className="flex flex-col min-w-0"><b className="font-600 ellipsis">{name(l.id)}{l.sku && <span className="font-400 text-sub"> · {en ? l.sku[1] : l.sku[0]}</span>}</b>
                  <small className="help num">{l.weighed ? `${l.w!.toFixed(3)} kg · ${t('rt.wholeLine')}` : (noReceipt ? '' : t('rt.left', { n: left }) + ' · ') + t('line.each', { p: unit.toFixed(2) })}{!noReceipt && (l.returned || 0) > 0 ? ' · ' + t('od.returnedN', { n: l.weighed ? '' : l.returned! }) : ''}</small></span>
                <span>{l.weighed ? <span className="help">{on ? '1' : '—'}</span> : <Stepper q={q} onDec={() => set({ pick: { ...r.pick, [l.key]: Math.max(0, q - 1) } })} onInc={() => set({ pick: { ...r.pick, [l.key]: Math.min(left, q + 1) } })} />}</span>
                <span className={cn('text-right font-600 num', !on && 'text-placeholder')}>{money(on ? (l.weighed ? unit : unit * q) : 0)}</span>
              </div>); })}
        </div>
        <Amount sm label={pcs ? t('rt.picked', { n: pcs, m: money(amt) }) : t('rt.none')} value={money(amt)} />
      </>
    );
  }

  /* ---------- ③ 原因与金额 ---------- */
  else if (r.step === 3) {
    const net = retNet(st), picked = lines.filter((l) => r.pick[l.key]);
    body = (
      <>
        <span className="gt"><span>{t('rt.reason')}</span></span>
        <OptionGroup value={r.why} onChange={(v) => setReason(v as ReturnReason)} ariaLabel={t('rt.reason')}>{REASONS.map((w) => <Option key={w} value={w} label={t('rt.r.' + w)} />)}</OptionGroup>
        <label className="flex items-center gap-3 rounded-rk bg-wash py-2.5 px-3 mt-1">
          <span className="flex flex-col flex-1"><b>{t('rt.restock')}</b><small className="help">{r.restock ? t('rt.restockOn') : t('rt.restockOff')}</small></span>
          <Switch checked={r.restock} onCheckedChange={(v) => set({ restock: v })} aria-label={t('rt.restock')} />
        </label>
        <div className="rounded-rk bg-wash py-pad px-px mt-1"><Amount label={ex ? t('ex.credit') : t('rt.amount')} value={money(amt)} /></div>
        {ex && <Note className="flex items-center gap-2"><Ico name="arrow-left-right" size={16} />{t('ex.creditHint', { m: money(amt) })}</Note>}
        {disc > 0 && <>
          <span className="gt mt-1"><span>{t('rt.showAlloc')}</span><span>{t('rt.alloc', { m: money(disc) })}</span></span>
          <div className="grid grid-cols-[minmax(0,1fr)_auto_auto] gap-x-4 gap-y-1 text-sm">{picked.map((l) => (
            <Fragment key={l.key}><span className="ellipsis">{name(l.id)} × {l.weighed ? '1' : r.pick[l.key]}</span><span className="help num">{t('rt.allocRow', { a: money(l.amt), b: money(net[l.key]) })}</span><b className="num text-right">{money(l.weighed ? net[l.key] : (net[l.key] / l.q) * r.pick[l.key])}</b></Fragment>))}</div>
        </>}
      </>
    );
  }

  /* ---------- ④ 退款 ---------- */
  else {
    const allowed = noReceipt ? [...NO_RECEIPT.methods] as string[] : sale!.pay.map((p) => p[0]);
    const pinKeys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'clr', '0', 'back'].map((k) => ({ k, fn: k === 'clr' || k === 'back', label: k === 'clr' ? (en ? 'Clear' : '清空') : undefined }));
    body = (
      <>
        <span className="gt"><span>{t('rt.method')}{noReceipt ? '' : ' · ' + t('rt.original')}</span></span>
        <PayGroup value={r.method} onChange={(v) => set({ method: v })} ariaLabel={t('rt.method')}>
          {PAYS.filter((p) => p.key !== 'mix').map((p) => { const ok = allowed.includes(p.key);
            return <PayItem key={p.key} value={p.key} color={p.color} ch={p.ch} name={payLabel(p.key, lang)} desc={ok ? (sale ? money(sale.pay.find((x) => x[0] === p.key)![1]) : payDesc(p.key, lang)) : noReceipt ? t('rt.nrWhy') : t('rt.originalWhy')} disabled={!ok} />; })}
        </PayGroup>
        <div className={cn('grid gap-[calc(var(--gap)*2)] mt-1', noReceipt ? 'grid-cols-2' : 'grid-cols-1')}>
          <div className="flex flex-col gap-gap">
            <div className="rounded-rk bg-wash py-pad px-px"><Amount label={t('rt.amount')} value={money(amt)} /></div>
            <span className="help">{t('rt.doneHint', { n: pcs, m: payLabel(r.method, lang) })} · {t('rt.r.' + (r.why || 'other'))} · {r.restock ? t('rt.restockOn') : t('rt.restockOff')}</span>
          </div>
          {noReceipt && <div className="flex flex-col gap-1.5 items-center"><b>{t('rt.auth')}</b><small className="help">{t('rt.authHint')}</small><Pins n={r.pin} /><Keypad keys={pinKeys} onKey={(k) => set({ pin: k === 'clr' ? 0 : k === 'back' ? Math.max(0, r.pin - 1) : Math.min(4, r.pin + 1) })} /></div>}
        </div>
      </>
    );
  }

  const canNext = r.step === 2 ? pcs > 0 : r.step === 3 ? !!r.why : true;
  const authOk = !noReceipt || r.pin >= 4;
  return (
    <Dialog wide open={r.open} onClose={close} ariaLabel={t('rt.title')}>
      <div className="row-between"><DialogTitle>{ex ? t('ex.title') : t('rt.title')}{noReceipt && r.step > 1 ? ' · ' + t('rt.noReceipt') : ''}</DialogTitle><Button variant="muted" size="icon-sm" aria-label={en ? 'Close' : '关闭'} onClick={close}><Ico name="x" size={16} /></Button></div>
      <Steps items={steps} current={r.step} />
      {r.step === 1 && <DialogDesc>{t('rt.find')}</DialogDesc>}
      <div className="flex flex-col gap-gap min-h-320px">{body}</div>
      {r.step > 1 && <DialogActs>
        <Button variant="muted" onClick={() => set({ step: (r.step - 1) as 1 | 2 | 3 })}>{t('rt.prev')}</Button>
        {ex && r.step === 3 ? <Button disabled={!canNext} onClick={confirmExchange}>{t('ex.go')}<Ico name="arrow-right" /></Button>
          : r.step < 4 ? <Button disabled={!canNext} onClick={() => set({ step: (r.step + 1) as 2 | 3 | 4 })}>{t('rt.next')}</Button>
          : <Button variant="danger-solid" disabled={!authOk || !amt} onClick={confirmRefund}>{t('rt.confirm', { m: money(amt) })}</Button>}
      </DialogActs>}
    </Dialog>
  );
}
