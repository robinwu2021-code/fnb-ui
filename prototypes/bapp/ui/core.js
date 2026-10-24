/* 全线点餐 POS · UI 原型 · 核心：状态（S，存 localStorage）、示例数据、金额计算、业务状态（pos-states.js）与各模块共用的小件。
 * 本目录的脚本都是普通脚本、按 pos-ui.html 里的顺序加载，顶层声明在文件之间共享。 */
/* 注册表：各模块把浮层登记到 OVL[S.ov]、把动作登记到 ACT[动作名]；分发在 app.js（overlay、act） */
var OVL = {}, ACT = {};
var root = document.documentElement, KEY = 'pos-ui';
var S = { wb: 'order', ov: 'none', sheet: 'set', nav: 'left', detail: 'right', skin: 'mono', font: 'inter', frame: 'f14', btn: 'b8', density: 'compact', card: 'v', sg: 'display', si: 'shape', pic: 'img', banner: 'off', lang: 'zh', names: 'bi', colors: 'v2', tone: 'quiet',
          cat: '热销', table: 'A03', pay: 'cash', got: 300, spec: '', toast: '', load: 'off', oview: 'list', ost: 'all', och: '全部', oper: '今天', osel: '#1024', rsec: '概况', rrange: '今天', rby: 'qty', msub: '菜品', mdf: 'all', mdc: '全部', mdsel: 0, mcsel: 0, mmsel: 0, mssel: 0, syncing: false, synced: false, msec: '会员', mlayer: '全部', msel: 0, mtsel: 0, csel: 0, cst: 'all', plan: 0, rcm: 0, rcState: '', redeem: '', mph: '', paid: [], mixm: 'cash', cpn: '', manual: 0, mval: 0, mwhy: '', change: 0, svWait: false, pAct: true, lines: false, ent: null, member: false, quick: ['member', 'hold', 'split', 'share', 'reprice'], lact: -1, bmode: '', rpLine: 2, rpMode: 'disc', rpVal: 9, rpWhy: '', shareN: 2, holdWhy: '', batch: false, sel: [], note: ['少辣'], noteDraft: null };
var STYLE = ['quick', 'oview', 'load', 'wb', 'nav', 'detail', 'skin', 'font', 'frame', 'btn', 'card', 'sg', 'si', 'density', 'pic', 'banner', 'lang', 'names', 'colors', 'tone'];
try { var saved = JSON.parse(localStorage.getItem(KEY) || '{}'); STYLE.forEach(function (k) { if (saved[k]) S[k] = saved[k]; }); } catch (e) {}
if (S.tone !== 'color') S.tone = 'quiet';   /* 色系：默认黑白灰；旧存档里的 full（原默认）不再沿用 */
if (!/^(img|off)$/.test(S.pic)) S.pic = 'img';

