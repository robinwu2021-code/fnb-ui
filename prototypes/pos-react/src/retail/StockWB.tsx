// 库存作业工作台（W1，零售方案 §5.2）：左 作业类型 + 预警 · 中 单据卡片 · 右 当前单据（扫码累加 → 过账）。
// 入库、报损直接过账；盘点先提交、主管审批后实盘覆盖账面；调拨未实现。规则在 domain/stock.ts，数据在 retail/stock.ts。
import { useStore } from '@/store';
import { money } from '@/lib/money';
import { Ico } from '@/lib/icon';
import { Rail } from '@/layout/rail';
import { Panel, Block, DueFoot, Acts } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { Search } from '@/components/ui/input';
import { Filter, FilterCount } from '@/components/ui/toggle';
import { Seg } from '@/components/ui/toggle-group';
import { StatusTag, Chip } from '@/components/ui/badge';
import { StatusTile } from '@/components/ui/status-tile';
import { Stepper } from '@/components/ui/stepper';
import { Empty, Note } from '@/components/ui/feedback';
import { LineRow } from '@/components/pos/line-row';
import { stGroup } from '@/lib/states';
import { findByCode } from '@/domain/catalog';
import { toast } from 'sonner';
import { ITEMS, item } from './data';
import { addToDoc, costOf, docCost, docDiff, docQty, isLow, isNear, newDoc, postDoc, removeLine, setQty, setWhy, unitOf, useStock, voidDoc, type DocKind, type LossWhy, type StockDoc } from './stock';
import { rt, useRT } from './dict';

const ENT: Record<DocKind, string> = { receive: 'receipt', count: 'count', loss: 'loss', transfer: 'feature' };
const OPEN = ['draft', 'counting', 'pending'];
const editable = (d: StockDoc) => d.st === 'draft' || d.st === 'counting';
const useEn = () => useStore((s) => s.lang) === 'en';
const nm = (id: string, en: boolean) => (en ? item(id).en : item(id).n);
const qtyLabel = (id: string, q: number) => (item(id).weigh ? q.toFixed(3) + ' kg' : String(q));

