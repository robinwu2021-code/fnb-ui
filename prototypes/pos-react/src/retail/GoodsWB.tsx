// 零售商品工作台（W1，方案 §5.1）：商品资料（改价 → 自动待打价签）· 库存（账面 / 安全库存 / 临期 · 最近变动 · 去入库 / 盘点）· 价签（待打队列 · 模板 · 预览 · 打印）。
// 库存数据和流水来自 retail/stock.ts（库存作业、收银、退货都通过事件更新它）；价签条码由 domain/catalog 编码。
import { useState } from 'react';
import { useStore } from '@/store';
import { money } from '@/lib/money';
import { Ico } from '@/lib/icon';
import { Rail } from '@/layout/rail';
import { Panel, Block, DueFoot, Acts } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { Input, Search } from '@/components/ui/input';
import { Filter, FilterCount } from '@/components/ui/toggle';
import { Seg } from '@/components/ui/toggle-group';
import { StatusTag } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { Cells, Cell } from '@/components/ui/description-list';
import { Empty, Note } from '@/components/ui/feedback';
import { Table, Tr, Td, Nm2 } from '@/components/ui/table';
import { stockState } from '@/domain/catalog';
import { useBiz } from '@/biz/store';
import { toast } from 'sonner';
import { ITEMS, RCATS, item, type Item } from './data';
import { EXPIRY, SAFETY, addToDoc, costOf, isLow, isNear, movesOf, newDoc, unitOf, useStock } from './stock';
import { printPicked, queueLabel, setPrice, togglePick, useGoods, type LabelTpl } from './goods';
import { PriceLabel } from './PriceLabel';
import { rt, useRT } from './dict';

const useEn = () => useStore((s) => s.lang) === 'en';
const nm = (it: Item, en: boolean) => (en ? it.en : it.n);
const qty = (id: string, q: number) => (item(id).weigh ? q.toFixed(3) + ' kg' : String(q));
/** 库存状态：缺货 / 低于安全库存 / 临期 / 充足（规则：domain/catalog.stockState + 安全库存 + 到期日） */
function stockTag(id: string, book: Record<string, number>, t: ReturnType<typeof useRT>) {
  const left = book[id] ?? 0, st = stockState(left, SAFETY[id] ?? 5);
  if (st === 'out') return <StatusTag ent="stock" k="out" form="dot" />;
  if (isLow(id, book)) return <StatusTag ent="stock" k="low" form="dot" label={t('gd.sf.low')} />;
  if (isNear(id)) return <StatusTag ent="stock" k="expiry" form="dot" />;
  return <StatusTag ent="stock" k="in_stock" form="dot" />;
}

export function GoodsWB() {
  const t = useRT(); const g = useGoods(), book = useStock((s) => s.book);
  const low = ITEMS.filter((it) => isLow(it.id, book) || (book[it.id] ?? 0) <= 0).length;
  const nav = [{ v: 'items', icon: 'package', label: t('gd.items'), em: ITEMS.length }, { v: 'stock', icon: 'warehouse', label: t('gd.stock'), em: low || undefined }, { v: 'labels', icon: 'tags', label: t('gd.labels'), em: g.queue.length || undefined }];
  return (
    <>
      <Rail items={nav} cur={g.sub} onPick={(v) => g.set({ sub: v as typeof g.sub })} ariaLabel={t('tab.goods')} />
      {g.sub === 'items' ? <ItemsPage /> : g.sub === 'stock' ? <StockPage /> : <LabelsPage />}
    </>
  );
}

