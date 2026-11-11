// 领域层自测：npm run test:domain（esbuild 打包后用 node 跑，不需要测试框架）
// ① 计价引擎回归：抽领域层前的餐饮 totals / 零售 rTotals 原样留在这里作「旧实现」，随机 3000 组输入逐字段比对
// ② 订单行 / 购物车 / 支付 / 商品码 / 桌台 的单元断言
import { COUPONS, DISHES, PVOUCHERS, pvOf, type Line } from '@/data/sample';
import { totals as fnbTotals } from '@/lib/pricing';
import { ITEMS, item, rTotals, type RLine } from '@/retail/data';
import * as order from '@/domain/order';
import * as cart from '@/domain/cart';
import * as payment from '@/domain/payment';
import * as catalog from '@/domain/catalog';
import * as table from '@/domain/table';
import * as member from '@/domain/member';
import * as aftersale from '@/domain/aftersale';
import * as shift from '@/domain/shift';
import * as stock from '@/domain/stock';
import * as events from '@/domain/events';
import * as pricing from '@/domain/pricing';

let fails = 0, passes = 0;
const eq = (a: unknown, b: unknown, msg: string) => { const ok = JSON.stringify(a) === JSON.stringify(b); ok ? passes++ : (fails++, console.error('✗', msg, '\n  got     ', JSON.stringify(a), '\n  expected', JSON.stringify(b))); };
const r2 = (v: number) => Math.round(v * 100) / 100;

/* ---------- 旧实现（抽层前 lib/pricing.ts 与 retail/data.ts 原文） ---------- */
function oldPvWhy(v: any, lines: Line[]) { return v.items && !v.items.every((n: string) => lines.some((l) => l.n === n && l.st !== 'void' && l.st !== 'gift')) ? 'x' : ''; }
function oldCpAmt(c: any, sub: number) { return c.disc ? Math.min(c.cap, r2(sub * c.disc)) : c.amt; }
function oldCpWhy(c: any, sub: number, lines: Line[]) { return c.min && sub < c.min ? 'x' : c.need && !lines.some((l) => l.n === c.need && l.st !== 'void') ? 'x' : ''; }
function oldTotals(s: any) {
  let sub = 0; s.lines.forEach((l: Line) => { if (l.st !== 'gift' && l.st !== 'void') sub += l.p * l.q; });
  const svc = Math.round(sub * 0.05 * 10) / 10;
  let pv = 0; s.pv.forEach((x: any) => { const v = pvOf(x.id); if (v && !oldPvWhy(v, s.lines)) pv += v.face * x.n; }); const pvFace = pv; pv = Math.min(pv, sub + svc);
  const act = !pv && s.pAct !== false && sub ? Math.min(20, r2(sub * 0.2)) : 0;
  const c = !pv && !act && s.cpn ? COUPONS.find((x) => x.id === s.cpn && !oldCpWhy(x, sub, s.lines)) ?? null : null; const cp = c ? oldCpAmt(c, sub) : 0;
  const manual = !pv && !act && !cp ? s.manual || 0 : 0;
  const raw = r2(sub + svc - pv - act - cp - manual), round = cp || pv ? 0 : r2(raw - Math.floor(raw)), due = r2(raw - round);
  const paid = s.paid.reduce((a: number, p: any) => a + p[1], 0);
  return { sub, svc, pv, pvOver: r2(pvFace - pv), act, cp, coupon: c, manual, mem: act + cp + manual, round, due, paid, rest: Math.max(0, r2(due - paid)) };
}
function oldR(lines: RLine[], member: boolean) {
  const amt = (l: RLine) => { const u = l.custom ? l.custom.p : item(l.id).p; return l.gift ? 0 : l.w != null ? r2(u * l.w) : r2(u * l.q); };
  const promo = (l: RLine) => (!l.custom && !l.gift && item(l.id).promo === 'half2' ? r2(Math.floor(l.q / 2) * item(l.id).p * 0.5) : 0);
  const mem = (l: RLine) => { const it = l.custom ? null : item(l.id); return member && it?.mp && !l.gift ? r2((it.p - it.mp) * l.q) : 0; };
  let sub = 0, p = 0, m = 0, pcs = 0; lines.forEach((l) => { sub += amt(l); p += promo(l); m += mem(l); pcs += l.w != null ? 1 : l.q; });
  sub = r2(sub); p = r2(p); m = r2(m);
  const raw = r2(sub - p - m), round = r2(raw - Math.floor(raw * 10) / 10), due = r2(raw - round);
  return { sub, promo: p, mem: m, round, due, pcs, save: r2(p + m + round) };
}

