// 零售收银工作台（W2）：左购物车 440（P2 块色清单 + 白色窄底）· 右操作区两态（扫码 / 收款）。
// 方案：docs/全线点餐POS-零售POS设计方案.md §三。关键画面原型：状态在本文件的小 store 里，页首「画面」直接切预设。
// 和餐饮共用：系统栏骨架 SysBarShell · 清单行 LineRow · 费用行 FeeRow · 快捷操作 QuickBar · 商品磁贴 ItemTile · 收款区 PayHead / CashPad / WaitBlock（components/pos/）
import { create } from 'zustand';
import { Fragment, type ReactNode } from 'react';
import { useStore } from '@/store';
import { useT } from '@/i18n';
import { money, round2 } from '@/lib/money';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Panel, Block, DueFoot, Acts } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { StatusIcon, StatusTag, Chip } from '@/components/ui/badge';
import { Banner, Empty, Note } from '@/components/ui/feedback';
import { Filter } from '@/components/ui/toggle';
import { Seg } from '@/components/ui/toggle-group';
import { Stepper } from '@/components/ui/stepper';
import { Amount, Result } from '@/components/ui/result';
import { Dialog, DialogActs, DialogDesc, DialogTitle } from '@/components/ui/dialog';
import { Sheet } from '@/components/ui/sheet';
import { StatusTile } from '@/components/ui/status-tile';
import { Cells, Cell } from '@/components/ui/description-list';
import { Menu, MenuContent, MenuItem, MenuSep, MenuTrigger } from '@/components/ui/dropdown-menu';
import { LineRow } from '@/components/pos/line-row';
import { FeeRow } from '@/components/pos/fee-row';
import { QuickBar, type QuickItem } from '@/components/pos/quick';
import { ItemTile, TileFoot } from '@/components/pos/item-tile';
import { CashPad, PayHead, WaitBlock, payLabel } from '@/components/pos/pay';
import { toast } from 'sonner';
import { CART, HELD, ITEMS, RCATS, SKU, UNKNOWN_CODE, item, lineAmt, lineMember, lineName, linePromo, lineUnit, nextKey, rTotals, type Item, type RCat, type RLine } from './data';
import { rt, useRT } from './dict';
import { openReturn } from './sales';
import * as cart from '@/domain/cart';
import * as catalog from '@/domain/catalog';
import * as payment from '@/domain/payment';
import { emit, on } from '@/domain/events';
import { exchangeSettle } from '@/domain/aftersale';
import { BIZ } from '@/biz';

/* ---------- 状态 ---------- */
export type Screen = 'empty' | 'cart' | 'unknown' | 'peek' | 'weigh' | 'sku' | 'held' | 'clear' | 'pay' | 'done' | 'xcart' | 'xrefund';
/** 换货抵扣：退回部分折成的金额，从订单页换货带过来（事件 exchange.credit） */
export interface Credit { no: string; amt: number; pcs: number; method: string }
interface RS {
  screen: Screen; lines: RLine[]; member: boolean; mode: 'scan' | 'pay'; ov: '' | 'weigh' | 'sku' | 'held' | 'clear' | 'done'; banner: '' | 'unknown';
  last: number | null; open: number | null; cat: RCat; qty: number; weighId: string; w: number; sku: [number, number] | null; skuId: string;
  peek: boolean; peekId: string | null; pay: string; ent: string; credit: Credit | null; set: (p: Partial<RS>) => void;
}
const clone = (l: RLine[]) => l.map((x) => ({ ...x }));
function preset(s: Screen): Partial<RS> {
  const base: Partial<RS> = { screen: s, lines: clone(CART), member: true, mode: 'scan', ov: '', banner: '', last: 8, open: null, cat: 'common', qty: 1, weighId: 'apple', w: 1.236, sku: null, skuId: 'tee', peek: false, peekId: null, pay: '', ent: '', credit: null };
  switch (s) {
    case 'empty': return { ...base, lines: [], member: false, last: null };
    case 'unknown': return { ...base, banner: 'unknown' };
    case 'peek': return { ...base, peek: true, peekId: 'milk' };
    case 'weigh': return { ...base, ov: 'weigh', cat: 'fresh', weighId: 'grape', w: 0.862 };
    case 'sku': return { ...base, ov: 'sku', cat: 'apparel', sku: [1, 1] };
    case 'held': return { ...base, ov: 'held' };
    case 'clear': return { ...base, ov: 'clear' };
    case 'pay': return { ...base, mode: 'pay', pay: 'cash', ent: '200' };
    case 'done': return { ...base, mode: 'pay', pay: 'cash', ent: '200', ov: 'done' };
    case 'xcart': return { ...base, lines: [{ key: 9, id: 'tee', q: 1, sku: ['黑 / L', 'Black / L'] }], last: 9, member: false, cat: 'apparel', credit: { no: '#R0155', amt: 53.1, pcs: 1, method: 'cash' } };
    case 'xrefund': return { ...base, lines: [{ key: 9, id: 'socks', q: 1, sku: ['灰', 'Grey'] }], last: 9, member: false, cat: 'apparel', credit: { no: '#R0155', amt: 53.1, pcs: 1, method: 'cash' } };
    default: return base;
  }
}
export const useR = create<RS>((set) => ({ ...(preset('cart') as RS), set: (p) => set(p) }));
export const goScreen = (s: Screen) => useR.getState().set(preset(s));