export function StockWB() {
  const t = useRT(), en = useEn(); const s = useStock();
  const low = ITEMS.filter((it) => isLow(it.id, s.book)).length, near = ITEMS.filter((it) => isNear(it.id)).length;
  const openN = (k: DocKind) => s.docs.filter((d) => d.kind === k && OPEN.includes(d.st)).length;
  const nav = [
    { v: 'receive', icon: 'package-plus', label: t('sk.receive'), em: openN('receive') || undefined },
    { v: 'count', icon: 'clipboard-list', label: t('sk.count'), em: openN('count') || undefined },
    { v: 'loss', icon: 'trash-2', label: t('sk.loss'), em: openN('loss') || undefined },
    { v: 'transfer', icon: 'arrow-left-right', label: t('sk.transfer') },
    { cap: t('sk.alerts') },
    { v: 'alert-low', icon: 'triangle-alert', label: t('sk.low', { n: low }) },
    { v: 'alert-near', icon: 'calendar-clock', label: t('sk.near', { n: near }) },
  ];
  const pick = (v: string) => {
    if (v === 'alert-low') { s.set({ kind: 'receive' }); return; }
    if (v === 'alert-near') { s.set({ kind: 'loss' }); return; }
    const first = s.docs.find((d) => d.kind === v); s.set({ kind: v as DocKind, sel: first?.no || '' });
  };
  const docs = s.docs.filter((d) => d.kind === s.kind && (s.filter === 'all' || (s.filter === 'open' ? OPEN.includes(d.st) : !OPEN.includes(d.st))));
  const cur = s.docs.find((d) => d.no === s.sel && d.kind === s.kind) || docs[0];

  if (s.kind === 'transfer') return (
    <>
      <Rail items={nav} cur={s.kind} onPick={pick} ariaLabel={t('tab.stock')} />
      <Panel area="content" bodyClass="flex flex-col"><Empty fill icon={<Ico name="arrow-left-right" size={44} className="text-sub" />} title={t('sk.transfer')} desc={<><StatusTag ent="feature" k="todo" /><br />{t('sk.transferTodo')}</>} /></Panel>
      <Panel area="detail"><span /></Panel>
    </>
  );
  return (
    <>
      <Rail items={nav} cur={s.kind} onPick={pick} ariaLabel={t('tab.stock')} />
      <Panel area="content" head={<>
        <div className="row-between"><b className="h-title">{t('sk.' + s.kind)}</b><Button variant="soft" size="sm" onClick={() => newDoc(s.kind)}><Ico name="plus" />{t('sk.new.' + s.kind)}</Button></div>
        <div className="flex flex-wrap gap-gap">{(['all', 'open', 'done'] as const).map((f) => { const n = s.docs.filter((d) => d.kind === s.kind && (f === 'all' || (f === 'open' ? OPEN.includes(d.st) : !OPEN.includes(d.st)))).length;
          return <Filter key={f} pressed={s.filter === f} onPressedChange={() => s.set({ filter: f })}>{t('sk.f.' + f)}<FilterCount n={n} alert={f === 'open' && n > 0} /></Filter>; })}</div>
      </>}>
        <div className="sec"><span>{t('sk.docs', { n: docs.length })}</span></div>
        {!docs.length ? <Empty fill title={t('sk.new.' + s.kind)} /> : <div className="grid grid-cols-[repeat(auto-fill,minmax(210px,1fr))] gap-gap">
          {docs.map((d) => { const g = stGroup(ENT[d.kind], d.st);
            return (
              <StatusTile key={d.no} group={g} alert={d.st === 'pending'} quiet={g === 'closed'} current={cur?.no === d.no} onClick={() => s.set({ sel: d.no })} className="py-3 px-3.5 gap-0.5 min-h-112px">
                <span className="flex items-center justify-between gap-2"><b className="num">{d.no}</b><StatusTag ent={ENT[d.kind]} k={d.st} form="dot" /></span>
                <span className="help ellipsis">{d.supplier || (d.scope ? t('sk.scope') + ' · ' + d.scope : d.ref ? t('sk.ref', { no: d.ref }) : '')}</span>
                <span className="help">{t('sk.by', { w: d.who, t: d.time })}</span>
                <span className="a mt-auto pt-1.5 flex items-baseline justify-between gap-2"><span className="text-sm">{t('sk.lines', { n: d.lines.length })}</span>{d.kind !== 'count' ? <b className="text-lg font-700 num">{money(docCost(d))}</b> : d.diff ? <span className="text-sm num">{t('sk.gainLoss', { g: d.diff.gain, l: d.diff.loss })}</span> : null}</span>
              </StatusTile>); })}
        </div>}
      </Panel>
      {cur ? <DocPanel d={cur} en={en} /> : <Panel area="detail" bodyClass="flex flex-col"><Empty fill title={t('sk.new.' + s.kind)} /></Panel>}
    </>
  );
}