/* ---------- ① 计价回归 ---------- */
let seed = 7; const rnd = (n: number) => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed % n; };
const ST: Line['st'][] = ['new', 'cook', 'done', 'rush', 'gift', 'void'];
for (let i = 0; i < 3000; i++) {
  const lines: Line[] = Array.from({ length: 1 + rnd(7) }, () => { const d = DISHES[rnd(DISHES.length)]; return { n: d.n, q: 1 + rnd(4), p: d.p, st: ST[rnd(ST.length)] }; });
  const s = { lines, pv: rnd(3) ? [] : [{ id: PVOUCHERS[rnd(PVOUCHERS.length)].id, n: 1 + rnd(2) }], pAct: !!rnd(2), cpn: rnd(2) ? COUPONS[rnd(COUPONS.length)].id : '', manual: rnd(3) ? 0 : 10, paid: rnd(3) ? [] : [['cash', 50]] as [string, number][] };
  const a = fnbTotals(s) as any, b = oldTotals(s) as any;
  for (const k of Object.keys(b)) eq(k === 'coupon' ? a[k]?.id : a[k], k === 'coupon' ? b[k]?.id : b[k], `餐饮 #${i} ${k}`);
}
const keys = ITEMS.filter((x) => x.s !== 0);
for (let i = 0; i < 3000; i++) {
  const lines: RLine[] = Array.from({ length: rnd(9) }, (_x, j) => { const it = keys[rnd(keys.length)]; return { key: j, id: it.id, q: 1 + rnd(5), w: it.weigh ? (1 + rnd(2000)) / 1000 : undefined, gift: !rnd(15) || undefined }; });
  const member = !!rnd(2), a = rTotals(lines, member) as any, b = oldR(lines, member) as any;
  for (const k of Object.keys(b)) eq(a[k], b[k], `零售 #${i} ${k}`);
}

/* ---------- ② 单元断言 ---------- */
const kp = { n: '宫保鸡丁', p: 48, s: 2 }, timed = { n: '红糖糍粑', p: 22, s: 10, timed: '14:00 起' };
let L: Line[] = [];
L = order.addItem(L, kp); L = order.addItem(L, kp); eq(L, [{ n: '宫保鸡丁', q: 2, p: 48, st: 'new' }], '加两份合并一行');
eq(order.addCheck(kp, L), 'soldOut', '余量 2 已点 2 → 沽清'); eq(order.addCheck(timed, []), 'timed', '限时供应不能加');
L = order.decAt(order.decAt(L, 0), 0); eq(L, [], '减到 0 删行');
const sent = order.sendNew([{ n: 'a', q: 2, p: 1, st: 'new' }, { n: 'b', q: 1, p: 1, st: 'done' }]); eq([sent.n, sent.lines[0].st], [2, 'cook'], '送厨：待下单 → 制作中');
eq(order.rush(sent.lines, [0, 1]).n, 1, '只有制作中能催'); eq(order.canTransit('void', 'cook'), false, '已退不能回到制作中');
eq(order.repriced(48, 'disc', 9), 43.2, '9 折'); eq(order.repriceNeedsAuth(48, 'disc', 7), true, '7 折要授权'); eq(order.repriceNeedsAuth(100, 'off', 21), true, '一次减超 ¥20 要授权');

let C: cart.CartLine[] = []; let k = 0; const nk = () => ++k;
({ lines: C } = cart.scanAdd(C, { id: 'cola', q: 1 }, nk)); ({ lines: C } = cart.scanAdd(C, { id: 'tea', q: 1 }, nk)); const r = cart.scanAdd(C, { id: 'cola', q: 2 }, nk); C = r.lines;
eq(C.map((l) => l.id + '×' + l.q), ['cola×3', 'tea×1'], '同商品合并并移到最上'); eq(r.last, 1, '刚加的行是可乐');
({ lines: C } = cart.scanAdd(C, { id: 'apple', q: 1, w: 0.5 }, nk)); ({ lines: C } = cart.scanAdd(C, { id: 'apple', q: 1, w: 0.3 }, nk)); eq(C.filter((l) => l.id === 'apple').length, 2, '称重行不合并');
eq(cart.bump(C, C[0].key, 1), C, '称重行不能加减'); eq(cart.pieces(C), 6, '件数：称重行算 1 件');
eq(cart.promoOff('half2', 5, 2), 2, '第二件半价：5 件减 2 个半件');