/* 业务规则在 domain/cart（合并加购、加减、撤销）、domain/catalog（码分类、称重码）、domain/payment（键盘输入）；这里只接状态 */
function addLine(nl: Omit<RLine, 'key'>) { const s = useR.getState(); const r = cart.scanAdd(s.lines, nl, nextKey); s.set({ lines: r.lines, last: r.last, banner: '', qty: 1 }); }
const bump = (key: number, d: number) => { const s = useR.getState(); const lines = cart.bump(s.lines, key, d); s.set({ lines, last: d > 0 ? key : lines.some((x) => x.key === s.last) ? s.last : null }); };
const undoLast = (key: number) => { const s = useR.getState(); const lines = cart.undoLast(s.lines, key); s.set({ lines, last: lines.some((x) => x.key === key) ? key : null }); };
const openDrawer = () => toast(rt('drawer.toast'));
const addCustom = () => addLine({ id: 'bag', q: 1, custom: { n: rt('line.custom'), p: 10 } });

/** 扫到一个码：按 domain/catalog 分类后分流（方案 §3.2）——付款码去收款、会员码识别会员、称重码直接加称重行、商品码按商品类型加购 / 称重 / 选规格、找不到出横幅 */
export function scanCode(raw: string) {
  const s = useR.getState(), kind = catalog.classifyCode(raw);
  if (kind === 'pay') { if (s.lines.length) s.set({ mode: 'pay', pay: 'wx', ent: '' }); return; }
  if (s.mode === 'pay') s.set({ mode: 'scan', pay: '', ent: '' });
  if (kind === 'member') { s.set({ member: true }); return; }
  if (kind === 'weigh') {
    const w = catalog.parseWeighCode(raw)!, it = ITEMS.find((x) => x.weigh && Number(x.code.replace(/\D/g, '')) === Number(w.plu));
    if (!it) { s.set({ banner: 'unknown' }); return; }
    addLine({ id: it.id, q: 1, w: w.weight }); toast(rt('scan.weighCode')); return;
  }
  const it = catalog.findByCode(ITEMS, raw);
  if (!it) { s.set({ banner: 'unknown', peekId: null }); return; }
  if (s.peek) { s.set({ peekId: it.id, banner: '' }); return; }
  if (it.weigh) { s.set({ ov: 'weigh', weighId: it.id, w: 0.354 }); return; }
  if (it.sku) { s.set({ ov: 'sku', skuId: it.id, sku: null }); return; }
  addLine({ id: it.id, q: s.qty });
}
/** 页首「模拟扫码」：每种情况一个真实格式的码，走 scanCode */
const SIM_CODES = { std: '6901939621271', weighCode: '200100200431006180', sku: '2300101', unknown: UNKNOWN_CODE, loose: 'PLU 1010', payCode: '134567890123456789' } as const;
export function scanSim(kind: keyof typeof SIM_CODES) { scanCode(SIM_CODES[kind]); }
/** 卖出扣库存：发 stock.changed（负数，称重按 kg，临时商品不管库存），库存服务订阅后扣账面 */
function emitSaleStock(lines: RLine[]) { const delta: Record<string, number> = {}; lines.filter((l) => !l.custom).forEach((l) => (delta[l.id] = (delta[l.id] || 0) - (l.w ?? l.q))); emit({ type: 'stock.changed', ids: Object.keys(delta), delta, reason: 'sale', ref: '#R0158' }); }
function useTot() { const lines = useR((s) => s.lines), member = useR((s) => s.member); return rTotals(lines, member); }
/** 应收：有换货抵扣时按 domain/aftersale.exchangeSettle 算补收 / 退差价 */
function usePay() { const tot = useTot(), credit = useR((s) => s.credit); const st = credit ? exchangeSettle(tot.due, credit.amt) : null; return { tot, credit, st, due: st ? st.pay : tot.due }; }
/* 订单页换货确认后：开一张新购物车，带抵扣（订阅事件，不直接互相调用） */
on('exchange.credit', (e) => useR.getState().set({ ...preset('empty'), credit: { no: e.orderNo, amt: e.amt, pcs: e.pcs, method: e.method } }));
const useEn = () => useStore((s) => s.lang) === 'en';

