// 点餐工作台（W1）：分类导航 · 工具条 + 菜品宫格 · 结单区 v4（头 · 块色清单 · 白色窄底）
import { useStore, useTotals } from '@/store';
import { CATS, DISHES, MEMBER } from '@/data/sample';
import { qtyOf, newCount, dishCount } from '@/lib/pricing';
import { money } from '@/lib/money';
import { useLang, useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Rail } from '@/layout/rail';
import { Panel, Block, DueFoot, Acts } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { Search } from '@/components/ui/input';
import { Filter } from '@/components/ui/toggle';
import { Seg } from '@/components/ui/toggle-group';
import { Chip, StatusTag } from '@/components/ui/badge';
import { Empty } from '@/components/ui/feedback';
import { DishCard } from './DishCard';
import { OrderLine } from './OrderLine';
import { AdjRows } from './AdjRows';
import { MoreMenu } from '@/overlays/MoreMenu';
import { useState } from 'react';
import { Slot } from '@/layout/edit/Slot';

export function OrderWB() {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const [filters, setFilters] = useState<string[]>(['all']);
  const nav = CATS.map((c) => ({ v: c.key, k: c.k, label: lang === 'en' ? c.en : c.key, second: s.names === 'bi' ? (lang === 'en' ? c.key : c.en) : undefined, dot: DISHES.some((d) => (c.key === '热销' ? d.hot : d.c === c.key) && qtyOf(s.lines, d.n) > 0) }));
  const list = s.dishes.filter((d) => (s.cat === '热销' ? d.hot : d.c === s.cat));
  const tog = (k: string) => setFilters((f) => (f.includes(k) ? f.filter((x) => x !== k) : [...f, k]));
  return (
    <>
      <Rail items={nav} cur={s.cat} onPick={(v) => dispatch('cat:' + v)} ariaLabel={t('tab.order')} />
      <Panel area="content" head={
        <div className="flex flex-wrap gap-gap items-center [&>label]:flex-[1_1_220px] [&>label]:w-auto">
          <Search placeholder={t('order.search')} extra={t('order.scan')} />
          <Filter pressed={filters.includes('all')} onPressedChange={() => tog('all')}>{t('order.all')}</Filter><Filter pressed={filters.includes('spicy')} onPressedChange={() => tog('spicy')}>{t('order.spicy')}</Filter><Filter pressed={filters.includes('veg')} onPressedChange={() => tog('veg')}>{t('order.veg')}</Filter><Filter pressed={filters.includes('deal')} onPressedChange={() => tog('deal')}>{t('order.deal')}</Filter>
          <Seg value={s.pic} onChange={(v) => s.set({ pic: v })} items={[{ v: 'img', label: t('order.pic.img') }, { v: 'off', label: t('order.pic.off') }]} />
          {s.pic === 'img' && <Seg value={s.card} onChange={(v) => s.set({ card: v })} items={[{ v: 'v', label: t('order.card.v') }, { v: 'h', label: t('order.card.h') }]} />}
          <Slot id="t" className="ml-auto" />
        </div>}>
        <div className={cn('grid gap-gap w-full', s.pic === 'off' ? 'grid-cols-[repeat(auto-fill,minmax(140px,1fr))] auto-rows-max content-start' : s.card === 'h' ? 'grid-cols-[repeat(auto-fill,minmax(200px,1fr))] auto-rows-fr' : 'grid-cols-[repeat(auto-fill,minmax(160px,1fr))] auto-rows-fr')}>
          {list.map((d) => <DishCard key={d.n} d={d} />)}
        </div>
      </Panel>
      {/* F 悬浮槽：和内容区同一 gridArea 叠上去，右下角一列圆钮 */}
      <div style={{ gridArea: 'content' }} className="relative z-20 pointer-events-none"><div className="absolute right-3 bottom-3 pointer-events-auto"><Slot id="f" /></div></div>
      <Dock />
    </>
  );
}

