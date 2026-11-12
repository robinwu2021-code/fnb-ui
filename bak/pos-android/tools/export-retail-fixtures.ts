// 用法（在 pos-react-retail 分支的原型目录里运行；原型已冻结，只用来导出对照数据）：
//   cd prototypes/pos-react
//   npx esbuild <本文件> --bundle --platform=node --format=cjs --alias:@=./src --outfile=/tmp/x.cjs
//   node /tmp/x.cjs <android 仓库>/bak/pos-android/app/src/test/resources/retail-fixtures.json
// 从原型（prototypes/pos-react，已冻结）导出零售规则对照数据给 Android 测试用。金额一律换成「分」。
// 运行：esbuild 打包（@ 指向原型 src），node 运行，输出 JSON 到 Android 测试资源目录。
import { writeFileSync } from 'fs';
import { ITEMS, rTotals, type RLine } from '@/retail/data';
import { allocate, refundAmount, exchangeSettle, refundableQty, saleStatus, applyReturn, type SoldLine } from '@/domain/aftersale';
import { classifyCode, parseWeighCode, toEan13, ean13Bars, labelUnit } from '@/domain/catalog';
import { countDiff, applyCount } from '@/domain/stock';
import { scanAdd, bump, undoLast, pieces, promoOff, type CartLine } from '@/domain/cart';
const fen = (y: number) => Math.round(y * 100);
let seed = 11; const rnd = (n: number) => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed % n; };
const out: any = {};
out.items = ITEMS.map((it) => ({ id: it.id, p: fen(it.p), mp: it.mp != null ? fen(it.mp) : null, promo: it.promo ?? null, weigh: !!it.weigh, code: it.code }));
// 1 零售计价
const keys = ITEMS.filter((x) => x.s !== 0);
out.pricing = Array.from({ length: 2000 }, () => {
  const lines: RLine[] = Array.from({ length: rnd(9) }, (_x, j) => { const it = keys[rnd(keys.length)]; return { key: j, id: it.id, q: 1 + rnd(5), w: it.weigh ? (1 + rnd(2000)) / 1000 : undefined, gift: !rnd(15) || undefined }; });
  const member = !!rnd(2), t = rTotals(lines, member);
  return { member, lines: lines.map((l) => ({ id: l.id, q: l.q, grams: l.w != null ? Math.round(l.w * 1000) : null, gift: !!l.gift })), exp: { sub: fen(t.sub), promo: fen(t.promo), memberSave: fen(t.memberSave), round: fen(t.round), due: fen(t.due), pcs: t.pcs } };
});
// 2 退货分摊与退款
out.refund = Array.from({ length: 1000 }, (_x, i) => {
  const lines: SoldLine[] = Array.from({ length: 1 + rnd(5) }, (_y, j) => { const weighed = !rnd(5); const q = weighed ? 1 : 1 + rnd(4); return { key: 'k' + j, q, amt: (1 + rnd(20000)) / 100, returned: rnd(3) ? 0 : Math.min(q, rnd(2)), weighed }; });
  const gross = lines.reduce((a, l) => a + l.amt, 0), disc = rnd(3) ? 0 : Math.round(rnd(Math.floor(gross * 30)) ) / 100;
  const pick: Record<string, number> = {}; lines.forEach((l) => { if (rnd(2)) pick[l.key] = 1 + rnd(5); });
  const net = allocate(lines, disc);
  return { lines: lines.map((l) => ({ key: l.key, q: l.q, amt: fen(l.amt), returned: l.returned || 0, weighed: !!l.weighed })), disc: fen(disc), pick,
    exp: { net: Object.fromEntries(Object.entries(net).map(([k, v]) => [k, fen(v)])), refund: fen(refundAmount(lines, disc, pick)), refundable: lines.map(refundableQty), status: saleStatus(applyReturn(lines, pick)) } };
});
// 3 换货差价
out.exchange = Array.from({ length: 200 }, () => { const a = (rnd(30000)) / 100, b = (rnd(30000)) / 100, r = exchangeSettle(a, b); return { newDue: fen(a), credit: fen(b), exp: { pay: fen(r.pay), refund: fen(r.refund), even: r.even } }; });
// 4 条码
const codes = ['6954767423579', '6901939621271', '2000001', 'PLU 1001', 'PLU1010', 'M10086', '134567890123456789', '200100200431006180', '210010001582012367', '4006381333931', 'abc', '12345678901', '695476742357', '2100012', 'm77'];
out.codes = codes.map((c) => ({ raw: c, kind: classifyCode(c), weigh: parseWeighCode(c) && { plu: parseWeighCode(c)!.plu, amount: fen(parseWeighCode(c)!.amount), grams: Math.round(parseWeighCode(c)!.weight * 1000) }, ean13: /\d/.test(c) ? toEan13(c) : null, bars: /\d/.test(c) ? ean13Bars(toEan13(c)) : null }));
out.labelUnit = ITEMS.map((it) => ({ id: it.id, exp: labelUnit(it.p, it.weigh) ? fen(labelUnit(it.p, it.weigh)!.price) : null }));
// 5 盘点差异
out.count = Array.from({ length: 200 }, () => { const ids = ['a', 'b', 'c', 'd']; const book: Record<string, number> = {}, counted: Record<string, number> = {}; ids.forEach((id) => { if (rnd(4)) book[id] = rnd(50); if (rnd(4)) counted[id] = rnd(50); }); const d = countDiff(book, counted); return { book, counted, exp: { rows: d.rows, gain: d.gain, loss: d.loss, after: applyCount(book, counted) } }; });
// 6 购物车操作序列
out.cart = Array.from({ length: 300 }, () => {
  let lines: CartLine[] = []; let k = 0; const ops: any[] = [];
  for (let i = 0; i < 8; i++) {
    const r = rnd(10), it = keys[rnd(keys.length)];
    if (r < 6 || !lines.length) { const nl = { id: it.id, q: 1 + rnd(3), w: it.weigh ? (1 + rnd(999)) / 1000 : undefined, sku: it.sku ? (rnd(2) ? ['白 / M', 'White / M'] as [string, string] : ['黑 / L', 'Black / L'] as [string, string]) : undefined }; const res = scanAdd(lines, nl, () => ++k); lines = res.lines; ops.push({ op: 'add', id: nl.id, q: nl.q, grams: nl.w != null ? Math.round(nl.w * 1000) : null, sku: nl.sku?.[0] ?? null, last: res.last }); }
    else if (r < 8) { const key = lines[rnd(lines.length)].key, d = rnd(2) ? 1 : -1; lines = bump(lines, key, d); ops.push({ op: 'bump', key, d }); }
    else { const key = lines[rnd(lines.length)].key; lines = undoLast(lines, key); ops.push({ op: 'undo', key }); }
  }
  return { ops, exp: { lines: lines.map((l) => ({ key: l.key, id: l.id, q: l.q, grams: l.w != null ? Math.round(l.w * 1000) : null, sku: l.sku?.[0] ?? null })), pieces: pieces(lines) } };
});
out.promo = Array.from({ length: 100 }, () => { const q = rnd(10), p = (1 + rnd(9999)) / 100; return { q, p: fen(p), exp: fen(promoOff('half2', q, p)) }; });
writeFileSync(process.argv[2], JSON.stringify(out));
console.log('ok', Object.fromEntries(Object.entries(out).map(([k, v]) => [k, (v as any[]).length])));