/* ---------- 商品资料 ---------- */
function ItemsPage() {
  const t = useRT(), en = useEn(); const g = useGoods(), book = useStock((s) => s.book);
  const list = ITEMS.filter((it) => it.c.includes(g.cat as Item['c'][number]));
  const cur = item(g.sel);
  return (
    <>
      <Panel area="content" head={<><Search placeholder={t('gd.search')} /><Seg value={g.cat} onChange={(v) => g.set({ cat: v })} items={RCATS.map((c) => ({ v: c.v, label: en ? c.en : c.zh }))} /></>}>
        <Table cols="minmax(0,1.6fr) 112px 80px 72px 80px 96px" head={[t('gd.th.item'), t('gd.th.code'), <span className="block text-right">{t('gd.th.price')}</span>, <span className="block text-right">{t('gd.th.mp')}</span>, <span className="block text-right">{t('gd.th.stock')}</span>, t('gd.th.st')]}>
          {list.map((it) => (
            <Tr key={it.id} on={g.sel === it.id} onClick={() => g.set({ sel: it.id })}>
              <Td><Nm2 a={nm(it, en)} b={it.weigh ? t('gd.weigh') : it.sku ? t('gd.sku') : undefined} /></Td><Td className="num text-sub text-xs">{it.code}</Td>
              <Td r>{money(it.p)}</Td><Td r>{it.mp ? money(it.mp) : '—'}</Td><Td r>{qty(it.id, book[it.id] ?? 0)}</Td><Td>{stockTag(it.id, book, t)}</Td>
            </Tr>))}
        </Table>
      </Panel>
      <ItemDetail key={cur.id} it={cur} en={en} />
    </>
  );
}
function ItemDetail({ it, en }: { it: Item; en: boolean }) {
  const t = useRT(); const g = useGoods(), book = useStock((s) => s.book); const [draft, setDraft] = useState(String(it.p));
  const cost = costOf(it.id), margin = Math.round((1 - cost / it.p) * 100);
  const save = () => { const p = Number(draft); if (setPrice(it.id, p)) toast(rt('gd.priced', { n: nm(it, en), p: money(p) })); };
  return (
    <Panel area="detail" head={<><div className="row-between"><b className="h-title">{nm(it, en)}</b>{stockTag(it.id, book, t)}</div><span className="help num">{it.code} · {RCATS.filter((c) => it.c.includes(c.v)).map((c) => (en ? c.en : c.zh)).join(' / ')}</span></>}
      foot={<Acts><Button variant="muted" onClick={() => { queueLabel(it.id); g.set({ sub: 'labels' }); }}><Ico name="tag" />{t('gd.toLabels')}</Button><Button block disabled={Number(draft) === it.p || !(Number(draft) > 0)} onClick={save}>{t('gd.savePrice')}</Button></Acts>}>
      <Block title={t('gd.price')}>
        <div className="flex items-center gap-gap"><Input num value={draft} onChange={(e) => setDraft(e.target.value.replace(/[^\d.]/g, ''))} aria-label={t('gd.price')} className="flex-1" /><span className="help">{it.weigh ? '/ kg' : '/ ' + (en ? 'pc' : unitOf(it.id))}</span></div>
        <span className="help block mt-1.5">{t('gd.priceHint')}</span>
      </Block>
      <Block title={t('gd.items')}>
        <Cells>
          <Cell k={t('gd.th.mp')} v={it.mp ? money(it.mp) + (it.mp >= it.p ? ' · ' + t('gd.mpOff') : '') : '—'} num /><Cell k={t('gd.cost')} v={money(cost)} num /><Cell k={t('gd.margin')} v={margin + '%'} num />
          <Cell k={t('gd.th.stock')} v={qty(it.id, book[it.id] ?? 0)} num /><Cell k={t('gd.th.safety')} v={SAFETY[it.id] != null ? qty(it.id, SAFETY[it.id]) : '—'} num /><Cell k={t('gd.th.expiry')} v={EXPIRY[it.id] || '—'} num />
        </Cells>
      </Block>
    </Panel>
  );
}