eq(payment.keyInput('12.', '.'), '12.', '只允许一个小数点'); eq(payment.keyInput('0', '5'), '5', '前导 0 去掉'); eq(payment.keyInput('12345678', '9'), '12345678', '最多 8 位');
eq(payment.quickAmounts(150.9), [150.9, 160, 200, 500], '快捷金额'); eq(payment.cashResult(150.9, 200), { short: 0, change: 49.1, ok: true }, '找零');
eq(payment.methodBlock('wx', { offline: true, member: false, balance: 0, rest: 10, paidCount: 0 })?.key, 'offline', '离线不能扫码');
eq(payment.methodBlock('sv', { offline: false, member: true, balance: 5, rest: 10, paidCount: 0 }), { key: 'svShort', params: { m: '¥5.00' } }, '储值余额不足');

eq(['6954767423579', '200000101234500123000', '2000001', 'PLU1001', 'M10086', '134567890123456789', 'abc'].map(catalog.classifyCode), ['std', 'unknown', 'instore', 'plu', 'member', 'pay', 'unknown'], '码分类');
eq(catalog.parseWeighCode('210010001582012367'), { plu: '100100', amount: 15.82, weight: 1.236 }, '称重码解析：苹果 1.236 kg ¥15.82');
eq(catalog.findByCode(ITEMS, '6954 7674 23579')?.id, 'cola', '按条码找商品（忽略空格）'); eq([catalog.stockState(0), catalog.stockState(3), catalog.stockState(null)], ['out', 'low', null], '库存状态');

eq(catalog.ean13Check('695476742357'), '9', 'EAN-13 校验位'); eq(catalog.toEan13('2000001'), '2000020000019', '店内码转 EAN-13：2 + 补零到 11 位 + 校验位');
eq(catalog.toEan13('6954767423579'), '6954767423579', '13 位原样'); eq(catalog.ean13Bars('6954767423579').length, 95, 'EAN-13 共 95 个模块');
eq(catalog.ean13Bars('4006381333931'), '101' + '0001101' + '0100111' + '0101111' + '0111101' + '0001001' + '0110011' + '01010' + '1000010' + '1000010' + '1000010' + '1110100' + '1000010' + '1100110' + '101', 'EAN-13 编码：首位 4 → 奇偶 LGLLGG，右半 R 码（4006381333931）');
eq(catalog.labelUnit(12.8, 'kg'), { per: '500g', price: 6.4 }, '称重商品价签按 500g'); eq(catalog.labelUnit(3.5), null, '按件商品不标单位价');
{ const it = item('milk'), p0 = it.p; it.p = 35.5; eq(rTotals([{ key: 1, id: 'milk', q: 1 }], true).memberSave, 0, '会员价高于售价时不生效'); it.p = p0; }
eq(table.canTableTransit('to_clear', 'free'), true, '待清台 → 空闲'); eq(table.canTableTransit('free', 'to_clear'), false, '空闲不能直接待清台');

/* ---------- 会员营销 · 售后 · 班次 · 库存 · 事件 ---------- */
eq([member.matchLayer('全部', '熟客', true), member.matchLayer('线索', '新客', true), member.matchLayer('熟客', '熟客', true), member.matchLayer('熟客', '熟客', false)], [true, true, false, true], '分层筛选：线索不计入分层');
eq(member.toggleTag(Array.from({ length: 10 }, (_x, i) => 't' + i), 'new').length, 10, '标签最多 10 个'); eq(member.toggleTag(['a', 'b'], 'a'), ['b'], '标签再点去掉');
const plans = [{ n: 'a', pay: 300, gift: 30, on: false }, { n: 'b', pay: 500, gift: 60, on: true }];
eq(member.pickPlan(plans, 0).n, 'b', '选第 1 个启用的方案'); eq(member.recharge(520, plans[1]), 1080, '储值到账 = 余额 + 实付 + 赠送');
eq([member.canCamp('running', 'paused'), member.canCamp('ended', 'running'), member.canCamp('draft', 'paused')], [true, false, false], '活动状态流转');

