// 动作注册表：对应原型各模块的 ACT[名]。v 是冒号后的值；toast 走 sonner，文案用词表 key（带参数时传 [key, params]）。
import { toast as sonner } from 'sonner';
import type { PosState } from './index';
import { dish, pvOf, PAY_EN, payOf, type OrderRow } from '@/data/sample';
import { totals } from '@/lib/pricing';
import * as order from '@/domain/order';
import * as payment from '@/domain/payment';
import { setTable } from '@/domain/table';
import { money, round2 } from '@/lib/money';
import { tr } from '@/i18n';
import type { Device } from '@/data/devices';
import { livePlats } from '@/lib/channels';
import { platDef, type PlatKey, type PlatState } from '@/data/platforms';
import { DEFAULT_EXTRA, DEFAULT_QUICK, SLOTS, placeIn, readSlots, writeSlots } from '@/data/slots';
type Get = () => PosState; type Set = (p: Partial<PosState>) => void;
export type Action = (v: string, get: Get, set: Set) => void;
export const ACT: Record<string, Action> = {};
export function notify(get: Get, key: string, params?: Record<string, string | number>) { sonner(tr(get().lang, key, params)); }
export function notifyText(text: string) { sonner(text); }
/* 业务规则在 domain/*（订单行、改价、支付、桌台）；这里只做「取状态 → 调领域函数 → 写回 + 提示」 */

/* 通用 */
ACT.ov = (v, get, set) => set({ ov: v, noteDraft: v === 'note' ? get().noteDraft : null });
ACT.sheet = (v, _g, set) => set({ ov: 'sheet', sheet: v });
ACT.toast = (v, get, set) => { set({ ov: 'none' }); notify(get, v); };
ACT.wb = (v, _g, set) => set({ wb: v as PosState['wb'], ov: 'none', batch: false, sel: [] });
ACT.goorder = (_v, _g, set) => set({ wb: 'order' });
ACT.gopay = (_v, get, set) => set({ wb: 'checkout', ov: 'none', got: payment.defaultCash(totals(get()).rest) });
ACT.goset = (_v, _g, set) => set({ wb: 'settings', sg: 'display', si: 'shape', ov: 'none' });
ACT.gomember = (_v, _g, set) => set({ wb: 'members', msec: '会员', msel: 0, ov: 'none' });
ACT.banner = (v, _g, set) => set({ banner: v === 'on' });
ACT.scan = (v, get, set) => {
  if (v === 'member') { set({ member: true, mph: '', ov: 'none' }); notify(get, 'sheet.member.identified', { p: '138****6621', l: get().lang === 'en' ? 'Regular' : '熟客' }); }
};
ACT.munbind = (_v, _g, set) => set({ member: false, ov: 'none' });
ACT.mnew = (_v, _g, set) => set({ ov: 'mnew' });
ACT.mph = (v, get, set) => { const m = get().mph; set({ mph: v === 'back' ? m.slice(0, -1) : v === 'clr' ? '' : (m + v).slice(0, 11) }); };

/* 桌台 */
ACT.table = (v, _g, set) => set({ table: v });
ACT.tst = (v, _g, set) => set({ tst: v });
ACT.cleartable = (_v, get, set) => { const s = get(); set({ tables: setTable(s.tables, s.table, 'free') }); notify(get, 'tables.cleared', { t: s.table }); };
ACT.seat = (_v, get, set) => { const s = get(); set({ tables: setTable(s.tables, s.table, 'seated'), wb: 'order', lines: [] }); notify(get, 'tables.seated', { t: s.table }); };
ACT.enable = (_v, get, set) => { const s = get(); set({ tables: setTable(s.tables, s.table, 'free') }); notify(get, 'tables.enabled'); };
ACT.moved = (v, get, set) => { set({ ov: 'none' }); notify(get, 'tables.pick.moved', { t: v }); };