/* ---------- 库存 ---------- */
function StockPage() {
  const t = useRT(), en = useEn(); const g = useGoods(), book = useStock((s) => s.book);
  const match = (id: string, f: typeof g.sf) => f === 'all' || (f === 'out' ? (book[id] ?? 0) <= 0 : f === 'low' ? isLow(id, book) && (book[id] ?? 0) > 0 : isNear(id));
  const list = ITEMS.filter((it) => match(it.id, g.sf));
  const value = Math.round(ITEMS.reduce((a, it) => a + Math.max(0, book[it.id] ?? 0) * costOf(it.id), 0));
  return (
    <>
      <Panel area="content" head={<><div className="row-between"><b className="h-title">{t('gd.stock')}</b><span className="help num">{t('gd.th.value')} {money(value)}</span></div>
        <div className="flex flex-wrap gap-gap">{(['all', 'low', 'out', 'near'] as const).map((f) => { const n = ITEMS.filter((it) => match(it.id, f)).length; return <Filter key={f} pressed={g.sf === f} onPressedChange={() => g.set({ sf: f })}>{t('gd.sf.' + f)}<FilterCount n={n} alert={f !== 'all' && n > 0} /></Filter>; })}</div></>}>
        {!list.length ? <Empty fill title={t('gd.sf.' + g.sf)} /> : (
          <Table cols="minmax(0,1.6fr) 88px 80px 96px 104px" head={[t('gd.th.item'), <span className="block text-right">{t('gd.th.stock')}</span>, <span className="block text-right">{t('gd.th.safety')}</span>, t('gd.th.expiry'), t('gd.th.st')]}>
            {list.map((it) => (
              <Tr key={it.id} on={g.sel === it.id} onClick={() => g.set({ sel: it.id })}>
                <Td><Nm2 a={nm(it, en)} b={it.code} /></Td><Td r className={(book[it.id] ?? 0) <= (SAFETY[it.id] ?? -1) ? 'text-danger-text font-600' : ''}>{qty(it.id, book[it.id] ?? 0)}</Td>
                <Td r className="text-sub">{SAFETY[it.id] != null ? qty(it.id, SAFETY[it.id]) : '—'}</Td><Td className={isNear(it.id) ? 'text-warning-text font-600 num' : 'text-sub num'}>{EXPIRY[it.id] || '—'}</Td><Td>{stockTag(it.id, book, t)}</Td>
              </Tr>))}
          </Table>)}
      </Panel>
      <StockDetail id={g.sel} en={en} />
    </>
  );
}
function StockDetail({ id, en }: { id: string; en: boolean }) {
  const t = useRT(); const it = item(id); const book = useStock((s) => s.book), all = useStock((s) => s.moves), moves = movesOf(all, id); /* 先取整个数组再筛，避免选择器每次返回新数组导致重渲染循环 */
  const go = (kind: 'receive' | 'count') => { newDoc(kind); const no = useStock.getState().sel; addToDoc(no, id, kind === 'receive' ? 12 : 1); useBiz.getState().set({ rwb: 'stock' }); toast(rt('gd.opened', { no })); };
  return (
    <Panel area="detail" ledger head={<><div className="row-between"><b className="h-title">{nm(it, en)}</b>{stockTag(id, book, t)}</div><span className="help num">{it.code}</span></>}
      foot={<DueFoot label={t('gd.th.stock')} amt={qty(id, book[id] ?? 0)} note={t('gd.th.value') + ' ' + money(Math.max(0, book[id] ?? 0) * costOf(id))}><Acts><Button variant="muted" onClick={() => go('count')}><Ico name="clipboard-list" />{t('gd.doAdjust')}</Button><Button block onClick={() => go('receive')}><Ico name="package-plus" />{t('gd.doReceive')}</Button></Acts></DueFoot>}>
      <Block title={t('gd.stock')}><Cells><Cell k={t('gd.th.safety')} v={SAFETY[id] != null ? qty(id, SAFETY[id]) : '—'} num /><Cell k={t('gd.cost')} v={money(costOf(id))} num /><Cell k={t('gd.th.expiry')} v={EXPIRY[id] || '—'} num /></Cells></Block>
      <Block title={t('gd.moves')}>{moves.length ? <Cells>{moves.slice(0, 8).map((m, i) => (
        <Cell key={i} k={<span className="flex flex-col leading-tight"><span className="text-ink">{t('gd.mv.' + m.kind)}</span><small className="text-xs num">{m.time} · {m.ref}</small></span>}><b className={'num ' + (m.delta < 0 ? 'text-danger-text' : 'text-success-text')}>{(m.delta > 0 ? '+' : '') + qty(id, m.delta)}</b></Cell>))}</Cells>
        : <span className="help">{t('gd.noMoves')}</span>}</Block>
    </Panel>
  );
}