/* ---------- 购物车行（LineRow）---------- */
function CartLine({ l }: { l: RLine }) {
  const t = useRT(), en = useEn(); const r = useR(); const pay = r.mode === 'pay';
  const it = l.custom ? null : item(l.id), promo = linePromo(l), mem = lineMember(l, r.member), open = !pay && r.open === l.key;
  const meta: ReactNode[] = [];
  if (l.w != null) meta.push(<StatusTag key="w" ent="item" k="weighed" form="dot" label={t('line.kg', { w: l.w.toFixed(3), p: lineUnit(l).toFixed(2) })} />);
  else meta.push(<span key="u">{t('line.each', { p: lineUnit(l).toFixed(2) })}</span>);
  if (l.sku) meta.push(<span key="s">{en ? l.sku[1] : l.sku[0]}</span>);
  if (l.custom) meta.push(<StatusTag key="c" ent="item" k="custom" form="dot" label={t('line.custom')} />);
  if (l.gift) meta.push(<StatusTag key="g" ent="item" k="comp" form="dot" />);
  if (promo) meta.push(<StatusTag key="p" ent="item" k="promo" form="dot" label={<>{t('line.half2')} <span className="text-saving num">−{money(promo)}</span></>} />);
  if (mem) meta.push(<StatusTag key="m" ent="item" k="promo" form="dot" label={<>{t('line.mp', { p: it!.mp!.toFixed(2) })} <span className="text-saving num">−{money(mem)}</span></>} />);
  const amt = l.gift ? <><s className="text-placeholder">{money(lineUnit(l) * l.q)}</s> ¥0.00</> : money(lineAmt(l));
  const side = l.w != null ? null : pay ? '×' + l.q : <Stepper q={l.q} onDec={() => bump(l.key, -1)} onInc={() => bump(l.key, 1)} />;
  return (
    <LineRow name={lineName(l, en)} amt={amt} meta={meta} side={side} strong open={open} highlight={!pay && r.last === l.key}
      onToggle={pay ? undefined : () => r.set({ open: open ? null : l.key })}
      actions={<>
        <Button variant="muted" size="sm" onClick={() => toast(t('line.reprice'))}>{t('line.reprice')}</Button>
        <Button variant="muted" size="sm" onClick={() => r.set({ lines: cart.toggleGift(r.lines, l.key), open: null })}>{t('line.gift')}</Button>
        <Button variant="muted" size="sm" onClick={() => toast(t('line.note'))}>{t('line.note')}</Button>
        <Button variant="danger" size="sm" onClick={() => r.set({ lines: cart.removeLine(r.lines, l.key), open: null })}>{t('line.del')}</Button>
      </>} />
  );
}

