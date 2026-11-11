// 浮层分发：S.ov 决定开哪个（和原型 OVL[键] 一一对应）。第 1 步：规格 x1 · 授权 x2 · 选桌 x3 · 清台提示 confirm · 挂单 · 拼桌 · 调价 · 备注 · 侧滑面板
import { useStore } from '@/store';
import { useT } from '@/i18n';
import { dish } from '@/data/sample';
import { money } from '@/lib/money';
import { rpPrice, rpNeedsAuth } from '@/store/actions';
import { Dialog, DialogTitle, DialogDesc, DialogActs } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { OptionGroup, Option, OptionMulti } from '@/components/ui/radio-cards';
import { Filter } from '@/components/ui/toggle';
import { Seg } from '@/components/ui/toggle-group';
import { Stepper } from '@/components/ui/stepper';
import { Textarea } from '@/components/ui/input';
import { Keypad, Pins } from '@/components/ui/keypad';
import { Cells, Cell } from '@/components/ui/description-list';
import { Note } from '@/components/ui/feedback';
import { StatusTile } from '@/components/ui/status-tile';
import { useLang } from '@/i18n';
import { SheetPanel } from './SheetPanel';
import { DeviceAddSheet } from './DeviceAdd';
import { PlatformAddSheet } from './PlatformAdd';
import { CheckoutOverlays } from './checkout';
import { MemberOverlays } from './members';
import { useState } from 'react';

export function Overlays() {
  const ov = useStore((s) => s.ov);
  return (
    <>
      <SpecDialog open={ov === 'x1'} /><AuthDialog open={ov === 'x2'} /><PickTableDialog open={ov === 'x3'} /><ConfirmDialog open={ov === 'confirm'} />
      <HoldDialog open={ov === 'hold'} /><ShareDialog open={ov === 'share'} /><RepriceDialog open={ov === 'reprice'} /><NoteDialog open={ov === 'note'} />
      <SheetPanel open={ov === 'sheet'} />
      <DeviceAddSheet open={ov === 'devadd'} />
      <PlatformAddSheet open={ov === 'platadd'} />
      <CheckoutOverlays />
      <MemberOverlays />
    </>
  );
}
const useClose = () => { const dispatch = useStore((s) => s.act); return () => dispatch('ov:none'); };
const Gt = ({ children }: { children: React.ReactNode }) => <span className="gt"><span>{children}</span></span>;
const Row = ({ children }: { children: React.ReactNode }) => <div className="flex flex-wrap gap-gap items-center">{children}</div>;