/* 点餐 */
ACT.cat = (v, _g, set) => set({ cat: v });
ACT.add = (v, get, set) => {
  const s = get(), d = dish(v), why = order.addCheck(d, s.lines);
  if (why === 'soldOut') { notify(get, 'toast.soldOut', { n: s.lang === 'en' ? d.en : d.n }); return; }
  if (why === 'timed') { notify(get, 'toast.timed', { n: s.lang === 'en' ? d.en : d.n, t: d.timed! }); return; }
  if (why === 'spec') { set({ spec: d.n, ov: 'x1' }); return; }
  set({ lines: order.addItem(s.lines, d) });
};
ACT.addspec = (_v, get, set) => { const s = get(); set({ lines: order.addWithSpec(s.lines, dish(s.spec), '大份 · 微辣 · 少油'), ov: 'none' }); };
ACT.inc = (v, get, set) => set({ lines: order.incAt(get().lines, +v) });
ACT.dec = (v, get, set) => set({ lines: order.decAt(get().lines, +v) });
ACT.clear = (_v, get, set) => { set({ lines: order.clearNew(get().lines) }); notify(get, 'toast.cleared'); };
ACT.send = (_v, get, set) => { const r = order.sendNew(get().lines); set({ lines: r.lines }); notify(get, 'toast.sent', { n: r.n }); };
ACT.lact = (v, get, set) => set({ lact: get().lact === +v ? -1 : +v });
ACT.rpline = (v, get, set) => set({ rpLine: +v, rpWhy: '', rpMode: get().rpMode || 'price', rpVal: null, ov: 'reprice', lact: -1 });
ACT.rpl = (v, _g, set) => set({ rpLine: +v });
ACT.rpm = (v, get, set) => { const l = get().lines[get().rpLine]; set({ rpMode: v as PosState['rpMode'], rpVal: { price: l.p - 2, disc: 9, off: 5 }[v as 'price'] }); };
ACT.rpv = (v, _g, set) => set({ rpVal: +v });
ACT.rpw = (v, _g, set) => set({ rpWhy: v });
export const rpPrice = (s: PosState, p: number) => order.repriced(p, s.rpMode || 'price', s.rpVal);
export const rpNeedsAuth = (s: PosState, p: number) => order.repriceNeedsAuth(p, s.rpMode || 'price', s.rpVal);
ACT.rpok = (_v, get, set) => {
  const s = get(), lines = order.applyReprice(s.lines, s.rpLine, s.rpMode || 'price', s.rpVal), rl = lines[s.rpLine];
  set({ lines, ov: 'none', rpWhy: '' }); notify(get, 'rp.done', { n: s.lang === 'en' ? dish(rl.n).en : rl.n, p: money(rl.p), w: tr(s.lang, s.rpWhy) });
};
ACT.holdwhy = (v, get, set) => set({ holdWhy: get().holdWhy === v ? '' : v });
ACT.holdok = (_v, get, set) => {
  const s = get(); const row: OrderRow = ['#1034', s.table, '堂食', 4, s.lines.length, '12:31', totals(s).due, 'order', 'held', ''];
  set({ orders: [row, ...s.orders], lines: [], ov: 'none', batch: false }); notify(get, 'hold.done');
};
ACT.sn = (v, get, set) => set({ shareN: Math.max(1, get().shareN + (v === '+' ? 1 : -1)) });
ACT.shareok = (_v, get, set) => { const s = get(); set({ tables: setTable(s.tables, s.table, 'shared'), ov: 'none' }); notify(get, 'share.done', { t: s.table, n: s.shareN }); };
ACT.splitsel = (_v, get, set) => { const s = get(), n0 = s.sel.length; set({ lines: order.removeAt(s.lines, s.sel), batch: false, bmode: '', sel: [] }); notify(get, 'toast.split', { n: n0 }); };
ACT.batch = (v, get, set) => {
  const s = get(), all = order.pickable(s.lines);
  if (v === 'on' || v === 'split') set({ batch: true, sel: [], bmode: v === 'split' ? 'split' : '', lact: -1, ov: 'none' });
  else if (v === 'off') set({ batch: false, sel: [], bmode: '' });
  else set({ sel: v === 'all' ? all : [] });
};
ACT.pick = (v, get, set) => { const sel = get().sel.slice(), p = sel.indexOf(+v); if (p > -1) sel.splice(p, 1); else sel.push(+v); set({ sel }); };
ACT.rushsel = (_v, get, set) => {
  const s = get(), r = order.rush(s.lines, s.sel);
  set({ lines: r.lines, batch: false, sel: [] }); if (r.n) notify(get, 'toast.rushed', { n: r.n }); else notify(get, 'toast.noRush');
};
ACT.ntag = (v, get, set) => { const dr = (get().noteDraft || get().note).slice(), q = dr.indexOf(v); if (q > -1) dr.splice(q, 1); else dr.push(v); set({ noteDraft: dr }); };
ACT.notesave = (_v, get, set) => { const s = get(); set({ note: s.noteDraft || s.note, noteDraft: null, ov: 'none' }); notify(get, 'note.saved'); };
ACT.void = (_v, get, set) => { const l0 = get().lines; set({ lines: order.voidAt(l0, l0.findIndex((l) => l.st === 'gift' || l.st === 'done')), ov: 'none' }); notify(get, 'auth.voided'); };