/* ---------- 左：购物车 ---------- */
function MoreMenu() {
  const t = useRT(); const r = useR();
  return (
    <Menu>
      <MenuTrigger asChild><Button variant="muted" size="icon-sm" aria-label={t('q.more')}><Ico name="ellipsis" size={16} /></Button></MenuTrigger>
      <MenuContent>
        <MenuItem onSelect={() => openReturn(null, 1)}>{t('more.return')}</MenuItem>
        <MenuSep />
        <MenuItem onSelect={() => toast(t('more.note'))}>{t('more.note')}</MenuItem><MenuItem onSelect={() => toast(t('more.reprint'))}>{t('more.reprint')}</MenuItem>
        <MenuItem hint={t('more.needAuth')} onSelect={openDrawer}>{t('more.drawer')}</MenuItem>
        <MenuSep />
        <MenuItem danger disabled={!r.lines.length} onSelect={() => r.set({ ov: 'clear' })}>{t('more.clear')}</MenuItem>
      </MenuContent>
    </Menu>
  );
}
function CartPanel() {
  const t = useRT(); const r = useR(), { tot, credit, st, due } = usePay(); const pay = r.mode === 'pay', empty = !r.lines.length;
  const QDEF: QuickItem[] = [
    { key: 'member', label: t('q.member'), icon: 'scan-line', onClick: () => r.set({ member: !r.member }), on: r.member },
    { key: 'hold', label: t('q.hold'), icon: 'pause', onClick: () => toast(t('q.hold')), disabled: empty },
    { key: 'pick', label: t('q.pick'), icon: 'archive-restore', onClick: () => r.set({ ov: 'held' }), badge: HELD.length },
    { key: 'disc', label: t('q.disc'), icon: 'badge-percent', onClick: () => toast(t('q.disc')), disabled: empty },
    { key: 'reprice', label: t('q.reprice'), icon: 'tag', onClick: () => toast(t('q.reprice')), disabled: empty },
  ];
  const quick = BIZ.retail.quick.map((k) => QDEF.find((q) => q.key === k)).filter(Boolean) as QuickItem[]; /* 顺序和开关来自业态配置 */
  const head = (
    <>
      <div className="flex items-center gap-2">
        <b className="h-title">{t('cart')}</b><span className="help num">{t('cart.no', { no: '#R0158' })}</span>
        {r.member && <Chip onClick={pay ? undefined : () => r.set({ member: false })}><StatusIcon ent="member" k="linked" size={14} />{t('member.on')}</Chip>}
        {!pay && <span className="ml-auto"><MoreMenu /></span>}
      </div>
      {!pay && <QuickBar items={quick} />}
    </>
  );
  const foot = (
    <DueFoot label={credit && st ? (st.refund ? t('ex.refund') : st.even ? t('ex.even') : t('ex.pay')) : empty ? '' : t('due')} amt={money(st ? st.refund || st.pay : tot.due)}
      note={credit ? t('ex.note', { a: money(tot.due), b: money(credit.amt) }) : t('due.note', { n: tot.pcs, m: money(tot.save) })}>
      {pay ? <Acts><Button variant="muted" block onClick={() => r.set({ mode: 'scan', pay: '', ent: '' })}><Ico name="plus" />{t('more')}</Button></Acts>
      : st && (st.refund || st.even) ? <Acts><Button size="lg" block disabled={empty} onClick={() => r.set({ pay: credit!.method, ent: '', ov: 'done', open: null })}>{st.refund ? t('ex.refundBtn', { m: money(st.refund) }) : t('ex.evenBtn')}</Button></Acts> : <>
        {!empty && <div className="flex gap-gap [&>button]:flex-1">
          <Button variant="muted" size="sm" onClick={() => r.set({ mode: 'pay', pay: 'cash', ent: String(due), ov: 'done', open: null })}><Ico name="banknote" />{t('fast.cash')} <b className="num">{money(due)}</b></Button>
          <Button variant="muted" size="sm" onClick={() => r.set({ mode: 'pay', pay: 'wx', ent: '', open: null })}><Ico name="qr-code" />{t('fast.qr')}</Button>
        </div>}
        <Acts><Button variant="muted" disabled={empty} onClick={() => toast(t('q.hold'))}>{t('q.hold')}</Button><Button size="lg" block disabled={empty} onClick={() => r.set({ mode: 'pay', pay: '', ent: '', open: null })}>{t('pay')}{!empty && <b className="num ml-1">{money(due)}</b>}</Button></Acts>
      </>}
    </DueFoot>
  );
  return (
    <Panel area="detail" ledger head={head} foot={foot} bodyClass={empty ? 'justify-center' : undefined}>
      {credit && <Block title={t('ex.block')} className="mb-3"><FeeRow icon="arrow-left-right" name={t('ex.line', { no: credit.no, n: credit.pcs })} amt={'−' + money(credit.amt)} neg /></Block>}
      {empty && credit ? <Empty icon={<Ico name="scan-barcode" size={44} className="text-sub" />} title={t('ex.scanNew')} desc={t('ex.scanNewHint', { m: money(credit.amt) })} />
      : empty ? <Empty icon={<Ico name="scan-barcode" size={44} className="text-sub" />} title={t('cart.empty')} desc={<>{t('cart.emptyDesc')}<br /><Button variant="link" size="sm" className="hit text-sm mt-1" onClick={() => r.set({ member: true })}>{t('member.add')}</Button></>} />
        : <>
          <div>{r.lines.map((l) => <CartLine key={l.key} l={l} />)}</div>
          <Block title={t('fees')} right={<span>{t('fees.order')}</span>} className="mt-3">
            <div className="flex flex-col gap-0.5">
              {tot.promo > 0 && <FeeRow icon="badge-percent" name={t('fee.promo')} amt={'−' + money(tot.promo)} neg />}
              {r.member ? tot.mem > 0 && <FeeRow icon="id-card" name={t('fee.mem')} amt={'−' + money(tot.mem)} neg /> : <FeeRow icon="id-card" name={t('fee.memOff')} off onClick={() => r.set({ member: true })} />}
              <FeeRow icon="ticket" name={t('fee.coupon')} off />
              {tot.round > 0 && <FeeRow icon="scissors" name={t('fee.round')} amt={'−' + money(tot.round)} neg />}
            </div>
          </Block>
        </>}
    </Panel>
  );
}