eq([aftersale.voucherUndo({ undone: false, hoursAgo: 0.8, operator: '李敏' }, '李敏'), aftersale.voucherUndo({ undone: false, hoursAgo: 1.3, operator: '王芳' }, '李敏'), aftersale.voucherUndo({ undone: false, hoursAgo: 26, operator: '李敏' }, '李敏'), aftersale.voucherUndo({ undone: true, hoursAgo: 1, operator: '李敏' }, '李敏')], ['', 'auth', 'late', 'done'], '平台券撤销：本人 / 他人 / 超 24h / 已撤');
const sold = [{ key: 'a', q: 2, amt: 60 }, { key: 'b', q: 1, amt: 30 }, { key: 'c', q: 1, amt: 10.01, weighed: true }];
const net = aftersale.allocate(sold, 10); eq(r2(net.a + net.b + net.c), 90.01, '分摊后合计 = 整单实付');
eq(aftersale.refundAmount(sold, 10, { a: 1 }), r2(net.a / 2), '退 1 件按分摊后单件实付'); eq(aftersale.refundAmount([{ key: 'x', q: 3, amt: 30, returned: 2 }], 0, { x: 5 }), 10, '超过可退数量按可退算');
eq(aftersale.refundableQty({ key: 'w', q: 1, amt: 5, weighed: true, returned: 1 }), 0, '称重行退过就不能再退'); eq(aftersale.restockByDefault('quality'), false, '质量问题默认不回库存');

const sold2 = [{ key: 'a', q: 3, amt: 30 }, { key: 'w', q: 1, amt: 15.82, weighed: true }];
const after = aftersale.applyReturn(sold2, { a: 5, w: 1 }); eq(after.map((l) => l.returned), [3, 1], '退货记数不超过可退');
eq([aftersale.saleStatus(sold2), aftersale.saleStatus(aftersale.applyReturn(sold2, { a: 1 })), aftersale.saleStatus(after)], ['paid', 'partial_return', 'returned'], '原单退货状态');
eq([aftersale.channelBlock('platform'), aftersale.channelBlock('store')], ['platform', null], '平台单不能在门店退'); eq(aftersale.pickedPieces(sold2, { a: 2, w: 1 }), 3, '选中件数');

eq([aftersale.exchangeSettle(59, 53.1), aftersale.exchangeSettle(29, 53.1), aftersale.exchangeSettle(53.1, 53.1)], [{ pay: 5.9, refund: 0, even: false }, { pay: 0, refund: 24.1, even: false }, { pay: 0, refund: 0, even: true }], '换货：补收 / 退差价 / 等价');

eq(shift.drawerTotal([15, 3, 4, 3, 1, 5]), 1770, '钱箱实盘'); eq(shift.expectedCash({ float: 500, cashIn: 1280 }), 1780, '应有现金');
eq(shift.variance(1780, 1770), { diff: -10, st: 'short' }, '盘亏 ¥10'); eq(shift.variance(1780, 1781, 2).st, 'ok', '容差内平账');

eq(stock.receive({ a: 1 }, [{ id: 'a', q: 5 }, { id: 'b', q: 2 }]), { a: 6, b: 2 }, '入库累加');
eq(stock.countDiff({ a: 10, b: 5 }, { a: 8, b: 5, c: 1 }), { rows: [{ id: 'a', book: 10, counted: 8, diff: -2 }, { id: 'c', book: 0, counted: 1, diff: 1 }], gain: 1, loss: 2 }, '盘点差异');
eq(stock.applyCount({ a: 10, b: 5 }, { a: 8 }), { a: 8, b: 5 }, '盘点过账只改盘到的'); eq(stock.writeOff({ a: 10 }, [{ id: 'a', q: 3 }]), { a: 7 }, '报损扣减');
eq(stock.applyDelta({ a: 1.5, b: 2 }, { a: -0.236, c: 1 }), { a: 1.264, b: 2, c: 1 }, '按变化量改库存（称重保留 3 位）'); eq(stock.costTotal([{ id: 'a', q: 3 }, { id: 'b', q: 2 }], (id) => (id === 'a' ? 1.2 : 2.35)), 8.3, '入库金额');
eq([stock.nearExpiry('2026-11-13', '2026-11-11'), stock.nearExpiry('2026-11-20', '2026-11-11')], [true, false], '临期 3 天内');

const got: string[] = []; const off = events.on('order.paid', (e) => got.push(e.biz + (e.table || '')));
events.emit({ type: 'order.paid', biz: 'fnb', orderNo: '1', due: 1, table: 'A03', methods: [] }); events.emit({ type: 'order.held', biz: 'fnb', orderNo: '2' }); off();
events.emit({ type: 'order.paid', biz: 'retail', orderNo: '3', due: 1, methods: [] }); eq(got, ['fnbA03'], '事件：只收订阅的类型，取消后不再收到');
eq(pricing.rulesOf('retail').roundTo, 0.1, '按业态键取计价规则');

console.log(fails ? `✗ ${fails} 失败，${passes} 通过` : `✓ 全部通过（${passes} 项断言）`);
if (fails) process.exit(1);