var CATS = [['热销', 'red', 'Popular'], ['招牌', 'rose', 'Signature'], ['凉菜', 'green', 'Cold'], ['热菜', 'orange', 'Hot'], ['主食', 'yellow', 'Staples'], ['汤羹', 'teal', 'Soups'], ['饮品', 'blue', 'Drinks'], ['套餐', 'purple', 'Sets']];
var CK = {}; CATS.forEach(function (c) { CK[c[0]] = c[1]; }); CK['甜品'] = 'brown';
var DISHES = [
  { img: 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/8e/Kung_Pao_Chicken_at_Yujiayan_Restaurant_%2820230510123120%29.jpg/500px-Kung_Pao_Chicken_at_Yujiayan_Restaurant_%2820230510123120%29.jpg', tg: ['招牌', '辣'], n: '宫保鸡丁', en: 'Kung Pao Chicken', p: 48, c: '热菜', hot: 1, spec: 1, s: 32 }, { img: 'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/44/Shuizhuyu_at_Hamarkand_Silk_Road_Cuisine%2C_Beiqinglu_%2820230121154615%29.jpg/500px-Shuizhuyu_at_Hamarkand_Silk_Road_Cuisine%2C_Beiqinglu_%2820230121154615%29.jpg', tg: ['招牌', '辣'], n: '水煮鱼', en: 'Sichuan Boiled Fish', p: 88, c: '招牌', hot: 1, spec: 1, s: 8 },
  { img: 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/13/%E6%B9%98%E8%8F%9C%E9%A6%86%E4%B9%8B%E5%B0%8F%E7%82%92%E9%BB%84%E7%89%9B%E8%82%89.jpg/500px-%E6%B9%98%E8%8F%9C%E9%A6%86%E4%B9%8B%E5%B0%8F%E7%82%92%E9%BB%84%E7%89%9B%E8%82%89.jpg', n: '小炒黄牛肉', en: 'Stir-fried Beef', p: 68, c: '热菜', hot: 1, s: 15 }, { tg: ['素'], n: '蒜蓉西兰花', en: 'Garlic Broccoli', p: 28, c: '凉菜', noimg: 1, s: 40 },
  { img: 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/88/Chen_Mapo_Tofu.jpg/500px-Chen_Mapo_Tofu.jpg', tg: ['辣'], n: '麻婆豆腐', en: 'Mapo Tofu', p: 26, c: '热菜', hot: 1, s: 26 }, { img: 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/54/%E9%85%B8%E8%8F%9C%E9%B1%BC_Preserved_Mustard_Green_with_Fish_-_Charming_Spice_AUD24.80_%284104355401%29.jpg/500px-%E9%85%B8%E8%8F%9C%E9%B1%BC_Preserved_Mustard_Green_with_Fish_-_Charming_Spice_AUD24.80_%284104355401%29.jpg', n: '酸菜鱼', en: 'Pickled Cabbage Fish', p: 78, c: '招牌', hot: 1, s: 5 },
  { img: 'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/45/Fuqi_Feipian.jpg/500px-Fuqi_Feipian.jpg', n: '夫妻肺片', en: 'Spicy Beef Slices', p: 38, c: '凉菜', hot: 1, s: 12 }, { tg: ['素'], n: '拍黄瓜', en: 'Smashed Cucumber', p: 16, c: '凉菜', noimg: 1, s: 50 },
  { n: '米饭', en: 'Steamed Rice', p: 3, c: '主食', hot: 1, noimg: 1, s: null }, { img: 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d2/Dan-dan_noodles%2C_Shanghai.jpg/500px-Dan-dan_noodles%2C_Shanghai.jpg', n: '担担面', en: 'Dan Dan Noodles', p: 22, c: '主食', s: 0 },
  { n: '番茄蛋汤', en: 'Tomato Egg Soup', p: 18, c: '汤羹', noimg: 1, s: 20 }, { img: 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/60/Sour_plum_drink.jpg/500px-Sour_plum_drink.jpg', tg: ['新品'], n: '酸梅汤', en: 'Sour Plum Drink', p: 12, c: '饮品', hot: 1, s: 60 },
  { n: '鲜榨橙汁', en: 'Fresh Orange Juice', p: 18, c: '饮品', noimg: 1, s: 3 }, { tg: ['新品'], timed: '14:00 起', n: '红糖糍粑', en: 'Brown Sugar Rice Cake', p: 22, c: '甜品', noimg: 1, s: 10 },
  { tg: ['套餐'], n: '双人套餐', en: 'Set for Two', p: 168, c: '套餐', spec: 1, noimg: 1, s: 6 }];
/* 余量 = 库存 − 已点；≤ 5 为低库存，0 为沽清；null 为不限量 */
function left(d) { return d.s == null ? null : Math.max(0, d.s - qtyOf(d.n)); }
function dish(n) { for (var i = 0; i < DISHES.length; i++) if (DISHES[i].n === n) return DISHES[i]; return { n: n, p: 0, c: '' }; }
/* 订单行：st = new 待下单 · cook 制作中 · done 已上 · rush 催菜中 · gift 赠送 · void 已退 */
var LINES = [
  { n: '宫保鸡丁', q: 1, p: 48, sub: '大份 · 微辣 · 少油', st: 'new' }, { n: '米饭', q: 4, p: 3, st: 'new' },
  { n: '酸菜鱼', q: 1, p: 78, st: 'cook' }, { n: '夫妻肺片', q: 1, p: 38, st: 'done' }, { n: '小炒黄牛肉', q: 1, p: 68, st: 'rush' }, { n: '酸梅汤', q: 1, p: 12, st: 'gift', sub: '原价 ¥12.00' }];
var TABLES = [];
/* 桌台状态取业务状态字典 table 实体：空闲 · 已预订 · 待点餐 · 用餐中 · 超时未上菜 · 待结账 · 部分支付 · 待清台 · 并入 · 拼桌 · 停用 */
['dining', 'dining', 'dining', 'free', 'due', 'late', 'seated', 'booked', 'dining', 'free', 'partial', 'due', 'free', 'dining', 'to_clear', 'shared', 'free', 'disabled'].forEach(function (st, i) {
  TABLES.push({ id: i < 12 ? 'A' + ('0' + (i + 1)).slice(-2) : 'V0' + (i - 11), st: st, seats: i < 12 ? 4 : 10, ppl: 2 + i % 3, min: 15 + i * 7, amt: 120 + i * 31 });
});
function money(v) { return '¥' + v.toFixed(2); }
/* 优惠规则取自 ai-shop 餐饮提案（TDD-三行业优惠规则）：B01 参加活动就不能用券 · B07 用了活动或券不能再人工打折 · B08 用券后不能抹零 */
var COUPONS = [{ id: 'c15', name: '满 200 减 15', kind: '满减券', amt: 15, min: 200 }, { id: 'drink', name: '饮品体验券', kind: '兑换券', amt: 12, need: '酸梅汤' },
  { id: 'c30', name: '满 300 减 30', kind: '满减券', amt: 30, min: 300 }, { id: 'd8', name: '8 折券 · 最多减 ¥30', kind: '折扣券', disc: 0.2, cap: 30 }];
/* 平台券（团购券）：美团·点评、抖音、快手、小红书等平台卖出的券，到店验券后抵扣；钱由平台结算给门店，报表记「平台券抵扣」，不算门店优惠。
 * 默认规则：不与店内活动、本店券、人工折扣同享，用后不抹零；一单最多 3 张；代金券不找零；套餐券按券里的菜抵扣。 */
var PLATS = [['mt', '美团·点评', '#FFC300', '美', '#181A1C', '已授权', 'Meituan'], ['dy', '抖音', '#161823', '抖', '#FFFFFF', '已授权', 'Douyin'], ['ks', '快手', '#FF4906', '快', '#FFFFFF', '未授权', 'Kuaishou'], ['xhs', '小红书', '#FF2442', '红', '#FFFFFF', '未开通', 'Xiaohongshu']];
var PVOUCHERS = [
  { id: 'mt100', plat: 'mt', kind: '代金券', name: '100 元代金券', en: '¥100 voucher', face: 100, price: 88, left: 2, code: '6021 8873 5510', till: '2026-12-31' },
  { id: 'dy116', plat: 'dy', kind: '套餐券', name: '招牌双拼套餐', en: 'Signature set for two', face: 116, price: 99, left: 1, items: ['酸菜鱼', '夫妻肺片'], code: '8801 2297 3364', till: '2026-11-30' }];
var PVERR = { used: ['mt', '券已使用', '这张券 10-21 19:02 已在本店核销，不能重复使用'], ks: ['ks', '平台未授权', '快手团购还没有授权给本店；到「设置 › 外卖与渠道 › 团购验券」授权后再验'] };
function plat(k) { return PLATS.filter(function (p) { return p[0] === k; })[0]; }
function pvOf(id) { return PVOUCHERS.filter(function (v) { return v.id === id; })[0]; }
function pvWhy(v) { return v.items && !v.items.every(function (n) { return LINES.some(function (l) { return l.n === n && l.st !== 'void' && l.st !== 'gift'; }); }) ? '本单没有券里的菜：' + v.items.join('、') : ''; }
/* 平台券的名称、说明在代码里按语言拼好（数字、券码混在句子里，查表翻译不稳），外层加 data-notr */
function pvName(v) { var p = plat(v.plat); return S.lang === 'en' ? p[6] + ' · ' + v.en : p[1] + ' · ' + v.name; }
function pvCount() { return (S.pv || []).reduce(function (a, x) { return a + x.n; }, 0); }
function cpAmt(c, sub) { return c.disc ? Math.min(c.cap, Math.round(sub * c.disc * 100) / 100) : c.amt; }
function cpWhy(c, sub) { return c.min && sub < c.min ? '还差 ' + money(c.min - sub) + ' 可用' : c.need && !LINES.some(function (l) { return l.n === c.need && l.st !== 'void'; }) ? '本单没有可兑换的饮品' : ''; }
function totals() {
  var sub = 0; LINES.forEach(function (l) { if (l.st !== 'gift' && l.st !== 'void') sub += l.p * l.q; });
  var svc = Math.round(sub * 0.05 * 10) / 10;
  var pv = 0; (S.pv || []).forEach(function (x) { var v = pvOf(x.id); if (v && !pvWhy(v)) pv += v.face * x.n; }); var pvFace = pv; pv = Math.min(pv, sub + svc);   /* 平台券：不找零 */
  var act = !pv && S.pAct !== false && sub ? Math.min(20, Math.round(sub * 0.2 * 100) / 100) : 0;          /* 非高峰 8 折，最多减 20（AP020）；用了平台券不参加 */
  var c = !pv && !act && S.cpn ? COUPONS.filter(function (x) { return x.id === S.cpn && !cpWhy(x, sub); })[0] : null, cp = c ? cpAmt(c, sub) : 0;
  var manual = !pv && !act && !cp ? (S.manual || 0) : 0;
  var raw = Math.round((sub + svc - pv - act - cp - manual) * 100) / 100, round = cp || pv ? 0 : Math.round((raw - Math.floor(raw)) * 100) / 100, due = Math.round((raw - round) * 100) / 100;
  var paid = (S.paid || []).reduce(function (a, p) { return a + p[1]; }, 0);
  return { sub: sub, svc: svc, pv: pv, pvOver: Math.round((pvFace - pv) * 100) / 100, act: act, cp: cp, coupon: c, manual: manual, mem: act + cp + manual, round: round, due: due, paid: paid, rest: Math.max(0, Math.round((due - paid) * 100) / 100) };
}
function qtyOf(n) { var q = 0; LINES.forEach(function (l) { if (l.n === n && l.st !== 'void') q += l.q; }); return q; }
function newCount() { var c = 0; LINES.forEach(function (l) { if (l.st === 'new') c += l.q; }); return c; }

function mark(n) { var c = dish(n).c; return '<i class="dn-mark k-' + (CK[c] || 'gray') + '"></i>'; }
/* 业务状态：取 pos-states.js（由 tools/gen-colors.py 生成）。图标 + 文字，chip 组加浅底；label 传入时用它（会走界面翻译），否则按语言取状态名 */
var PS = window.POS_STATES || { groups: {}, states: {} };
function stv(g) { return '--d:var(--c-st-' + g + '-dot);--t:var(--c-st-' + g + '-text);--b:var(--c-st-' + g + '-tint);--bk:var(--c-st-' + g + '-block);--bks:var(--c-st-' + g + '-block-strong)'; }
function stx(ent, key, label, form) {
  var st = (PS.states[ent] || {})[key]; if (!st) return label || key;
  var g = PS.groups[st.group] || { form: 'dot' };
  var txt = label != null ? label : '<span data-notr="1">' + (S.lang === 'en' ? st.en : st.zh) + '</span>';
  return '<span class="st-tag ' + (form || g.form) + '" style="' + stv(st.group) + '"><i data-lucide="' + st.icon + '"></i>' + txt + '</span>';
}
function stIcon(ent, key) { var st = (PS.states[ent] || {})[key]; return st ? '<span class="st-ic" style="' + stv(st.group) + '"><i data-lucide="' + st.icon + '"></i></span>' : ''; }
var STMAP = { new: 'draft', cook: 'cooking', done: 'served', rush: 'rushed', gift: 'comp', void: 'voided' };
var STAT = { cook: ['ok', '制作中'], done: ['', '已上'], rush: ['warn', '催菜中'], gift: ['act', '赠送'], void: ['bad', '已退'], new: ['', '待下单'] };
function chipOf(st) { return stx('item', STMAP[st]); }
function stat(st) { return stx('item', STMAP[st], null, 'dot'); }
/* 订单行：色块小方块 · 菜名 · 金额 / 做法或状态 · 份数或步进器 */
function stepper(q, dec, inc, cls) { return '<span class="sh-stepper' + (cls ? ' ' + cls : '') + '"><span' + (dec ? ' data-act="' + dec + '"' : '') + ' role="button" aria-label="减少">' + ico('minus') + '</span><b>' + q + '</b><span' + (inc ? ' data-act="' + inc + '"' : '') + ' role="button" aria-label="增加">' + ico('plus') + '</span></span>'; }
function oline(l, i, edit) {
  var pr = l.st === 'gift' ? '<s>' + money(l.p * l.q) + '</s> ¥0.00' : money(l.p * l.q);
  var meta = edit ? (l.sub || '¥' + l.p + ' / 份') : stat(l.st) + (l.rp ? stx('item', 'repriced', null, 'dot') : '') + (l.sub && l.st !== 'gift' ? '<span>' + l.sub + '</span>' : '');
  var side = edit ? stepper(l.q, 'dec:' + i, 'inc:' + i) : '×' + l.q;
  var ck = !edit && S.batch && l.st !== 'void', on = ck && S.sel.indexOf(i) > -1;
  if (ck) return '<div class="dn-oline dn-oline--ck' + (on ? ' is-on' : '') + '" data-act="pick:' + i + '" role="checkbox" aria-checked="' + on + '"><i class="sh-check' + (on ? ' is-on' : '') + '"></i><b class="nm">' + l.n + '</b><span class="pr">' + pr + '</span><span class="meta">' + meta + '</span><span class="side">' + side + '</span></div>';
  /* 已下单行点一下展开行内操作（调价 · 赠送 · 退菜 · 转菜），再点收起 */
  var tap = !edit && l.st !== 'void' && S.wb === 'order', open = tap && S.lact === i;
  var lacts = open ? '<span class="lacts">' + btn('调价', 'sh-btn--muted sh-btn--sm', 'rpline:' + i) + btn('赠送', 'sh-btn--muted sh-btn--sm', 'ov:x2') + btn('转菜', 'sh-btn--muted sh-btn--sm', 'ov:x3') + btn('退菜', 'sh-btn--danger sh-btn--sm', 'ov:x2') + '</span>' : '';
  return '<div class="dn-oline' + (l.st === 'void' ? ' is-void' : '') + (open ? ' is-open' : '') + '"' + (tap ? ' data-act="lact:' + i + '"' : '') + '>' + mark(l.n) + '<b class="nm">' + l.n + '</b><span class="pr">' + pr + '</span><span class="meta">' + meta + '</span><span class="side">' + side + '</span>' + lacts + '</div>';
}
/* 图标一律 Lucide（线宽 1.75）；只有图标的按钮必须传 label（写进 aria-label） */
function ico(name) { return '<i data-lucide="' + name + '"></i>'; }
function btn(txt, cls, act, label) { return '<span class="sh-btn ' + (cls || '') + '"' + (act ? ' data-act="' + act + '"' : '') + (label ? ' role="button" aria-label="' + label + '"' : '') + '>' + txt + '</span>'; }
/* 双语名：第一行是界面语言，第二行（双语时）是另一种语言；不走界面翻译 */
function biName(zh, en) {
  var a = S.lang === 'en' ? en : zh, b = S.lang === 'en' ? zh : en;
  return '<span class="sh-biname" data-notr="1"><b>' + a + '</b>' + (S.names === 'bi' ? '<small>' + b + '</small>' : '') + '</span>';
}
function search(ph, extra) { return '<div class="sh-search">' + ico('search') + '<span>' + ph + '</span>' + (extra || '') + '</div>'; }
function rail(items, cur, key) {
  return '<nav class="ui-nav dn-rail' + (S.nav === 'top' ? ' dn-rail--h' : '') + '">' + items.map(function (it) {
    if (it.cap) return S.nav === 'top' ? '' : '<span class="cap">' + it.cap + '</span>';
    return '<span class="' + (it.v === cur ? 'is-on' : '') + '" data-act="' + key + ':' + it.v + '">' + (it.icon ? '<span class="mlabel">' + it.icon + '<span data-notr="1">' + it.label + '</span></span>' : it.k ? '<span class="mlabel"><i class="dn-mark k-' + it.k + '"></i>' + (it.en ? biName(it.v, it.en) : it.v) + '</span>' : it.en ? biName(it.label || it.v, it.en) : it.v) + (it.dot ? '<i></i>' : it.em != null ? '<em>' + it.em + '</em>' : '') + '</span>';
  }).join('') + '</nav>';
}

var MEMBER = { name: '王女士', phone: '138****6621', layer: '熟客', tags: ['爱吃辣', '带小孩'], bal: 520, coupons: 2, since: '2025-03' };
/* 结单区 v4：头（桌号 · 状态 · 会员 · 一行信息含备注 · 快捷操作一排）· 中间块色清单（待下单白卡 → 已下单 → 费用与优惠）· 底部白色窄条（应收 + 操作） */
function adjRows(t, opt) {
  var c = t.coupon, act = t.act > 0, r = function (icon, name, amt, cls, a) { return '<div class="dn-promo' + (cls ? ' ' + cls : '') + '"' + (a ? ' data-act="' + a + '" role="button"' : '') + '>' + ico(icon) + '<span class="tx">' + name + '</span><span class="am">' + amt + '</span></div>'; };
  return '<div class="dn-block"><div class="dn-sec"><span>费用与优惠</span>' + (opt && opt.head || '') + '</div><div class="dn-promos dn-promos--sm">' +
    r('receipt', '服务费 5%', '+' + money(t.svc)) +
    (act ? r('badge-percent', '非高峰 8 折 · 活动', '−' + money(t.act), 'is-neg', S.wb === 'checkout' ? 'pact' : '') : '') +
    (t.pv ? r('ticket-check', '平台券 · ' + pvCount() + ' 张', '−' + money(t.pv), 'is-neg', 'ov:pvoucher') : '') +
    (c ? r('ticket', c.kind + ' · ' + c.name, '−' + money(t.cp), 'is-neg', 'ov:coupons') : r('ticket', t.pv ? '优惠券 · 已用平台券，不能用券' : act ? '优惠券 · 本单已参加活动，不能用券' : '优惠券 · 识别会员或扫券码', '', 'is-off', act || t.pv ? '' : 'ov:coupons')) +
    (t.pv ? '' : r('ticket-check', '平台券 · 美团、抖音等团购券', '', 'is-off', 'ov:pvoucher')) +
    (t.manual ? r('hand-coins', '整单折扣 · ' + (S.mwhy || ''), '−' + money(t.manual), 'is-neg', 'ov:manual') : '') +
    (t.round ? r('scissors', '抹零', '−' + money(t.round), 'is-neg') : '') +
    (opt && opt.extra || '') + '</div></div>';
}
/* 白色窄底：金额行（label 为空时不出）+ 操作 */
function dueFoot(label, amt, note, acts, cls) {
  return '<div class="ft dn-foot">' + (label ? '<div class="dn-due2' + (cls ? ' ' + cls : '') + '"><span>' + label + '<small>' + note + '</small></span><b>' + amt + '</b></div>' : '') + (acts || '') + '</div>';
}
function dishCount() { return LINES.filter(function (l) { return l.st !== 'void'; }).length; }
/* 数字键：退格用图标，功能键字小一号 */
function key(k, input) {
  var act = input ? ' data-act="kp:' + (k === '⌫' ? 'back' : k === '清空' ? 'clr' : k) + '"' : '';
  return k === '⌫' ? '<span class="fn" role="button" aria-label="退格"' + act + '>' + ico('delete') + '</span>' : '<span class="' + (/[^0-9.]/.test(k) && k !== '00' ? 'fn' : '') + '"' + (k === '取消' ? ' data-act="ov:none"' : act) + '>' + k + '</span>';
}
function panel(head, body, foot, cls) { return '<section class="ui-main sh-panel' + (cls ? ' ' + cls : '') + '"><div class="hd">' + head + '</div><div class="bd">' + body + '</div>' + (foot ? '<div class="ft">' + foot + '</div>' : '') + '</section>'; }
/* 结单类详情栏（P2）：头 · 块色清单 · 白色窄底（foot 用 dueFoot 生成，可为空） */
function ledger(head, body, foot) { return '<aside class="ui-detail sh-panel"><div class="hd">' + head + '</div><div class="bd dn-ledger">' + body + '</div>' + (foot || '') + '</aside>'; }
function side(head, body, foot) { return '<aside class="ui-detail sh-panel"><div class="hd">' + head + '</div><div class="bd">' + body + '</div>' + (foot ? '<div class="ft">' + foot + '</div>' : '') + '</aside>'; }
function stName(ent, key) { var st = (PS.states[ent] || {})[key]; return st ? (S.lang === 'en' ? st.en : st.zh) : key; }
function stGroup(ent, key) { var st = (PS.states[ent] || {})[key]; return st ? st.group : 'closed'; }