/* ---------- 右 · 扫码态 ---------- */
function ItemCard({ it }: { it: Item }) {
  const t = useRT(), en = useEn(); const lines = useR((s) => s.lines), set = useR((s) => s.set), qty = useR((s) => s.qty), peek = useR((s) => s.peek);
  const q = lines.reduce((a, l) => (l.id === it.id && !l.custom ? a + (l.w != null ? 1 : l.q) : a), 0), out = it.s === 0;
  const onClick = () => (out ? null : peek ? set({ peekId: it.id }) : it.weigh ? set({ ov: 'weigh', weighId: it.id, w: 0.5 }) : it.sku ? set({ ov: 'sku', skuId: it.id, sku: null }) : addLine({ id: it.id, q: qty }));
  const title = (
    <span className="flex flex-col min-w-0 gap-px">
      <b className="text-md font-600 leading-[1.3] line-clamp-2">{en ? it.en : it.n}</b>
      <span className="text-xs text-sub flex items-center gap-1 num">{it.weigh ? <><Ico name="scale" size={13} />PLU</> : it.sku ? <><Ico name="layers" size={13} />{t('card.sku')}</> : <span className="ellipsis">{it.code.slice(-6)}</span>}</span>
    </span>
  );
  const stock = out ? <StatusTag ent="stock" k="out" form="dot" /> : it.s != null && it.s <= 5 ? <StatusTag ent="stock" k="low" form="dot" label={t('card.low', { n: it.s })} /> : null;
  return <ItemTile qty={q} off={out} onClick={onClick} ariaLabel={en ? it.en : it.n} title={title} foot={<TileFoot price={it.weigh ? t('card.kg', { p: it.p.toFixed(2) }) : '¥' + it.p.toFixed(2)} stock={stock} />} />;
}
/** 扫码框下一行：未知条码横幅 / 查价卡 / 刚扫的一件（撤销 · 再来一件）/ 提示 */
function ScanStrip() {
  const t = useRT(), en = useEn(); const r = useR();
  if (r.banner === 'unknown') return (
    <Banner action={<span className="flex gap-1.5"><Button variant="muted" size="sm" className="bg-surface" onClick={addCustom}>{t('scan.custom')}</Button><Button variant="muted" size="sm" className="bg-surface" onClick={() => toast(t('scan.new'))}>{t('scan.new')}</Button></span>}>
      <Ico name="circle-help" />{t('scan.unknown', { c: UNKNOWN_CODE })}
    </Banner>
  );
  if (r.peek) {
    const it = r.peekId ? item(r.peekId) : null;
    if (!it) return <Note className="flex items-center gap-2"><Ico name="search-check" size={16} />{t('peek.idle')}</Note>;
    return (
      <div className="flex items-center gap-3 min-h-52px px-pad rounded-rk bg-faint">
        <Ico name="search-check" size={18} className="flex-none" />
        <span className="flex flex-col min-w-0 leading-tight"><b className="ellipsis">{en ? it.en : it.n}</b><span className="help num">{it.code}{it.promo ? ' · ' + t('line.half2') : ''}{it.mp ? ' · ' + t('line.mp', { p: it.mp.toFixed(2) }) : ''}</span></span>
        <b className="text-xl font-700 num ml-auto">{it.weigh ? t('card.kg', { p: it.p.toFixed(2) }) : money(it.p)}</b>
        <span className="help num whitespace-nowrap">{t('peek.stock', { n: it.s ?? 36 })}</span>
        <Button variant="muted" size="sm" className="bg-surface" disabled={it.s === 0} onClick={() => { r.set({ peek: false, peekId: null }); if (it.weigh) r.set({ ov: 'weigh', weighId: it.id, w: 0.5 }); else if (it.sku) r.set({ ov: 'sku', skuId: it.id, sku: null }); else addLine({ id: it.id, q: 1 }); }}>{t('peek.add')}</Button>
      </div>
    );
  }
  const last = r.lines.find((l) => l.key === r.last);
  if (!last) return <div className="min-h-40px px-pad flex items-center help">{t('quick.hint')}</div>;
  return (
    <div className="flex items-center gap-2.5 min-h-40px pl-pad pr-1 rounded-rk bg-faint text-sm">
      <span className="help">{t('scan.last')}</span><b className="ellipsis">{lineName(last, en)}</b><span className="num">{last.w != null ? last.w.toFixed(3) + ' kg' : '× ' + last.q}</span><b className="num">{money(lineAmt(last))}</b>
      <span className="ml-auto help whitespace-nowrap">{t('scan.lastN', { n: rTotals(r.lines, r.member).pcs })}</span>
      <Button variant="link" size="sm" className="hit text-sm px-2" onClick={() => undoLast(last.key)}>{t('scan.undo')}</Button>
      {last.w == null && !last.custom && <Button variant="muted" size="sm" className="bg-surface" onClick={() => bump(last.key, 1)}><Ico name="plus" />{t('scan.plus1')}</Button>}
    </div>
  );
}
function ScanPane() {
  const t = useRT(), en = useEn(); const r = useR();
  const tools: QuickItem[] = [
    { key: 'peek', label: t('tool.peek'), icon: 'search-check', on: r.peek, onClick: () => r.set({ peek: !r.peek, peekId: null, banner: '' }) },
    { key: 'custom', label: t('tool.custom'), icon: 'file-plus', onClick: addCustom },
    { key: 'drawer', label: t('tool.drawer'), icon: 'archive', onClick: openDrawer },
  ];
  const head = (
    <>
      <label className="flex items-center gap-3 h-ctl-lg px-px rounded-rk bg-wash text-lg">
        <Ico name={r.peek ? 'search-check' : 'scan-barcode'} size={22} className="flex-none" />
        {r.qty > 1 && !r.peek && <Chip act>{t('scan.qty', { n: r.qty })}</Chip>}
        {r.peek && <Chip act>{t('tool.peek')}</Chip>}
        <input placeholder={t('scan.ph')} className="flex-1 min-w-0 bg-transparent" autoComplete="off" onKeyDown={(e) => { if (e.key === 'Enter' && e.currentTarget.value.trim()) { scanCode(e.currentTarget.value.trim()); e.currentTarget.value = ''; } }} />
        <StatusTag ent="device" k="online" form="dot" label={t('entry.scanner')} />
      </label>
      <ScanStrip />
      <div className="row-between">
        <Seg value={r.cat} onChange={(v) => r.set({ cat: v })} items={RCATS.map((c) => ({ v: c.v, label: en ? c.en : c.zh }))} />
        <span className="flex items-center gap-1">
          {[2, 3, 5].map((n) => <Filter key={n} pressed={r.qty === n} disabled={r.peek} onPressedChange={(on) => r.set({ qty: on ? n : 1 })}>× {n}</Filter>)}
          <QuickBar form="icon" items={tools} className="ml-1.5" />
        </span>
      </div>
    </>
  );
  return (
    <Panel area="content" head={head}>
      <div className="grid gap-gap w-full grid-cols-[repeat(auto-fill,minmax(150px,1fr))] auto-rows-max content-start">
        {ITEMS.filter((it) => it.c.includes(r.cat)).map((it) => <ItemCard key={it.id} it={it} />)}
      </div>
    </Panel>
  );
}

