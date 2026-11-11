// 桌台工作台（W1）：桌区 / 状态导航 · 统计 + 桌台宫格 · 桌台详情（P2 块色清单 + 白色窄底）
import { useStore, useTotals } from '@/store';
import { busyTable, type Table, type TableSt } from '@/data/sample';
import { stGroup, stName, stateOf } from '@/lib/states';
import { useLang, useT } from '@/i18n';
import { Rail, type RailItem } from '@/layout/rail';
import { Panel, Block, DueFoot, Acts, Stat } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { StatusTag, StatusIcon } from '@/components/ui/badge';
import { StatusTile } from '@/components/ui/status-tile';
import { Cells, Cell } from '@/components/ui/description-list';
import { OrderLine } from '@/modules/order/OrderLine';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Seg } from '@/components/ui/toggle-group';
import { AREAS } from '@/data/floors';
import { FloorCanvas } from './FloorCanvas';
import { IsoCanvas } from './IsoCanvas';
import { EditorHead, FloorProps } from './FloorEditor';
import { beginEdit } from './floor-ops';

const ORDER: TableSt[] = ['free', 'booked', 'seated', 'dining', 'late', 'due', 'partial', 'to_clear', 'shared', 'disabled'];
export function TableTile({ t: tb, current, onClick }: { t: Table; current: boolean; onClick: () => void }) {
  const t = useT(); const g = stGroup('table', tb.st), alert = g === 'action' || g === 'failed', quiet = tb.st === 'free' || tb.st === 'disabled';
  const info: Record<string, string> = { free: t('tables.seats', { n: tb.seats }), booked: t('tables.booking'), seated: t('tables.guests', { a: tb.ppl, b: tb.seats }) + ' · ' + t('tables.seatedFor', { n: 6 }), dining: t('tables.guests', { a: tb.ppl, b: tb.seats }) + ' · ' + t('tables.min', { n: tb.min }), late: t('tables.guests', { a: tb.ppl, b: tb.seats }) + ' · ' + t('tables.lateDishes'),
    due: t('tables.guests', { a: tb.ppl, b: tb.seats }) + ' · ' + t('tables.min', { n: tb.min }), partial: t('tables.unpaid'), to_clear: t('tables.paidAgo'), shared: t('tables.sharedTwo'), disabled: t('tables.repair') };
  const amt = /^(dining|late|due|shared)$/.test(tb.st) ? '¥' + tb.amt : tb.st === 'partial' ? '¥' + (tb.amt + 100) : '';
  return (
    <StatusTile group={g} alert={alert} quiet={quiet} dim={tb.st === 'disabled'} current={current} onClick={onClick} className="min-h-[calc(var(--tile)+8px)] py-2.5 px-3 gap-0.5 w-auto">
      <b className="text-lg num tracking-[-.01em]">{tb.id}</b>
      <StatusTag ent="table" k={tb.st} form="dot" className="my-0.5" />
      <small className="text-xs text-sub">{info[tb.st]}</small>
      {amt && <span className="a mt-auto font-600 num">{amt}</span>}
    </StatusTile>
  );
}
export function TablesWB() {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const cnt: Partial<Record<TableSt, number>> = {}; s.tables.forEach((x) => (cnt[x.st] = (cnt[x.st] || 0) + 1));
  const nav: RailItem[] = [{ cap: t('tables.area') }, { v: '全部', label: t('tables.all'), em: s.tables.length }, { v: '大厅', label: t('tables.hall'), em: 12 }, { v: '包间', label: t('tables.room'), em: 6 }, { cap: t('tables.status') },
    ...ORDER.filter((k) => cnt[k]).map((k) => ({ v: k, label: stName('table', k, lang), icon: stateOf('table', k)!.icon, em: cnt[k] }))];
  const area = /^(大厅|包间)$/.test(s.tst) ? s.tst : s.farea;
  const list = s.tables.filter((x) => !s.tst || /^(全部|大厅|包间)$/.test(s.tst) || x.st === s.tst);
  const tb = s.tables.find((x) => x.id === s.table) || s.tables[0], busy = busyTable(tb.st);
  const tot = useTotals();
  return (
    <>
      <Rail items={nav} cur={s.tst || '全部'} onPick={(v) => { dispatch('tst:' + v); if (/^(大厅|包间)$/.test(v)) s.set({ farea: v, fsel: null }); }} ariaLabel={t('tables.area')} />
      {s.fedit && s.fdraft ? <>
        <Panel area="content" head={<EditorHead area={area} />}>{s.fiso ? <IsoCanvas area={area} data={s.fdraft} interactive={false} /> : <FloorCanvas area={area} data={s.fdraft} edit />}</Panel>
        <FloorProps area={area} />
      </> : <>
      <Panel area="content" head={<>
        <div className="flex items-center gap-gap flex-wrap">
          <Seg value={s.tview} onChange={(v) => s.set({ tview: v })} items={[{ v: 'grid', label: <><Ico name="layout-grid" size={15} />{t('fl.view.grid')}</> }, { v: 'floor', label: <><Ico name="map" size={15} />{t('fl.view.floor')}</> }, { v: 'iso', label: <><Ico name="box" size={15} />{t('fl.view.iso')}</> }]} />
          {s.tview !== 'grid' && <Seg value={area} onChange={(v) => s.set({ farea: v, tst: /^(大厅|包间|全部)$/.test(s.tst) ? v : s.tst })} items={AREAS.map((a) => ({ v: a, label: a === '大厅' ? t('tables.hall') : t('tables.room') }))} />}
          {s.tview === 'iso' && <Seg value={String(s.fzoom)} onChange={(v) => s.set({ fzoom: Number(v) as 1 | 2 })} items={[{ v: '1', label: t('fl.zoomFit') }, { v: '2', label: t('fl.zoomIn') }]} />}
          {s.tview !== 'grid' && <span className="help">{t('fl.dblHint')}</span>}
          {s.tview !== 'grid' && <Button variant="muted" size="sm" className="ml-auto" onClick={() => beginEdit(area)}><Ico name="pencil-ruler" size={16} />{t('fl.edit')}</Button>}
        </div>
        <Stat items={[{ n: cnt.free || 0, l: <><StatusIcon ent="table" k="free" />{t('tables.free')}</> }, { n: (cnt.seated || 0) + (cnt.dining || 0) + (cnt.late || 0) + (cnt.shared || 0), l: <><StatusIcon ent="table" k="dining" />{t('tables.dining')}</> }, { n: (cnt.due || 0) + (cnt.partial || 0), l: <><StatusIcon ent="table" k="due" />{t('tables.due')}</> }, { n: '1.6', l: t('tables.turns') }]} /></>}>
        {s.tview === 'iso' ? <IsoCanvas area={area} data={s.floor} dimOf={(x) => !/^(全部|大厅|包间)$/.test(s.tst) && x.st !== s.tst} /> : s.tview === 'floor' ? <FloorCanvas area={area} data={s.floor} dimOf={(x) => !/^(全部|大厅|包间)$/.test(s.tst) && x.st !== s.tst} /> : <div className="grid grid-cols-[repeat(auto-fill,minmax(124px,1fr))] gap-gap content-start">
          {list.map((x) => <TableTile key={x.id} t={x} current={x.id === s.table} onClick={() => dispatch('table:' + x.id)} />)}
        </div>}
      </Panel>
      <Panel area="detail" ledger head={<div className="row-between"><b className="h-title">{tb.id}</b><StatusTag ent="table" k={tb.st} /></div>}
        foot={<DueFoot label={busy ? t('tables.unpaidAmt') : ''} amt={'¥' + tb.amt + '.00'} note={t('tables.ppl.min', { a: tb.ppl, b: tb.min })}>
          {busy && <Acts><Button variant="muted" size="sm" onClick={() => dispatch('ov:x3')}>{t('tables.move')}</Button><Button variant="muted" size="sm" onClick={() => dispatch('ov:x3')}>{t('tables.merge')}</Button><Button variant="muted" size="sm" onClick={() => dispatch('toast:toast.prebill')}>{t('tables.prebill')}</Button><Button variant="danger" size="sm" onClick={() => dispatch('ov:confirm')}>{t('tables.clear')}</Button></Acts>}
          {tb.st === 'due' || tb.st === 'partial' ? <Button block onClick={() => dispatch('gopay')}>{t('tables.gopay')}</Button>
            : busy ? <Button block onClick={() => dispatch('goorder')}>{t('tables.addItems')}</Button>
            : tb.st === 'to_clear' ? <Button block onClick={() => dispatch('cleartable')}>{t('tables.clear')}</Button>
            : tb.st === 'disabled' ? <Button block variant="soft" onClick={() => dispatch('enable')}>{t('tables.enable')}</Button>
            : <Button block onClick={() => dispatch('seat')}>{tb.st === 'booked' ? t('tables.seatBooked') : t('tables.seat')}</Button>}
        </DueFoot>}>
        <Block><Cells className="bg-surface rounded-rk px-pad">
          {busy ? <><Cell k={t('tables.guestsK')} v={tb.ppl + ' / ' + tb.seats} num /><Cell k={t('tables.openAt')} v={t('tables.minutes', { t: '11:42', n: tb.min })} num /><Cell k={t('tables.server')} v={t('tables.serverName')} go /></>
            : tb.st === 'booked' ? <><Cell k={t('tables.booker')} v={t('tables.bookerName')} /><Cell k={t('tables.guestsK')} v={6} num /><Cell k={t('tables.arrive')} v="19:00" num /></>
            : <><Cell k={t('tables.seatsK')} v={tb.seats} num /><Cell k={t('tables.areaK')} v={t('tables.hall')} /></>}
        </Cells></Block>
        {busy && <Block title={t('tables.ordered', { n: s.lines.length })} right={t('tables.served', { a: 1, b: s.lines.length })}>
          <div>{s.lines.map((l, i) => <OrderLine key={i} l={l.st === 'new' ? { n: l.n, q: l.q, p: l.p, st: 'cook' } : l} i={i} edit={false} />)}</div>
        </Block>}
      </Panel>
      </>}
    </>
  );
}