/* 结账 */
const pvCount = (s: PosState) => s.pv.reduce((a, x) => a + x.n, 0);
const payName = (s: PosState, k: string) => (s.lang === 'en' ? PAY_EN[k]?.[0] : payOf(k)?.name) || k;
export const pvName = (s: PosState, id: string) => { const v = pvOf(id)!; const p = v.plat; const P = { mt: ['美团·点评', 'Meituan'], dy: ['抖音', 'Douyin'], ks: ['快手', 'Kuaishou'], xhs: ['小红书', 'Xiaohongshu'] }[p]!; return s.lang === 'en' ? P[1] + ' · ' + v.en : P[0] + ' · ' + v.name; };
ACT.pay = (v, get, set) => { const rest = totals(get()).rest; set({ pay: v, ent: null, svWait: false, got: v === 'cash' ? payment.defaultCash(rest) : rest }); };
ACT.cashok = (_v, get, set) => { const s = get(), rest = totals(s).rest; set({ change: payment.cashResult(rest, s.got).change, paid: [...s.paid, ['cash', rest]], ov: 'p3' }); };
ACT.payok = ACT.svok = (_v, get, set) => { const s = get(), rest = totals(s).rest; set({ paid: [...s.paid, [s.pay, rest]], memberBal: s.pay === 'sv' ? round2(s.memberBal - rest) : s.memberBal, svWait: false, change: 0, ov: 'p3' }); };
ACT.svgo = (_v, _g, set) => set({ svWait: true });
ACT.svcancel = (_v, _g, set) => set({ svWait: false });
ACT.mixm = (v, _g, set) => set({ mixm: v });
ACT.mixadd = (_v, get, set) => { const s = get(), rest = totals(s).rest, amt = payment.takeAmount(rest, s.got); set({ paid: [...s.paid, [s.mixm, amt]], ent: null, got: Math.max(0, round2(rest - amt)) }); notify(get, 'pay.took', { m: payName(s, s.mixm), a: money(amt) }); };
ACT.undo = (v, get, set) => { const paid = get().paid.slice(); paid.splice(+v, 1); set({ paid }); notify(get, 'pay.undone'); };
ACT.pact = (_v, get, set) => { const on = !get().pAct; set({ pAct: on, cpn: on ? '' : get().cpn }); };
ACT.pvscan = (v, get, set) => { if (get().banner) { set({ ov: 'pvoucher' }); return; } set({ pvLast: v, pvN: 1, ov: 'pvoucher' }); };
ACT.pvn = (v, get, set) => { const s = get(), pv = pvOf(s.pvLast); if (pv) set({ pvN: Math.max(1, Math.min(pv.left, 3 - pvCount(s), (s.pvN || 1) + Number(v))) }); };
ACT.pvadd = (v, get, set) => { const s = get(); if (pvCount(s) + (s.pvN || 1) > 3) { notify(get, 'pv.max3'); return; } set({ pv: [...s.pv, { id: v, n: s.pvN || 1 }], pAct: false, cpn: '', manual: 0 }); notify(get, 'pv.added', { n: pvName(s, v) }); };
ACT.pvdel = (v, get, set) => { const pv = v === 'all' ? [] : get().pv.filter((x) => x.id !== v); set({ pv, pAct: pv.length ? get().pAct : true }); };
ACT.cpn = (v, get, set) => { const cpn = v === 'none' ? '' : v; set({ cpn, pAct: cpn ? false : get().pAct, ov: 'none' }); };
ACT.mval = (v, _g, set) => set({ mval: +v });
ACT.mwhy = (v, _g, set) => set({ mwhy: v });
ACT.manualok = (_v, get, set) => { const s = get(); set({ manual: s.mval, ov: 'none' }); notify(get, 'manual.done', { v: s.mval, w: tr(s.lang, s.mwhy) }); };
ACT.lines = (v, _g, set) => set({ showLines: v === 'on' });
ACT.kp = (v, get, set) => { const e = payment.keyInput(get().ent ?? '', v); set({ ent: e, got: +e || 0 }); };
ACT.got = (v, _g, set) => set({ got: +v, ent: null });
ACT.payother = (_v, get, set) => set({ ov: 'none', pay: 'cash', got: payment.defaultCash(totals(get()).rest) });
ACT.paid = (_v, get, set) => {
  const s = get(); const redeemed = { ...s.redeemed }; if (s.pv.length) redeemed['#1024'] = s.pv.map((x) => [x.id, x.n, '12:31', 0, '李敏', 'redeemed'] as const) as any;
  set({ tables: setTable(s.tables, s.table, 'to_clear'), ov: 'none', wb: 'tables', paid: [], change: 0, redeemed, cpn: '', pay: '', pv: [], pvLast: '', manual: 0, pAct: true, member: false });
  notify(get, 'pay.tablePaid', { t: s.table });
};
/* 订单 */
ACT.ost = (v, _g, set) => set({ ost: v });
ACT.och = (v, _g, set) => set({ och: v, ost: 'all' });
ACT.oper = (v, _g, set) => set({ oper: v });
ACT.osel = (v, _g, set) => set({ osel: v });
ACT.pvundo = (v, _g, set) => set({ pvu: v, pvuWhy: '', ov: 'pvundo' });
ACT.pvuwhy = (v, _g, set) => set({ pvuWhy: v });
ACT.pvundook = (_v, get, set) => {
  const s = get(), [no, i] = s.pvu.split('|'); const redeemed = { ...s.redeemed, [no]: s.redeemed[no].map((r, j) => (j === +i ? ([...r.slice(0, 5), 'undone'] as any) : r)) };
  set({ redeemed, orders: s.orders.map((o) => (o[0] === no ? ([...o.slice(0, 8), 'partial', o[9]] as OrderRow) : o)), ov: 'none' });
  notify(get, 'pvundo.done', { n: pvName(s, s.redeemed[no][+i][0]), w: tr(s.lang, s.pvuWhy) });
};