/* ---------- 右 · 收款态（PayHead / CashPad / WaitBlock 与餐饮结账共用）---------- */
function PayPane() {
  const t = useRT(), tc = useT(); const r = useR(), tot = useTot();
  const { due } = usePay(), got = r.ent ? Number(r.ent) : 0, done = r.ov === 'done';
  const key = (k: string) => r.set({ ent: payment.keyInput(r.ent, k) });
  let body: ReactNode, foot: ReactNode = null;
  if (!r.pay) body = <Empty fill icon={<Ico name="hand-coins" size={44} className="text-success-text" />} title={tc('co.pickMethod')} desc={tc('co.pickMethodHint')} />;
  else if (r.pay === 'cash') body = <CashPad due={due} got={got} onKey={key} onQuick={(v) => r.set({ ent: String(v) })} onOk={() => r.set({ ov: 'done' })} />;
  else {
    const card = r.pay === 'card';
    body = <WaitBlock title={card ? t('cardWait') : t('qrWait')} hint={t('qrHint')} devLabel={card ? t('cardDev') : t('qrDev')} />;
    foot = <><Button variant="muted" onClick={() => r.set({ pay: 'cash', ent: '' })}>{t('cancelPay')}</Button><Button variant="soft" block onClick={() => r.set({ ov: 'done', ent: String(due) })}>{t('manualOk')}</Button></>;
  }
  const head = <PayHead step={done ? 4 : r.pay ? 3 : 2} rest={done ? 0 : due} due={due} paid={done ? due : 0} settled={done} value={r.pay} onChange={(v) => r.set({ pay: v, ent: '' })} pickHint={!r.pay}
    steps={[t('step1'), t('step2'), t('step3'), t('step4')]} methods={{ sv: r.member ? { desc: tc('co.svBal', { b: 520 }) } : { why: tc('co.svMember') } }} />;
  return <Panel area="content" head={head} bodyClass="flex flex-col" foot={foot ? <Acts>{foot}</Acts> : undefined}>{body}</Panel>;
}