function BatchActs() {
  const t = useT(); const s = useStore(), dispatch = s.act, n = s.sel.length;
  if (s.bmode === 'split') return <Acts><Button variant="muted" onClick={() => dispatch('batch:off')}>{t('order.cancel')}</Button><Button block disabled={!n} onClick={() => dispatch('splitsel')}>{t('order.splitTo')}{n ? ' · ' + n : ''}</Button></Acts>;
  return <Acts><Button variant="soft" block disabled={!n} onClick={() => dispatch('rushsel')}>{t('order.rush')}{n ? ' · ' + n : ''}</Button><Button variant="muted" disabled={!n} onClick={() => dispatch('ov:x3')}>{t('order.transfer')}</Button><Button variant="danger" disabled={!n} onClick={() => dispatch('ov:x2')}>{t('order.void')}</Button></Acts>;
}
export function Dock() {
  const t = useT(); const s = useStore(), dispatch = s.act, tot = useTotals();
  const nc = newCount(s.lines), news: [typeof s.lines[0], number][] = [], sent: [typeof s.lines[0], number][] = [];
  s.lines.forEach((l, i) => (l.st === 'new' ? news : sent).push([l, i]));
  const pickable = sent.filter((x) => x[0].st !== 'void').length;
  const head = (
    <>
      <div className="flex flex-col gap-0.5">
        <div className="flex items-center gap-2">
          <b className="text-xl font-700 tracking-[-.01em] num">{s.table}</b><StatusTag ent="order" k="open" />
          {s.member && <Chip onClick={() => dispatch('sheet:member')}>{MEMBER.layer} · {MEMBER.name}</Chip>}
          <MoreMenu />
        </div>
        {s.note.length ? <button type="button" className="hit text-sm text-sub text-left" onClick={() => dispatch('ov:note')}>{t('order.meta')} · {t('order.note')}<span>{s.note.map((k) => t(k)).join('、')}</span></button> : <div className="text-sm text-sub">{t('order.meta')}</div>}
      </div>
      {!s.batch && <Slot id="q" />}
    </>
  );
  const foot = s.lines.length ? (
    <DueFoot label={t('order.due')} amt={money(tot.due)} note={t('order.dueNote', { n: dishCount(s.lines), m: money(tot.mem + tot.round) })}>
      {s.batch ? <BatchActs /> : <Acts>{nc > 0 && <Button variant="soft" onClick={() => dispatch('send')}>{t('order.send', { n: nc })}</Button>}<Button block onClick={() => dispatch('gopay')}>{t('order.pay')}</Button></Acts>}
    </DueFoot>
  ) : null;
  return (
    <Panel area="detail" ledger head={head} foot={foot}>
      {s.lines.length === 0 ? <Empty fill title={t('order.empty')} desc={t('order.emptyHint')} /> : (
        <>
          {news.length > 0 && <Block title={t('order.pending', { n: nc })} right={<Button variant="link" className="hit text-sm" onClick={() => dispatch('clear')}>{t('order.clear')}</Button>}>
            <div className="bg-surface rounded-rk px-pad">{news.map(([l, i]) => <OrderLine key={i} l={l} i={i} edit />)}</div>
          </Block>}
          {sent.length > 0 && <Block title={s.batch ? <>{s.bmode === 'split' ? t('order.pickSplit') : ''}{t('order.selected', { a: s.sel.length, b: pickable })}</> : t('order.sent', { n: sent.length, a: sent.filter((x) => x[0].st === 'done').length, b: sent.length })}
            right={s.batch ? <><Button variant="link" className="hit text-sm" onClick={() => dispatch('batch:' + (s.sel.length === pickable ? 'none' : 'all'))}>{s.sel.length === pickable ? t('order.selectNone') : t('order.selectAll')}</Button><Button variant="link" className="hit text-sm" onClick={() => dispatch('batch:off')}>{t('order.done')}</Button></> : <Button variant="link" className="hit text-sm" onClick={() => dispatch('batch:on')}>{t('order.batch')}</Button>}>
            <div>{sent.map(([l, i]) => <OrderLine key={i} l={l} i={i} edit={false} tappable />)}</div>
          </Block>}
          <AdjRows t={tot} />
        </>
      )}
    </Panel>
  );
}