function DocPanel({ d, en }: { d: StockDoc; en: boolean }) {
  const t = useRT(); const book = useStock((s) => s.book); const edit = editable(d);
  const step = (id: string) => (item(id).weigh ? 0.5 : 1);
  const has = new Set(d.lines.map((l) => l.id));
  const suggest = !edit ? [] : ITEMS.filter((it) => !has.has(it.id) && (d.kind === 'receive' ? isLow(it.id, book) : d.kind === 'loss' ? isNear(it.id) : true)).slice(0, d.kind === 'count' ? 8 : 6);
  const df = d.kind === 'count' ? docDiff(d, book) : null;
  const onScan = (raw: string) => { const it = findByCode(ITEMS, raw); if (!it) { toast(rt('scan.unknown', { c: raw })); return; } addToDoc(d.no, it.id); };
  const head = (
    <>
      <div className="row-between"><b className="h-title num">{d.no}</b><StatusTag ent={ENT[d.kind]} k={d.st} /></div>
      <span className="help">{[d.supplier && t('sk.supplier') + ' · ' + d.supplier, d.scope && t('sk.scope') + ' · ' + d.scope, d.ref && t('sk.ref', { no: d.ref }), t('sk.by', { w: d.who, t: d.time })].filter(Boolean).join(' · ')}</span>
      {edit && <label className="flex items-center gap-2 h-ctl px-px rounded-rk bg-wash"><Ico name="scan-barcode" size={18} className="flex-none" />
        <input placeholder={t('sk.scan')} className="flex-1 min-w-0 bg-transparent" onKeyDown={(e) => { const v = e.currentTarget.value.trim(); if (e.key === 'Enter' && v) { onScan(v); e.currentTarget.value = ''; } }} /></label>}
      {suggest.length > 0 && <div className="flex flex-col gap-1"><span className="help">{t('sk.suggest.' + d.kind)}</span><div className="flex flex-wrap gap-1">{suggest.map((it) => <Chip key={it.id} onClick={() => addToDoc(d.no, it.id, d.kind === 'receive' ? 12 : 1)}><Ico name="plus" size={12} />{nm(it.id, en)}</Chip>)}</div></div>}
    </>
  );
  const lines = d.lines.map((l) => {
    const b = (d.kind === 'count' && d.snap ? d.snap[l.id] : book[l.id]) ?? 0, diff = Math.round((l.q - b) * 1000) / 1000;
    const meta = d.kind === 'receive' ? <><span>{t('sk.cost', { p: money(costOf(l.id)) })}</span><span>{t('sk.book', { n: qtyLabel(l.id, b) })}</span>{isLow(l.id, book) && <StatusTag ent="stock" k="low" form="dot" label={t('sk.low', { n: '' }).trim()} />}</>
      : d.kind === 'count' ? <><span>{t('sk.book', { n: qtyLabel(l.id, b) })}</span>{d.st !== 'done' && d.st !== 'variance' && diff !== 0 && <span className={diff < 0 ? 'text-danger-text font-600' : 'text-success-text font-600'}>{t('sk.diff', { n: (diff > 0 ? '+' : '') + diff })}</span>}</>
      : edit ? <Seg size="sm" value={l.why || 'damaged'} onChange={(v) => setWhy(d.no, l.id, v as LossWhy)} items={(['expired', 'damaged'] as const).map((w) => ({ v: w, label: t('sk.why.' + w) }))} />
      : <span>{t('sk.why.' + (l.why || 'damaged'))}</span>;
    const side = edit ? <Stepper q={l.q} onDec={() => (l.q - step(l.id) <= 0 && d.kind !== 'count' ? removeLine(d.no, l.id) : setQty(d.no, l.id, l.q - step(l.id)))} onInc={() => setQty(d.no, l.id, l.q + step(l.id))} /> : qtyLabel(l.id, l.q) + (item(l.id).weigh ? '' : ' ' + (en ? 'pcs' : unitOf(l.id)));
    const amt = d.kind === 'count' ? <span className="text-sm text-sub font-500">{t('sk.counted')}</span> : money(costOf(l.id) * l.q);
    return <LineRow key={l.id} name={nm(l.id, en)} amt={amt} meta={meta} side={side} strong={edit} />;
  });
  const total = d.kind === 'count'
    ? <DueFoot label={t('sk.total.count')} amt={df && (df.gain || df.loss) ? t('sk.gainLoss', { g: df.gain, l: df.loss }) : t('sk.noDiff')} note={t('sk.lines', { n: d.lines.length })} short={!!df?.loss} />
    : <DueFoot label={t('sk.total.' + d.kind)} amt={money(docCost(d))} note={t('sk.totalNote', { n: d.lines.length, m: money(docCost(d)) }) + ' · ' + docQty(d)} />;
  const done = () => toast(rt('sk.posted', { no: d.no }));
  const acts = d.st === 'draft' && d.kind === 'receive' ? <Acts><Button variant="muted" onClick={() => voidDoc(d.no)}>{t('sk.void')}</Button><Button block disabled={!d.lines.length} onClick={() => { postDoc(d.no); done(); }}>{t('sk.post.receive')}</Button></Acts>
    : d.st === 'draft' && d.kind === 'loss' ? <Acts><Button block disabled={!d.lines.length} onClick={() => { postDoc(d.no); done(); }}>{t('sk.post.loss')}</Button></Acts>
    : d.st === 'counting' ? <Acts><Button block disabled={!d.lines.length} onClick={() => { postDoc(d.no); toast(rt('sk.submitted', { no: d.no })); }}><Ico name="stamp" />{t('sk.submit')}</Button></Acts>
    : d.st === 'pending' ? <><Note tone="warn">{t('sk.pendingNote')}</Note><Acts><Button block onClick={() => { postDoc(d.no); done(); }}>{t('sk.approve')}</Button></Acts></>
    : <Note>{t('sk.readonly')}</Note>;
  return (
    <Panel area="detail" ledger head={head} foot={<>{total}{acts}</>}>
      {d.lines.length ? <Block>{lines}</Block> : <Empty fill icon={<Ico name="scan-barcode" size={40} className="text-sub" />} title={t('sk.empty')} />}
    </Panel>
  );
}