/* 菜品管理 */
const leftOf = (s: PosState, d: { n: string; p: number; s: number | null }) => order.stockLeft(d, s.lines);
const platKeys = (s: PosState) => livePlats(s).map((p) => p.key);
const markPending = (s: PosState, n: string) => { const m = { ...s.sync[n] }; platKeys(s).forEach((ch) => { if (m[ch] === 'synced') m[ch] = 'pending'; }); return { ...s.sync, [n]: m }; };
const dishName = (s: PosState, d: { n: string; en: string }) => (s.lang === 'en' ? d.en : d.n);
ACT.msub = (v, _g, set) => set({ msub: v });
ACT.mdf = (v, _g, set) => set({ mdf: v });
ACT.mdc = (v, _g, set) => set({ mdc: v });
ACT.mdsel = (v, _g, set) => set({ mdsel: +v });
ACT.mdgo = (v, _g, set) => set({ mdsel: +v, msub: '菜品' });
ACT.mcsel = (v, _g, set) => set({ mcsel: +v });
ACT.mmsel = (v, _g, set) => set({ mmsel: +v });
ACT.mssel = (v, _g, set) => set({ mssel: +v });
ACT.mdtag = (v, get, set) => { const s = get(), dishes = s.dishes.slice(), d = { ...dishes[s.mdsel] }; const tl = (d.tg || []).slice(), i = tl.indexOf(v); if (i > -1) tl.splice(i, 1); else tl.push(v); d.tg = tl; dishes[s.mdsel] = d; set({ dishes, sync: markPending(s, d.n) }); };
ACT.mdsold = (v, get, set) => { const s = get(), dishes = s.dishes.slice(), d = { ...dishes[s.mdsel] }; const was = leftOf(s, d) === 0; d.s = was ? 20 : s.lines.reduce((a, l) => (l.n === d.n && l.st !== 'void' ? a + l.q : a), 0); dishes[s.mdsel] = d; set({ dishes }); notify(get, was ? 'md.restored' : 'md.soldOut', { n: dishName(s, d) }); };
ACT.mdsave = (_v, get, set) => { const s = get(); set({ sync: markPending(s, s.dishes[s.mdsel].n) }); notify(get, 'md.saved'); };
ACT.mdnew = (_v, get) => notify(get, 'md.newDraft');
ACT.syncall = (_v, get, set) => {
  const s = get(), keys = platKeys(s); const sync: PosState['sync'] = {}; Object.keys(s.sync).forEach((n) => { sync[n] = { ...s.sync[n] }; keys.forEach((ch) => { if (sync[n][ch] === 'pending') sync[n][ch] = 'syncing'; }); });
  set({ sync, syncing: true });
  setTimeout(() => {
    const s2 = get(); const done: PosState['sync'] = {}; Object.keys(s2.sync).forEach((n) => { done[n] = { ...s2.sync[n] }; keys.forEach((ch) => { if (done[n][ch] === 'syncing') done[n][ch] = 'synced'; }); });
    const plats = { ...s2.plats }; keys.forEach((k) => (plats[k] = { ...plats[k], lastSync: '12:30' }));
    const failed = Object.keys(done).filter((n) => keys.some((k) => done[n][k] === 'failed')).length;
    set({ sync: done, plats, syncing: false, synced: true }); notify(get, 'md.syncDoneN', { n: failed });
  }, 1200);
};
ACT.sync1 = (v, get, set) => { const s = get(), m = { ...s.sync[v] }; platKeys(s).forEach((k) => { if (m[k] === 'pending') m[k] = 'synced'; }); set({ sync: { ...s.sync, [v]: m } }); notify(get, 'md.synced1', { n: v }); };
/* 会员与营销 */
ACT.msec = (v, _g, set) => set({ msec: v });
ACT.mlayer = (v, _g, set) => set({ mlayer: v });
ACT.msel = (v, _g, set) => set({ msel: +v });
ACT.mtsel = (v, _g, set) => set({ mtsel: +v });
ACT.csel = (v, _g, set) => set({ csel: +v });
ACT.cst = (v, _g, set) => set({ cst: v });
ACT.camp = (v, get, set) => { const s = get(), camps = s.camps.map((c, i) => (i === s.csel ? ([...c.slice(0, 5), v, c[6]] as typeof c) : c)); set({ camps }); notify(get, 'mb.campSt', { n: s.camps[s.csel][1], st: tr(s.lang, 'st.campaign.' + v) }); };
ACT.plan = (v, get, set) => { const plans = get().plans.map((p, i) => (i === +v ? { ...p, on: !p.on } : p)); set({ plans }); notify(get, plans[+v].on ? 'mb.planOn' : 'mb.planOff', { n: plans[+v].n }); };
ACT.plan_s = (v, _g, set) => set({ plan: +v });
ACT.rcm = (v, _g, set) => set({ rcm: +v });
ACT.rcgo = (_v, get, set) => { set({ rcState: 'wait' }); setTimeout(() => { const s = get(), p = s.plans.filter((x) => x.on)[s.plan] || s.plans[0]; const bal = s.memberBal + p.pay + p.gift; const members = s.members.slice(); members[0] = [...members[0]] as any; members[0][4] = bal; set({ rcState: 'done', memberBal: bal, members }); }, 900); };
ACT.rcdone = (_v, _g, set) => set({ rcState: '', ov: 'none' });
ACT.msave = (v, get, set) => { const s = get(); const members = [...s.members, ['张女士', '7714', '新客', [], 0, 0, 0, '今天', '手工录入', 1] as any]; set({ members, msel: members.length - 1, ov: v === 'sv' ? 'recharge' : 'none' }); if (v !== 'sv') notify(get, 'mb.savedLead'); };
ACT.mtog = (v, get, set) => { const s = get(), members = s.members.slice(), m = [...members[s.msel]] as any; const tl = m[3].slice(), i = tl.indexOf(v); if (i > -1) tl.splice(i, 1); else if (tl.length < 10) tl.push(v); m[3] = tl; members[s.msel] = m; set({ members }); };
ACT.mtagadd = (_v, get, set) => { set({ mtags: [...get().mtags, ['爱喝茶', 0]], ov: 'none' }); notify(get, 'mb.tagAdded'); };
ACT.plansave = (_v, get, set) => { set({ plans: [...get().plans, { n: '充 800 送 100', pay: 800, gift: 100, on: true }], ov: 'none' }); notify(get, 'mb.planAdded'); };
ACT.redeem = (v, get, set) => { set({ redeem: v }); if (v === 'done') notify(get, 'mb.redeemed'); };
/* 报表 · 设置 */
ACT.rsec = (v, _g, set) => set({ rsec: v });
ACT.rrange = (v, get, set) => { set({ rrange: v }); notify(get, 'rp.switched', { r: tr(get().lang, 'rp.range.' + v) }); };
ACT.rby = (v, _g, set) => set({ rby: v });
ACT.snav = (v, _g, set) => { const [g, i] = v.split('.'); if (g === 'wb') set({ wb: i as PosState['wb'], ov: 'none', batch: false, sel: [] }); else set({ sg: g, si: i }); };
ACT.qk = (v, get, set) => { const a = readSlots(get()); if (a.q.includes(v)) { set(writeSlots(placeIn(a, v, 'lib').next)); return; } if (a.q.length >= SLOTS.q.cap) { notify(get, 'set.quickMax'); return; } set(writeSlots(placeIn(a, v, 'q').next)); };
/* 布局编辑态：on 存快照 · done 保存（store 订阅自动写 localStorage）· cancel 还原快照 · reset 回默认（仍在编辑态） */
ACT.edit = (v, get, set) => {
  const s = get();
  if (v === 'on') { if (!s.edit) set({ edit: true, editSnap: { nav: s.nav, detail: s.detail, quick: s.quick, slots: s.slots }, ov: 'none', batch: false, bmode: '', sel: [] }); return; }
  if (v === 'done') { set({ edit: false, editSnap: null }); notify(get, 'edit.saved'); return; }
  if (v === 'cancel') { set({ edit: false, ...(s.editSnap || {}), editSnap: null }); return; }
  if (v === 'reset') { set({ nav: 'left', detail: 'right', quick: DEFAULT_QUICK.slice(), slots: { t: DEFAULT_EXTRA.t.slice(), s: DEFAULT_EXTRA.s.slice(), f: DEFAULT_EXTRA.f.slice() } }); notify(get, 'edit.resetDone'); }
};