/* ---------- 浮层 ---------- */
function WeighDialog() {
  const t = useRT(), tc = useT(), en = useEn(); const r = useR(); const it = item(r.weighId); const amt = round2(it.p * r.w);
  return (
    <Dialog open={r.ov === 'weigh'} onClose={() => r.set({ ov: '' })} ariaLabel={t('weigh.title', { n: it.n })}>
      <div className="row-between"><DialogTitle>{t('weigh.title', { n: en ? it.en : it.n })}</DialogTitle><span className="help num">{t('weigh.unit', { p: it.p.toFixed(2) })} · {it.code}</span></div>
      <DialogDesc>{t('weigh.help')}</DialogDesc>
      <div className="rounded-rk bg-wash py-5 px-px flex flex-col items-center gap-1.5">
        <b className="text-48px font-700 tracking-[-.03em] num leading-none">{r.w.toFixed(3)}<small className="text-xl font-600 ml-1.5">kg</small></b>
        <StatusTag ent="device" k="online" label={t('weigh.stable')} />
      </div>
      <Amount label={t('weigh.amt')} value={money(amt)} />
      <div className="flex gap-gap [&>button]:flex-1">
        <Button variant="muted" onClick={() => r.set({ w: round2(Math.max(0, r.w - 0.012)) })}>{t('weigh.tare')}</Button>
        <Button variant="muted" onClick={() => r.set({ w: 0 })}>{t('weigh.zero')}</Button>
        <Button variant="muted" onClick={() => toast(t('weigh.manual'))}><Ico name="keyboard" />{t('weigh.manual')}</Button>
      </div>
      <DialogActs><Button variant="muted" onClick={() => r.set({ ov: '' })}>{tc('common.cancel')}</Button><Button disabled={!r.w} onClick={() => { r.set({ ov: '' }); addLine({ id: it.id, q: 1, w: r.w }); }}>{t('weigh.add', { m: money(amt) })}</Button></DialogActs>
    </Dialog>
  );
}
function SkuDialog() {
  const t = useRT(), tc = useT(), en = useEn(); const r = useR(); const it = item(r.skuId);
  const label = r.sku ? [SKU.colors[r.sku[0]][0] + ' / ' + SKU.sizes[r.sku[1]], SKU.colors[r.sku[0]][1] + ' / ' + SKU.sizes[r.sku[1]]] as [string, string] : null;
  return (
    <Dialog wide open={r.ov === 'sku'} onClose={() => r.set({ ov: '' })} ariaLabel={t('sku.title', { n: it.n })}>
      <div className="row-between"><DialogTitle>{t('sku.title', { n: en ? it.en : it.n })}</DialogTitle><b className="text-lg num">¥{it.p.toFixed(2)}</b></div>
      <DialogDesc>{t('sku.help')}</DialogDesc>
      <div className="grid gap-1.5" style={{ gridTemplateColumns: `88px repeat(${SKU.sizes.length}, minmax(0,1fr))` }}>
        <span className="help self-end">{t('sku.color')}</span>
        {SKU.sizes.map((z) => <b key={z} className="text-center text-sm text-sub pb-0.5">{z}</b>)}
        {SKU.colors.map((c, ci) => (
          <Fragment key={ci}>
            <b className="self-center font-600">{en ? c[1] : c[0]}</b>
            {SKU.sizes.map((_, zi) => { const n = SKU.stock[ci][zi], on = r.sku?.[0] === ci && r.sku?.[1] === zi;
              return (
                <button key={zi} type="button" disabled={!n} aria-pressed={on} onClick={() => r.set({ sku: [ci, zi] })}
                  className={cn('h-ctl-lg rounded-rk bg-wash flex flex-col items-center justify-center leading-tight hover:bg-wash-strong', on && 'sel hover:bg-sel-bg', !n && 'text-placeholder hover:bg-wash cursor-default')}>
                  <span className={cn('text-sm num', n && n <= 2 && !on && 'text-warning-text font-600')}>{n ? (n <= 2 ? t('card.low', { n }) : n) : t('card.out')}</span>
                </button>
              ); })}
          </Fragment>
        ))}
      </div>
      <DialogActs><Button variant="muted" onClick={() => r.set({ ov: '' })}>{tc('common.cancel')}</Button>
        <Button disabled={!label} onClick={() => { r.set({ ov: '' }); addLine({ id: it.id, q: 1, sku: label! }); }}>{label ? t('sku.add', { s: en ? label[1] : label[0] }) : t('sku.pick')}</Button></DialogActs>
    </Dialog>
  );
}
function HeldSheet() {
  const t = useRT(), tc = useT(), en = useEn(); const r = useR();
  return (
    <Sheet open={r.ov === 'held'} onClose={() => r.set({ ov: '' })} title={t('held.title', { n: HELD.length })} closeLabel={tc('common.close')}>
      {r.lines.length > 0 && <Note>{t('held.note', { n: r.lines.length })}</Note>}
      {HELD.map((h) => (
        <StatusTile key={h.no} group="held" className="p-pad gap-1.5">
          <div className="row-between"><span className="flex items-center gap-2"><b className="num">{h.no}</b><StatusTag ent="order" k="held" /></span><b className="text-lg num">{money(h.amt)}</b></div>
          <div className="row-between text-sm text-sub"><span className="num">{h.time} · {t('held.pcs', { n: h.pcs })}{h.member && ' · ' + h.member}</span><span>{en ? h.why[1] : h.why[0]}</span></div>
          <Button variant="muted" size="sm" className="self-end bg-surface" onClick={() => { r.set({ ov: '' }); toast(t('held.take') + ' ' + h.no); }}>{t('held.take')}</Button>
        </StatusTile>
      ))}
    </Sheet>
  );
}
function ClearDialog() {
  const t = useRT(), tc = useT(); const r = useR(), tot = useTot();
  return (
    <Dialog open={r.ov === 'clear'} onClose={() => r.set({ ov: '' })} ariaLabel={t('clear.title')}>
      <DialogTitle>{t('clear.title')}</DialogTitle>
      <DialogDesc>{t('clear.desc', { n: r.lines.length, p: tot.pcs })}</DialogDesc>
      <DialogActs><Button variant="muted" onClick={() => r.set({ ov: '' })}>{tc('common.cancel')}</Button><Button variant="muted" onClick={() => { r.set({ ov: '' }); toast(t('q.hold')); }}>{t('q.hold')}</Button><Button variant="danger-solid" onClick={() => r.set({ ov: '', lines: [], last: null, open: null })}>{t('clear.ok')}</Button></DialogActs>
    </Dialog>
  );
}
function DoneDialog() {
  const t = useRT(), lang = useStore((s) => s.lang); const r = useR(), { tot, credit, st, due } = usePay(); const got = r.ent ? Number(r.ent) : due;
  const big = st?.refund ? st.refund : round2(Math.max(0, got - due));
  return (
    <Dialog open={r.ov === 'done'} onClose={() => r.set({ ov: '' })} ariaLabel={t('done.title')}>
      <Result title={credit ? t('ex.doneTitle') : t('done.title')}>
        <span className="help">{st?.refund ? t('ex.refunded', { m: payLabel(credit!.method, lang) }) : t('done.change')}</span>
        <b className="text-48px font-700 tracking-[-.03em] num leading-none">{money(big)}</b>
      </Result>
      <Cells className="mt-2">
        {credit && <><Cell k={t('ex.newItems')} v={money(tot.due)} num /><Cell k={t('ex.line', { no: credit.no, n: credit.pcs })}><span className="num text-saving font-600">−{money(credit.amt)}</span></Cell></>}
        {!st?.refund && !st?.even && <><Cell k={credit ? t('ex.paidMore') : t('due')} v={money(due)} num /><Cell k={t('done.got')} v={money(got)} num /></>}
        <Cell k={st?.refund ? t('rt.method') : t('done.method')} v={payLabel(r.pay || 'cash', lang)} />{tot.save > 0 && <Cell k={t('done.save')}><span className="num text-saving font-600">−{money(tot.save)}</span></Cell>}
        {r.member && <Cell k={t('done.points')} v="+150" num />}
      </Cells>
      {!st?.refund && <Note>{t('done.auto')}</Note>}
      <DialogActs><Button variant="muted" onClick={() => toast(t('done.reprint'))}><Ico name="printer" />{t('done.reprint')}</Button><Button onClick={() => { emit({ type: 'order.paid', biz: 'retail', orderNo: '#R0158', due, methods: [r.pay || 'cash'] }); emitSaleStock(r.lines); goScreen('empty'); }}>{t('done.next')}</Button></DialogActs>
    </Dialog>
  );
}

/* ---------- 工作台主体（系统栏、网格由 RetailWorkbench 给）---------- */
export function RetailPosWB() {
  const mode = useR((s) => s.mode);
  return (
    <>
      <CartPanel />
      {mode === 'pay' ? <PayPane /> : <ScanPane />}
      <WeighDialog /><SkuDialog /><HeldSheet /><ClearDialog /><DoneDialog />
    </>
  );
}