/* ---------- 价签 ---------- */
function LabelsPage() {
  const t = useRT(), en = useEn(); const g = useGoods();
  const add = (raw: string) => { const it = ITEMS.find((x) => x.code.replace(/\s/g, '') === raw.replace(/\s/g, '') || x.n.includes(raw) || x.en.toLowerCase().includes(raw.toLowerCase())); if (it) queueLabel(it.id); };
  const allOn = g.queue.length > 0 && g.queue.every((q) => g.picked.includes(q.id));
  const n = g.picked.filter((id) => g.queue.some((q) => q.id === id)).length;
  return (
    <>
      <Panel area="content" head={<>
        <label className="flex items-center gap-2 h-ctl-sm px-px rounded-rb bg-wash"><Ico name="search" size={16} className="text-sub" /><input placeholder={t('lb.add')} className="flex-1 min-w-0 bg-transparent" onKeyDown={(e) => { const v = e.currentTarget.value.trim(); if (e.key === 'Enter' && v) { add(v); e.currentTarget.value = ''; } }} /></label>
        <div className="row-between"><b>{t('lb.queue', { n: g.queue.length })}</b>{g.queue.length > 0 && <Button variant="link" size="sm" className="hit text-sm" onClick={() => g.set({ picked: allOn ? [] : g.queue.map((q) => q.id) })}>{allOn ? t('lb.none') : t('lb.all')}</Button>}</div>
      </>}>
        {!g.queue.length ? <Empty fill icon={<Ico name="tags" size={44} className="text-sub" />} title={t('lb.empty')} desc={t('lb.emptyHint')} /> : (
          <div className="flex flex-col gap-0.5">{g.queue.map((q) => { const it = item(q.id), on = g.picked.includes(q.id);
            return (
              <div key={q.id} className={'grid grid-cols-[22px_minmax(0,1fr)_auto] gap-3 items-center min-h-$row px-2.5 rounded-rk ' + (g.focus === q.id ? 'bg-wash-strong' : 'hover:bg-wash')}>
                <Checkbox checked={on} onCheckedChange={() => togglePick(q.id)} aria-label={nm(it, en)} />
                <button type="button" className="text-left min-w-0" onClick={() => useGoods.getState().set({ focus: q.id })}><Nm2 a={nm(it, en)} b={q.why === 'price' ? t('lb.why.price', { a: money(q.old!), b: money(it.p) }) : t('lb.why.' + q.why)} /></button>
                <b className="num">{money(it.p)}{it.weigh ? '/kg' : ''}</b>
              </div>); })}</div>)}
      </Panel>
      <Panel area="detail" head={<><b className="h-title">{t('lb.preview')}</b><span className="help">{t('lb.printer')}</span></>}
        foot={<Acts><Button block disabled={!n} onClick={() => { const k = printPicked(); toast(rt('lb.printed', { n: k })); }}><Ico name="printer" />{t('lb.print', { n })}</Button></Acts>}>
        <span className="gt"><span>{t('lb.tpl')}</span></span>
        <Seg value={g.tpl} onChange={(v) => g.set({ tpl: v as LabelTpl })} items={(['60x40', '40x30', 'shelf'] as const).map((v) => ({ v, label: t('lb.tpl.' + v) }))} className="mt-1.5" />
        <div className="mt-3 rounded-rt bg-wash p-4 flex justify-center items-center min-h-200px"><PriceLabel id={g.focus} tpl={g.tpl} en={en} /></div>
        <Note className="mt-3">{t('gd.priceHint')}</Note>
      </Panel>
    </>
  );
}