function SpecDialog({ open }: { open: boolean }) {
  const t = useT(), lang = useLang(), close = useClose(); const s = useStore(); const d = dish(s.spec || '宫保鸡丁');
  const [size, setSize] = useState('large'), [spice, setSpice] = useState('mild'), [add, setAdd] = useState<string[]>([]), [notes, setNotes] = useState(['spec.lessOil']);
  const tog = (arr: string[], set: (a: string[]) => void, k: string) => set(arr.includes(k) ? arr.filter((x) => x !== k) : [...arr, k]);
  return (
    <Dialog open={open} onClose={close} ariaLabel={lang === 'en' ? d.en : d.n}>
      <div className="row-between"><DialogTitle>{lang === 'en' ? d.en : d.n}</DialogTitle><b className="num">¥{d.p}</b></div>
      <Gt>{t('spec.size')}</Gt><OptionGroup value={size} onChange={setSize}><Option value="large" label={t('spec.large')} sub={'¥' + d.p} /><Option value="small" label={t('spec.small')} sub={'¥' + Math.round(d.p * 0.7)} /></OptionGroup>
      <Gt>{t('spec.spice')}</Gt><OptionGroup value={spice} onChange={setSpice}>{['none', 'mild', 'medium', 'hot'].map((k) => <Option key={k} value={k} label={t('spec.' + k)} />)}</OptionGroup>
      <Gt>{t('spec.addons')}</Gt><Row><OptionMulti on={add.includes('peanut')} onToggle={() => tog(add, setAdd, 'peanut')} label={t('spec.peanut')} sub="+¥3" /><OptionMulti on={add.includes('chicken')} onToggle={() => tog(add, setAdd, 'chicken')} label={t('spec.chicken')} sub="+¥12" /></Row>
      <Gt>{t('spec.notes')}</Gt><Row>{['spec.lessOil', 'spec.lessSalt', 'spec.noScallion'].map((k) => <Filter key={k} pressed={notes.includes(k)} onPressedChange={() => tog(notes, setNotes, k)}>{t(k)}</Filter>)}</Row>
      <DialogActs className="[&>button]:flex-1 [&>span]:flex-none"><Stepper q={1} size="md" /><Button variant="muted" onClick={close}>{t('common.cancel')}</Button><Button onClick={() => s.act('addspec')}>{t('spec.add', { p: d.p })}</Button></DialogActs>
    </Dialog>
  );
}
function AuthDialog({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const dispatch = useStore((s) => s.act); const [why, setWhy] = useState('r1'); const [pin, setPin] = useState(2);
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'cancel', '0', 'back'].map((k) => ({ k, fn: k === 'cancel' || k === 'back', label: k === 'cancel' ? t('common.cancel') : undefined, aria: k === 'back' ? t('auth.back') : undefined }));
  return (
    <Dialog open={open} onClose={close} ariaLabel={t('auth.title')}>
      <DialogTitle>{t('auth.title')}</DialogTitle><DialogDesc>{t('auth.item')}</DialogDesc>
      <OptionGroup value={why} onChange={setWhy}>{['r1', 'r2', 'r3', 'r4'].map((k) => <Option key={k} value={k} label={t('auth.' + k)} />)}</OptionGroup>
      <Pins n={pin} /><Keypad keys={keys} onKey={(k) => (k === 'cancel' ? close() : k === 'back' ? setPin((p) => Math.max(0, p - 1)) : setPin((p) => Math.min(4, p + 1)))} />
      <DialogActs><Button variant="muted" onClick={close}>{t('common.cancel')}</Button><Button onClick={() => dispatch('void')}>{t('auth.ok')}</Button></DialogActs><span className="help">{t('auth.help')}</span>
    </Dialog>
  );
}
function PickTableDialog({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore(); const [mode, setMode] = useState('move'); const [target, setTarget] = useState('');
  const free = s.tables.filter((x) => x.st === 'free'); const cur = target || free[1]?.id;
  return (
    <Dialog open={open} onClose={close} wide ariaLabel={t('tables.pick.title')}>
      <DialogTitle>{t('tables.pick.title')}</DialogTitle>
      <Seg value={mode} onChange={setMode} items={[{ v: 'move', label: t('tables.move') }, { v: 'merge', label: t('tables.merge') }, { v: 'transfer', label: t('tables.pick.transfer') }, { v: 'combine', label: t('tables.pick.combine') }]} />
      <span className="help">{t('tables.pick.help')}</span>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(124px,1fr))] gap-gap content-start">
        {free.map((x) => <StatusTile key={x.id} quiet current={x.id === cur} onClick={() => setTarget(x.id)} className="min-h-[calc(var(--tile)+8px)] py-2.5 px-3 gap-0.5"><b className="text-lg num">{x.id}</b><small className="text-xs text-sub">{t('tables.pick.freeSeats', { n: x.seats })}</small></StatusTile>)}
      </div>
      <DialogActs><Button variant="muted" onClick={close}>{t('common.cancel')}</Button><Button onClick={() => s.act('moved:' + cur)}>{t('tables.pick.moveTo', { t: cur })}</Button></DialogActs>
    </Dialog>
  );
}
function ConfirmDialog({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const dispatch = useStore((s) => s.act);
  return <Dialog open={open} onClose={close} ariaLabel={t('tables.confirm.title')}><DialogTitle>{t('tables.confirm.title')}</DialogTitle><DialogDesc>{t('tables.confirm.desc')}</DialogDesc><DialogActs><Button variant="muted" onClick={close}>{t('tables.confirm.ok')}</Button><Button onClick={() => dispatch('gopay')}>{t('tables.gopay')}</Button></DialogActs></Dialog>;
}
function HoldDialog({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore();
  return (
    <Dialog open={open} onClose={close} ariaLabel={t('hold.title', { t: s.table })}>
      <DialogTitle>{t('hold.title', { t: s.table })}</DialogTitle><DialogDesc>{t('hold.desc')}</DialogDesc>
      <Gt>{t('hold.why')}</Gt><Row>{['hold.w1', 'hold.w2', 'hold.w3', 'hold.w4'].map((k) => <Filter key={k} pressed={s.holdWhy === k} onPressedChange={() => s.act('holdwhy:' + k)}>{t(k)}</Filter>)}</Row>
      <DialogActs><Button variant="muted" onClick={close}>{t('common.cancel')}</Button><Button onClick={() => s.act('holdok')}>{t('hold.ok')}</Button></DialogActs>
    </Dialog>
  );
}
function ShareDialog({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore();
  return (
    <Dialog open={open} onClose={close} ariaLabel={t('share.title', { t: s.table })}>
      <DialogTitle>{t('share.title', { t: s.table })}</DialogTitle><DialogDesc>{t('share.desc')}</DialogDesc>
      <Cells><Cell k={t('share.existing')} v={t('share.existingV', { t: s.table })} /><Cell k={t('share.newN')}><Stepper q={s.shareN} onDec={() => s.act('sn:-')} onInc={() => s.act('sn:+')} /></Cell><Cell k={t('share.newNo')} v={s.table + '-2 · #1034'} /></Cells>
      <DialogActs><Button variant="muted" onClick={close}>{t('common.cancel')}</Button><Button onClick={() => s.act('shareok')}>{t('share.ok')}</Button></DialogActs>
    </Dialog>
  );
}
function RepriceDialog({ open }: { open: boolean }) {
  const t = useT(), lang = useLang(), close = useClose(); const s = useStore();
  const rl = s.lines[s.rpLine] || s.lines[0]; if (!rl) return null;
  const np = rpPrice(s, rl.p), need = rpNeedsAuth(s, rl.p);
  const vals = ({ price: [rl.p - 2, rl.p - 5, rl.p - 10].filter((v) => v > 0), disc: [9.5, 9, 8.5, 8, 7], off: [2, 5, 10, 20, 30] } as Record<string, number[]>)[s.rpMode || 'price'];
  const nm = (n: string) => (lang === 'en' ? dish(n).en : n);
  return (
    <Dialog open={open} onClose={close} wide ariaLabel={t('rp.title')}>
      <DialogTitle>{t('rp.title')}</DialogTitle>
      <Gt>{t('rp.pick')}</Gt><OptionGroup value={String(s.rpLine)} onChange={(v) => s.act('rpl:' + v)}>{s.lines.map((l, i) => l.st === 'void' ? null : <Option key={i} value={String(i)} label={nm(l.n)} sub={money(l.p) + ' × ' + l.q} />)}</OptionGroup>
      <div className="row-between"><Seg value={s.rpMode} onChange={(v) => s.act('rpm:' + v)} items={[{ v: 'price', label: t('rp.price') }, { v: 'disc', label: t('rp.disc') }, { v: 'off', label: t('rp.off') }]} /><span className="help">{money(rl.p * rl.q)} → <b className="num text-ink">{money(np * rl.q)}</b></span></div>
      <Row>{vals.map((v) => <Filter key={v} pressed={s.rpVal === v} onPressedChange={() => s.act('rpv:' + v)}>{s.rpMode === 'disc' ? t('rp.discV', { v }) : s.rpMode === 'off' ? t('rp.offV', { v }) : '¥' + v}</Filter>)}</Row>
      <Gt>{t('rp.why')}</Gt><Row>{['rp.w1', 'rp.w2', 'rp.w3', 'rp.w4', 'rp.w5'].map((k) => <Filter key={k} pressed={s.rpWhy === k} onPressedChange={() => s.act('rpw:' + k)}>{t(k)}</Filter>)}</Row>
      {need && <Note tone="warn">{t('rp.needAuth')}</Note>}
      <DialogActs><Button variant="muted" onClick={close}>{t('common.cancel')}</Button>{s.rpWhy ? <Button onClick={() => s.act(need ? 'ov:x2' : 'rpok')}>{need ? t('rp.apply') : t('rp.ok')}</Button> : <Button disabled>{t('rp.pickWhy')}</Button>}</DialogActs>
    </Dialog>
  );
}
function NoteDialog({ open }: { open: boolean }) {
  const t = useT(), close = useClose(); const s = useStore(); const d0 = s.noteDraft || s.note;
  return (
    <Dialog open={open} onClose={close} ariaLabel={t('note.title')}>
      <DialogTitle>{t('note.title')}</DialogTitle>
      <Gt>{t('note.common')}</Gt><Row>{['note.n1', 'note.n2', 'note.n3', 'note.n4', 'note.n5'].map((k) => <Filter key={k} pressed={d0.includes(k)} onPressedChange={() => s.act('ntag:' + k)}>{t(k)}</Filter>)}</Row>
      <Gt>{t('note.other')}</Gt><Textarea placeholder={t('note.ph')} /><span className="help">{t('note.help')}</span>
      <DialogActs><Button variant="muted" onClick={close}>{t('common.cancel')}</Button><Button onClick={() => s.act('notesave')}>{t('note.save')}</Button></DialogActs>
    </Dialog>
  );
}