/* 设备：dev:sel:<id> · dev:kind:<k> · dev:add[:<kind>] · dev:edit:<id> · dev:test:<id> · dev:reconnect:<id> · dev:pair:<id> · dev:sync:<id> · dev:paper:<id> · dev:remove:<id>
   硬件都是模拟：连接 / 配对用 1.2 秒假延时 */
const devName = (get: () => PosState, d: Device) => (get().lang === 'en' ? d.en : d.name);
export const patchDev = (get: () => PosState, set: (p: Partial<PosState>) => void, id: string, p: Partial<Device>) => set({ devices: get().devices.map((d) => (d.id === id ? { ...d, ...p } : d)) });
ACT.dev = (v, get, set) => {
  const i = v.indexOf(':'); const verb = i < 0 ? v : v.slice(0, i), arg = i < 0 ? '' : v.slice(i + 1);
  const d = get().devices.find((x) => x.id === arg);
  switch (verb) {
    case 'sel': set({ dsel: arg }); return;
    case 'kind': set({ dkind: arg as PosState['dkind'] }); return;
    case 'add': set({ ov: 'devadd', daddKind: (arg as PosState['daddKind']) || '', daddEdit: '' }); return;
    case 'edit': if (d) set({ ov: 'devadd', daddKind: d.kind, daddEdit: d.id }); return;
    case 'page': set({ wb: 'settings', sg: 'device', si: 'devices', dkind: 'all', ov: 'none' }); return;
  }
  if (!d) return;
  const n = devName(get, d);
  switch (verb) {
    case 'test':
      if (d.st === 'no_paper') notify(get, 'dv.testQueued');
      else if (d.st === 'online') notify(get, d.kind === 'cds' ? 'dv.previewSent' : 'set.testSent');
      else notify(get, 'dv.testFail', { n });
      return;
    case 'reconnect': case 'pair': {
      patchDev(get, set, d.id, { st: 'pairing' }); if (verb === 'pair') set({ dsel: d.id });
      setTimeout(() => { if (!get().devices.some((x) => x.id === d.id)) return; patchDev(get, set, d.id, { st: 'online', ...(verb === 'pair' ? { addedAt: get().lang === 'en' ? 'Today' : '今天' } : {}) }); notify(get, verb === 'pair' ? 'dv.added' : 'dv.reconnected', { n }); }, 1200);
      return;
    }
    case 'sync': patchDev(get, set, d.id, { st: 'online', lastSync: '12:30' }); notify(get, 'dv.synced', { n }); return;
    case 'paper': patchDev(get, set, d.id, { st: 'online' }); notify(get, 'dv.paperDone', { n }); return;
    case 'remove': { const rest = get().devices.filter((x) => x.id !== d.id); set({ devices: rest, dsel: rest.find((x) => x.st !== 'found')?.id || '' }); notify(get, 'dv.removed', { n }); return; }
  }
};

