// 会员与班次的浮层：新建会员 mnew · 充值 recharge · 标签 mtag / mtagnew · 充值方案 plannew · 确认交班 confirm-shift · 开班 s1
import { useStore } from '@/store';
import { MEMBER } from '@/data/sample';
import { useT } from '@/i18n';
import { Dialog, DialogTitle, DialogDesc, DialogActs } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Filter } from '@/components/ui/toggle';
import { Seg } from '@/components/ui/toggle-group';
import { OptionGroup, Option } from '@/components/ui/radio-cards';
import { Cells, Cell } from '@/components/ui/description-list';
import { StatusTag } from '@/components/ui/badge';
import { Note } from '@/components/ui/feedback';
import { Result, Spinner, Amount } from '@/components/ui/result';
import { cur } from '@/lib/money';

const useClose = () => { const dispatch = useStore((s) => s.act); return () => dispatch('ov:none'); };
const Gt = ({ children }: { children: React.ReactNode }) => <span className="gt"><span>{children}</span></span>;
export function MemberOverlays() {
  const ov = useStore((s) => s.ov);
  return <><NewMember open={ov === 'mnew'} /><Recharge open={ov === 'recharge'} /><TagDialog open={ov === 'mtag' || ov === 'mtagnew'} isNew={ov === 'mtagnew'} /><PlanNew open={ov === 'plannew'} /><ConfirmShift open={ov === 'confirm-shift'} /><OpenShift open={ov === 's1'} /></>;
}
function NewMember({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore();
  return (
    <Dialog open={open} onClose={close} ariaLabel={t('mnew.title')}>
      <DialogTitle>{t('mnew.title')}</DialogTitle><DialogDesc>{t('mnew.desc')}</DialogDesc>
      <Gt>{t('mnew.phone')}</Gt><Input defaultValue="138 0000 7714" /><Gt>{t('mnew.name')}</Gt><Input placeholder={t('mnew.namePh')} /><Gt>{t('mnew.birthday')}</Gt><Input placeholder={t('mnew.bdPh')} />
      <Gt>{t('mnew.tags')}</Gt><div className="flex flex-wrap gap-gap">{s.mtags.map((x) => <Filter key={x[0]} pressed={false}>{x[0]}</Filter>)}</div>
      <DialogActs><Button variant="muted" onClick={() => s.act('msave:only')}>{t('mnew.saveOnly')}</Button><Button onClick={() => s.act('msave:sv')}>{t('mnew.saveSv')}</Button></DialogActs>
    </Dialog>
  );
}
function Recharge({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore(); const pl = s.plans.filter((p) => p.on), cur = pl[s.plan] || pl[0], rs = s.rcState;
  return (
    <Dialog open={open} onClose={rs === 'wait' ? () => {} : close} wide ariaLabel={t('rc.title', { n: MEMBER.name, p: s.members[0][1] })}>
      <DialogTitle>{t('rc.title', { n: MEMBER.name, p: s.members[0][1] })}</DialogTitle>
      {rs ? (
        <>
          {rs === 'wait' ? <div className="flex flex-col items-center gap-1.5 text-center"><Spinner /><span className="text-lg font-600">{t('rc.processing')}</span><span className="help">{t('rc.processingHint')}</span></div>
            : <Result title={t('rc.credited', { t: cur.pay + cur.gift })}><span className="help">{t('rc.creditedHint', { p: cur.pay, g: cur.gift, b: s.memberBal.toFixed(2) })}</span></Result>}
          {rs === 'done' && <DialogActs><Button variant="muted" onClick={() => s.act('toast:rc.printed')}>{t('rc.print')}</Button><Button onClick={() => s.act('rcdone')}>{t('co.done')}</Button></DialogActs>}
        </>
      ) : (
        <>
          <Gt>{t('rc.pick')}</Gt>
          <OptionGroup value={String(s.plan)} onChange={(v) => s.act('plan_s:' + v)} className="grid grid-cols-[repeat(auto-fill,minmax(180px,1fr))]">{pl.map((p, i) => <Option key={p.n} value={String(i)} label={<b className="text-md">{p.n}</b>} sub={t('rc.credit', { t: p.pay + p.gift })} className="items-start py-3 px-3.5 rounded-rt" />)}</OptionGroup>
          <Gt>{t('rc.method')}</Gt><Seg value={String(s.rcm)} onChange={(v) => s.act('rcm:' + v)} items={['pay.cash', 'pay.wx', 'pay.ali', 'pay.card'].map((k, i) => ({ v: String(i), label: t(k) }))} />
          <span className="help">{t('rc.help')}</span>
          <DialogActs><Button variant="muted" onClick={close}>{t('common.cancel')}</Button><Button onClick={() => s.act('rcgo')}>{t('rc.ok', { p: cur.pay })}</Button></DialogActs>
        </>
      )}
    </Dialog>
  );
}
function TagDialog({ open, isNew }: { open: boolean; isNew: boolean }) {
  const t = useT(), close = useClose(); const s = useStore(); const m = s.members[s.msel] || s.members[0];
  return (
    <Dialog open={open} onClose={close} ariaLabel={isNew ? t('mtag.new') : t('mtag.edit', { n: m[0] })}>
      <DialogTitle>{isNew ? t('mtag.new') : t('mtag.edit', { n: m[0] })}</DialogTitle>
      {isNew ? <><Gt>{t('mtag.name')}</Gt><Input defaultValue="爱喝茶" /><span className="help">{t('mtag.help')}</span></>
        : <><div className="flex flex-wrap gap-gap">{s.mtags.map((x) => <Filter key={x[0]} pressed={m[3].includes(x[0])} onPressedChange={() => s.act('mtog:' + x[0])}>{x[0]}</Filter>)}</div><span className="help">{t('mtag.max')}</span></>}
      <DialogActs><Button variant="muted" onClick={close}>{t('common.cancel')}</Button><Button onClick={() => s.act(isNew ? 'mtagadd' : 'ov:none')}>{t('note.save')}</Button></DialogActs>
    </Dialog>
  );
}
function PlanNew({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore();
  return (
    <Dialog open={open} onClose={close} ariaLabel={t('plan.title')}>
      <DialogTitle>{t('plan.title')}</DialogTitle>
      <Gt>{t('plan.pay')}</Gt><Input num defaultValue={cur("¥ 800.00")} /><Gt>{t('plan.gift')}</Gt><Input num defaultValue={cur("¥ 100.00")} />
      <Amount sm label={t('plan.total')} value={cur("¥900.00")} /><span className="help">{t('plan.help')}</span>
      <DialogActs><Button variant="muted" onClick={close}>{t('common.cancel')}</Button><Button onClick={() => s.act('plansave')}>{t('plan.save')}</Button></DialogActs>
    </Dialog>
  );
}
function ConfirmShift({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore();
  return <Dialog open={open} onClose={close} ariaLabel={t('sh.confirmTitle')}><DialogTitle>{t('sh.confirmTitle')}</DialogTitle><DialogDesc>{t('sh.confirmDesc')}</DialogDesc><DialogActs><Button variant="muted" onClick={close}>{t('sh.recheck')}</Button><Button variant="danger-solid" onClick={() => s.act('toast:sh.handedOver')}>{t('sh.handover')}</Button></DialogActs></Dialog>;
}
function OpenShift({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore();
  return (
    <Dialog open={open} onClose={close} className="max-w-640px" ariaLabel={t('sh.open')}>
      <DialogTitle>{t('sh.open')}</DialogTitle>
      <Cells><Cell k={t('sh.register')} v={t('sh.register1')} go /><Cell k={t('sh.cashier')} v="李敏" /></Cells>
      <Gt>{t('sh.float')}</Gt><Input num defaultValue={cur("¥ 500.00")} />
      <Gt>{t('sh.devices')}</Gt><Cells><Cell k={<span className="text-ink">{t('sh.frontPrinter')}</span>}><StatusTag ent="device" k="online" label={t('sh.ok')} /></Cell><Cell k={<span className="text-ink">{t('sh.hotPrinter')}</span>}><StatusTag ent="device" k="no_paper" /></Cell><Cell k={<span className="text-ink">{t('sh.network')}</span>}><StatusTag ent="device" k="online" label={t('sh.ok')} /></Cell><Cell k={<span className="text-ink">{t('sh.drawerK')}</span>}><StatusTag ent="device" k="online" label={t('sh.ok')} /></Cell></Cells>
      <Note tone="warn">{t('sh.noPaperNote')}</Note>
      <DialogActs><Button size="lg" onClick={() => s.act('toast:sh.opened')}>{t('sh.open')}</Button></DialogActs>
    </Dialog>
  );
}
