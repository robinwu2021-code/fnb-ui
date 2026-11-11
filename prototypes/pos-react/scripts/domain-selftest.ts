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

eq(table.canTableTransit('to_clear', 'free'), true, '待清台 → 空闲'); eq(table.canTableTransit('free', 'to_clear'), false, '空闲不能直接待清台');

console.log(fails ? `✗ ${fails} 失败，${passes} 通过` : `✓ 全部通过（${passes} 项断言）`);
if (fails) process.exit(1);