/* 外送平台：plat:region:<r> · plat:sel:<k> · plat:back · plat:add[:<k>] · plat:pause:<k>[:<值>] · plat:resume:<k> · plat:prep:<k>:<分钟> · plat:markup:<k>:<%>
   · plat:map:<k>:<本店分类>:<平台类目> · plat:push:<k>[:<菜单>] · plat:disconnect:<k> · plat:cred:<k> */
const patchPlat = (get: () => PosState, set: (p: Partial<PosState>) => void, k: PlatKey, p: Partial<PlatState>) => set({ plats: { ...get().plats, [k]: { ...get().plats[k], ...p } } });
ACT.plat = (v, get, set) => {
  const [verb, k, a, ...rest] = v.split(':'); const b = rest.join(':'), tail = [a, ...rest].filter((x) => x != null).join(':'); // tail：带冒号的值（如 13:00）
  const key = k as PlatKey, def = platDef(k), n = def?.name || k;
  switch (verb) {
    case 'region': set({ region: k as PosState['region'], psel: '' }); return;
    case 'sel': set({ psel: key }); return;
    case 'back': set({ psel: '' }); return;
    case 'page': set({ wb: 'settings', sg: 'channel', si: 'platforms', psel: '', ov: 'none' }); return;
    case 'gosync': set({ wb: 'menu', msub: '同步', syncObj: 'dish', ov: 'none' }); return;
    case 'add': set({ ov: 'platadd', paddKey: (key || '') as PosState['paddKey'] }); return;
  }
  if (!def) return;
  switch (verb) {
    case 'pause': patchPlat(get, set, key, { st: 'paused', pausedUntil: def.pause === 'until' ? tail : undefined, pauseWhy: def.pause === 'reason' ? tail : undefined }); notify(get, 'pf.pausedToast', { n }); return;
    case 'resume': patchPlat(get, set, key, { st: 'connected', pausedUntil: undefined, pauseWhy: undefined }); notify(get, 'pf.resumedToast', { n }); return;
    case 'prep': patchPlat(get, set, key, { prep: Number(a) }); return;
    case 'markup': patchPlat(get, set, key, { markup: Number(a) }); return;
    case 'map': patchPlat(get, set, key, { map: { ...get().plats[key].map, [a]: b } }); return;
    case 'push': {
      const s = get(); const sync: PosState['sync'] = {}; Object.keys(s.sync).forEach((d) => { sync[d] = { ...s.sync[d] }; if (sync[d][key] === 'pending') sync[d][key] = 'synced'; });
      const menu = a || 'all'; patchPlat(get, set, key, { lastSync: '12:30', menuPush: { ...s.plats[key].menuPush, [menu]: '12:30' } }); set({ sync });
      notify(get, 'pf.pushed', { n }); return;
    }
    case 'disconnect': patchPlat(get, set, key, { st: 'disconnected' }); notify(get, 'pf.disconnected', { n }); return;
    case 'cred': patchPlat(get, set, key, { credSaved: true }); notify(get, 'pf.credSaved', { n }); return;
  }
};
