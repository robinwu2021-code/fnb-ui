/* 模块 · 点餐：菜单宫格、结单区 v4（快捷操作、待下单 / 已下单、费用与优惠、白色窄底）、规格 / 挂单 / 拼桌 / 调价 / 备注 / 更多菜单。 */
/* 菜品卡：有图模式出图文卡（有实拍图出图，没图的菜把菜名放大写在图位上），无图模式出纯文字卡；
   图文卡又分上下（图在上）和左右（图在左，方形）。菜名、第二语言名、价格、库存四项两种卡都有。 */
/* 菜品标记（属性，可多个）：不用状态色，Lucide 图标 + 次要色，悬停 / 读屏给出名字 */
var TAGS = { 招牌: 'award', 新品: 'sparkle', 辣: 'flame', 素: 'leaf', 称重: 'scale', 套餐: 'layers' };
function tagIcons(d) { return (d.tg || []).map(function (t) { return '<span class="dn-tag" title="' + t + '" aria-label="' + t + '">' + ico(TAGS[t]) + '</span>'; }).join(''); }
function dishCard(d) {
  var q = qtyOf(d.n), rest = left(d), out = rest === 0, k = ' k-' + (CK[d.c] || 'gray');
  var state = out || d.timed ? ' is-off' : q ? ' is-on' : '';
  var first = S.lang === 'en' ? d.en : d.n, second = S.lang === 'en' ? d.n : d.en;
  var names = '<span class="nms"><b class="nm" data-notr="1">' + first + '</b>' + (S.names === 'bi' || d.tg ? '<span class="en">' + tagIcons(d) + (S.names === 'bi' ? '<span data-notr="1">' + second + '</span>' : '') + '</span>' : '') + '</span>';
  var stock = d.timed ? '<span class="stock">' + stx('stock', 'timed', d.timed, 'dot') + '</span>' : out ? '<span class="stock">' + stx('stock', 'sold_out', null, 'dot') + '</span>' : rest == null ? '<span class="stock">库存充足</span>' : rest <= 5 ? '<span class="stock">' + stx('stock', 'low', '仅剩 ' + rest, 'dot') + '</span>' : '<span class="stock">库存 ' + rest + '</span>';
  var foot = '<span class="dfoot"><span class="p">' + (d.spec ? '¥' + d.p + ' 起' : '¥' + d.p) + '</span>' + stock + '</span>';
  var badge = q ? '<em class="dn-qty">' + q + '</em>' : '', act = ' data-act="add:' + d.n + '"';
  if (S.pic === 'off') return '<span class="dn-dish dn-dish--txt' + k + state + '"' + act + '><span class="nmrow"><i class="dn-mark' + k + '"></i>' + names + '</span>' + foot + badge + '</span>';
  var h = S.card === 'h', slot;
  if (d.img) slot = '<img src="' + d.img + '" alt="' + first + '" loading="lazy">';
  else if (h) slot = '<span class="slot" data-notr="1"><b>' + (S.lang === 'en' ? d.en.split(' ').map(function (w) { return w[0]; }).join('').slice(0, 3) : d.n.slice(0, 2)) + '</b></span>';
  else slot = '<span class="slot" data-notr="1"><b>' + first + '</b>' + (S.names === 'bi' ? '<small>' + second + '</small>' : '') + '</span>';
  var pic = '<span class="pic' + (d.img ? '' : ' txt') + '">' + slot + (out ? '<span class="sh-chip sh-chip--on-card">已沽清</span>' : '') + badge + '</span>';
  var info = d.img || h ? '<span class="nmrow"><i class="dn-mark' + k + '"></i>' + names + '</span>' + foot : foot;
  return '<span class="dn-dish dn-dish--img' + (h ? ' dn-dish--h' : '') + k + state + '"' + act + '>' + pic + '<span class="info">' + info + '</span></span>';
}
/* 骨架：形状和真卡片一样，数据回来前占位 */
function skelDish() { return '<span class="dn-dish dn-dish--skel" aria-hidden="true">' + (S.pic === 'img' && S.card !== 'h' ? '<span class="sh-skel sh-skel--pic"></span>' : '') + '<span class="sh-skel sh-skel--l"></span><span class="sh-skel sh-skel--s"></span></span>'; }
function orderWB() {
  var nav = rail(CATS.map(function (c) { return { v: c[0], k: c[1], en: c[2], dot: DISHES.some(function (d) { return (c[0] === '热销' ? d.hot : d.c === c[0]) && qtyOf(d.n); }) }; }), S.cat, 'cat');
  var list = DISHES.filter(function (d) { return S.cat === '热销' ? d.hot : d.c === S.cat; });
  var cards = S.load === 'on' ? [0, 1, 2, 3, 4, 5, 6, 7].map(skelDish).join('') : list.map(dishCard).join('');
  var main = panel('<div class="sh-toolbar">' + search('搜索菜名 / 拼音首字母 / 编号', '<span class="scan">扫码</span>') +
    '<span class="sh-filter is-on">全部</span><span class="sh-filter">辣</span><span class="sh-filter">素食</span><span class="sh-filter">特价</span>' +
    '<span class="sh-seg">' + PICS.map(function (x) { return '<span class="' + (S.pic === x[0] ? 'is-on' : '') + '" data-k="pic" data-v="' + x[0] + '">' + x[1] + '</span>'; }).join('') + '</span>' +
    (S.pic === 'img' ? '<span class="sh-seg">' + CARDS.map(function (x) { return '<span class="' + (S.card === x[0] ? 'is-on' : '') + '" data-k="card" data-v="' + x[0] + '">' + x[1] + '</span>'; }).join('') + '</span>' : '') + '</div>',
    '<div class="dn-grid">' + cards + '</div>', '', S.pic === 'img' ? 'ui-pic' + (S.card === 'h' ? ' ui-h' : '') : '');
  return nav + main + dock();
}
/* 批量模式的底部操作：没选时整排不可用；退菜要授权，所以走授权弹窗 */
/* 快捷操作：结单区头下一排 5 个，设置里可换 */
var QUICK = { member: ['会员码', 'scan-line', 'sheet:member'], hold: ['挂单', 'pause', 'ov:hold'], split: ['拆单', 'split', 'batch:split'], share: ['拼桌', 'users', 'ov:share'], reprice: ['调价', 'tag', 'ov:reprice'],
  note: ['备注', 'notebook-pen', 'ov:note'], move: ['换桌', 'arrow-right-left', 'ov:x3'], comp: ['赠送', 'gift', 'ov:x2'], prebill: ['预结单', 'printer', 'toast:已打印预结单'], pvoucher: ['验券', 'ticket-check', 'ov:pvoucher'], drawer: ['开钱箱', 'archive', 'toast:钱箱已打开'] };
function quickBar() { return '<div class="dn-quick dn-quick--row">' + S.quick.map(function (k) { var q = QUICK[k]; return q ? '<span class="dn-quick-it" data-act="' + q[2] + '" role="button">' + ico(q[1]) + '<span>' + q[0] + '</span></span>' : ''; }).join('') + '</div>'; }
function batchActs() {
  if (S.bmode === 'split') return '<div class="dn-acts">' + btn('取消', 'sh-btn--muted', 'batch:off') + btn('拆到新单' + (S.sel.length ? ' · ' + S.sel.length : ''), S.sel.length ? 'sh-btn--block' : 'sh-btn--off sh-btn--block', S.sel.length ? 'splitsel' : '') + '</div>';
  var n = S.sel.length, off = n ? '' : ' sh-btn--off';
  return '<div class="dn-acts">' + btn('催菜' + (n ? ' · ' + n : ''), (n ? 'sh-btn--soft' : 'sh-btn--off') + ' sh-btn--block', n ? 'rushsel' : '') + btn('转菜', 'sh-btn--muted' + off, n ? 'ov:x3' : '') + btn('退菜', (n ? 'sh-btn--danger' : 'sh-btn--off'), n ? 'ov:x2' : '') + '</div>';
}
function dock() {
  var t = totals(), nc = newCount(), news = [], sent = [];
  LINES.forEach(function (l, i) { (l.st === 'new' ? news : sent).push([l, i]); });
  var body = '';
  if (news.length) body += '<div class="dn-block"><div class="dn-sec"><span>待下单 · ' + nc + ' 份</span><a class="sh-link sh-hit" data-act="clear">清空</a></div><div class="dn-group">' +
    news.map(function (x) { return oline(x[0], x[1], true); }).join('') + '</div></div>';
  var pickable = sent.filter(function (x) { return x[0].st !== 'void'; }).map(function (x) { return x[1]; });
  var secHead = S.batch ? '<span>' + (S.bmode === 'split' ? '选要拆出的菜 · ' : '') + '已选 ' + S.sel.length + ' / ' + pickable.length + ' 道</span><span class="acts"><a class="sh-link sh-hit" data-act="batch:' + (S.sel.length === pickable.length ? 'none' : 'all') + '">' + (S.sel.length === pickable.length ? '全不选' : '全选') + '</a><a class="sh-link sh-hit" data-act="batch:off">完成</a></span>'
    : '<span>已下单 · ' + sent.length + ' 道 · 上菜 ' + sent.filter(function (x) { return x[0].st === 'done'; }).length + '/' + sent.length + '</span><a class="sh-link sh-hit" data-act="batch:on">批量</a>';
  if (sent.length) body += '<div class="dn-block"><div class="dn-sec">' + secHead + '</div><div class="dn-olist">' +
    sent.map(function (x) { return oline(x[0], x[1], false); }).join('') + '</div></div>';
  if (LINES.length) body += adjRows(t);
  else body = '<div class="sh-empty sh-empty--fill"><i></i><b>还没有点菜</b><span>从菜单里点一道菜</span></div>';
  var foot = LINES.length ? dueFoot('应收', money(t.due), dishCount() + ' 道 · 已优惠 ' + money(t.mem + t.round),
    S.batch ? batchActs() : '<div class="dn-acts">' + (nc ? btn('送厨 · ' + nc, 'sh-btn--soft', 'send') : '') + btn('结账', 'sh-btn--block', 'gopay') + '</div>') : '';
  return ledger('<div class="dn-dock-hd"><div class="t"><b>' + S.table + '</b>' + stx('order', 'open') +
    (S.member ? '<span class="sh-chip sh-hit" data-act="sheet:member">' + MEMBER.layer + ' · ' + MEMBER.name + '</span>' : '') + btn(ico('ellipsis'), 'sh-btn--muted sh-btn--icon sh-btn--sm', 'ov:menu', '更多') + '</div>' +
    '<div class="meta"' + (S.note.length ? ' data-act="ov:note"' : '') + '>4 人 · 48 分钟 · 堂食 · #1024' + (S.note.length ? ' · 备注：<span data-notr="1">' + S.note.join('、') + '</span>' : '') + '</div></div>' + (S.batch ? '' : quickBar()), body, foot);
}

OVL['x1'] = function (t) {
    var d = dish(S.spec || '宫保鸡丁');
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog" data-stop="1"><div class="row row--between"><span class="h">' + d.n + '</span><b class="num">¥' + d.p + '</b></div>' +
      '<span class="sh-gt"><span>规格 · 必选</span></span><div class="row"><span class="sh-option is-on">大份<small>¥' + d.p + '</small></span><span class="sh-option">小份<small>¥' + Math.round(d.p * 0.7) + '</small></span></div>' +
      '<span class="sh-gt"><span>辣度</span></span><div class="row"><span class="sh-option">不辣</span><span class="sh-option is-on">微辣</span><span class="sh-option">中辣</span><span class="sh-option">特辣</span></div>' +
      '<span class="sh-gt"><span>加料 · 多选</span></span><div class="row"><span class="sh-option">加花生<small>+¥3</small></span><span class="sh-option">加鸡肉<small>+¥12</small></span></div>' +
      '<span class="sh-gt"><span>备注</span></span><div class="row"><span class="sh-filter is-on">少油</span><span class="sh-filter">少盐</span><span class="sh-filter">不要葱</span></div>' +
      '<div class="acts">' + stepper(1, '', '', 'sh-stepper--lg') + btn('取消', 'sh-btn--muted', 'ov:none') + btn('加入 ¥' + d.p, '', 'addspec') + '</div></div></div>';
};
OVL['hold'] = function (t) {
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog" data-stop="1"><span class="h">挂单 · ' + S.table + ' #1024</span><span class="d">挂起后结单区清空，可以先接下一单；在「订单 › 挂起」或结单区「取单」取回。</span>' +
    '<span class="sh-gt"><span>原因（可选）</span></span><div class="row">' + ['等人到齐', '先上菜后加点', '顾客暂时离开', '待确认菜品'].map(function (x) { return '<span class="sh-filter' + (S.holdWhy === x ? ' is-on' : '') + '" data-act="holdwhy:' + x + '">' + x + '</span>'; }).join('') + '</div>' +
    '<div class="acts">' + btn('取消', 'sh-btn--muted', 'ov:none') + btn('挂起并清空', '', 'holdok') + '</div></div></div>';
};
OVL['share'] = function (t) {
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog" data-stop="1"><span class="h">拼桌 · ' + S.table + '</span><span class="d">同一张桌坐两组客人：分开点菜、分开结账。桌台卡显示两组，结单区可在两组之间切换。</span>' +
    '<div class="sh-cells"><div class="sh-cell"><span class="k">现有一组</span><span class="v">' + S.table + '-1 · 4 人</span></div><div class="sh-cell"><span class="k">新一组人数</span>' + stepper(S.shareN, 'sn:-', 'sn:+') + '</div><div class="sh-cell"><span class="k">新组单号</span><span class="v">' + S.table + '-2 · #1034</span></div></div>' +
    '<div class="acts">' + btn('取消', 'sh-btn--muted', 'ov:none') + btn('确认拼桌', '', 'shareok') + '</div></div></div>';
};
OVL['reprice'] = function (t) {
    var rl = LINES[S.rpLine] || LINES[0], np = rpPrice(rl), need = rpNeedsAuth(rl);
    var vals = { price: [rl.p - 2, rl.p - 5, rl.p - 10].filter(function (v) { return v > 0; }), disc: [9.5, 9, 8.5, 8, 7], off: [2, 5, 10, 20, 30] }[S.rpMode];
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog wide" data-stop="1"><span class="h">调价</span>' +
      '<span class="sh-gt"><span>选择菜品</span></span><div class="row">' + LINES.map(function (l, i) { return l.st === 'void' ? '' : '<span class="sh-option' + (S.rpLine === i ? ' is-on' : '') + '" data-act="rpl:' + i + '">' + l.n + '<small>' + money(l.p) + ' × ' + l.q + '</small></span>'; }).join('') + '</div>' +
      '<div class="row row--between"><span class="sh-seg">' + [['price', '改单价'], ['disc', '打折'], ['off', '减免']].map(function (m) { return '<span class="' + (S.rpMode === m[0] ? 'is-on' : '') + '" data-act="rpm:' + m[0] + '">' + m[1] + '</span>'; }).join('') + '</span>' +
      '<span class="sh-help">' + money(rl.p * rl.q) + ' → <b class="num">' + money(np * rl.q) + '</b></span></div>' +
      '<div class="row">' + vals.map(function (v) { return '<span class="sh-filter' + (S.rpVal === v ? ' is-on' : '') + '" data-act="rpv:' + v + '">' + (S.rpMode === 'disc' ? v + ' 折' : S.rpMode === 'off' ? '减 ¥' + v : '¥' + v) + '</span>'; }).join('') + '</div>' +
      '<span class="sh-gt"><span>原因 · 必选</span></span><div class="row">' + ['服务补偿', '出品问题', '获准议价', '尾数减免', '其他'].map(function (x) { return '<span class="sh-filter' + (S.rpWhy === x ? ' is-on' : '') + '" data-act="rpw:' + x + '">' + x + '</span>'; }).join('') + '</div>' +
      (need ? '<div class="sh-note sh-note--warn">低于 8 折或单行减免超过 ¥20，需要主管授权。</div>' : '') +
      '<div class="acts">' + btn('取消', 'sh-btn--muted', 'ov:none') + (S.rpWhy ? btn(need ? '申请授权' : '确认调价', '', need ? 'ov:x2' : 'rpok') : btn('先选原因', 'sh-btn--off')) + '</div></div></div>';
};
OVL['note'] = function (t) {
    var d0 = S.noteDraft || S.note;
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog" data-stop="1"><span class="h">整单备注</span><span class="sh-gt"><span>常用</span></span><div class="row">' +
      ['少辣', '不要香菜', '赶时间', '分开上', '过敏：花生'].map(function (x) { return '<span class="sh-filter' + (d0.indexOf(x) > -1 ? ' is-on' : '') + '" data-act="ntag:' + x + '" role="checkbox" aria-checked="' + (d0.indexOf(x) > -1) + '">' + x + '</span>'; }).join('') + '</div>' +
      '<span class="sh-gt"><span>其他要求</span></span><div class="sh-input sh-input--area ph-">选填，例如「先上凉菜」</div><span class="sh-help">备注会打在厨房单和结账单上</span>' +
      '<div class="acts">' + btn('取消', 'sh-btn--muted', 'ov:none') + btn('保存', '', 'notesave') + '</div></div></div>';
};
OVL['menu'] = function (t) {
    return '<div class="sh-mask sh-mask--clear" data-act="ov:none"></div><div class="ui-pop ' + (S.detail === 'right' ? 'at-r' : S.nav === 'left' ? 'at-l nav-l' : 'at-l') + '"><div class="sh-menu">' +
    '<span data-act="toast:改人数">改人数</span><span data-act="ov:note">整单备注</span><span data-act="ov:x3">换桌 / 并台</span><hr><span data-act="toast:已打印预结单">打印预结单</span><span data-act="batch:split">拆单</span><span data-act="ov:x3">转菜</span><hr><span data-act="ov:x2">赠送<small>需授权</small></span><span class="is-danger" data-act="ov:x2">退菜<small>需授权</small></span><span class="is-off">清台<small>有未结账菜品</small></span></div></div>';
};
ACT['cat'] = function (v, k) {
    S.cat = v; return;
};
ACT['add'] = function (v, k) {
    var d = dish(v);
    if (left(d) === 0) { toast(d.n + '已沽清'); return; }
    if (d.timed) { toast(d.n + ' ' + d.timed + '供应'); return; }
    if (d.spec) { S.spec = d.n; S.ov = 'x1'; return; }
    var hit = LINES.filter(function (l) { return l.n === d.n && l.st === 'new' && !l.sub; })[0];
    if (hit) hit.q++; else LINES.push({ n: d.n, q: 1, p: d.p, st: 'new' });
    return;
};
ACT['addspec'] = function (v, k) {
    var s = dish(S.spec); LINES.push({ n: s.n, q: 1, p: s.p, st: 'new', sub: '大份 · 微辣 · 少油' }); S.ov = 'none'; return;
};
ACT['inc'] = function (v, k) {
    LINES[+v].q++; return;
};
ACT['dec'] = function (v, k) {
    var l = LINES[+v]; if (--l.q <= 0) LINES.splice(+v, 1); return;
};
ACT['clear'] = function (v, k) {
    LINES = LINES.filter(function (l) { return l.st !== 'new'; }); toast('已清空待下单'); return;
};
ACT['send'] = function (v, k) {
    var n = newCount(); LINES.forEach(function (l) { if (l.st === 'new') l.st = 'cook'; }); toast('已送厨 ' + n + ' 道菜'); return;
};
ACT['hold'] = function (v, k) {
    toast('已挂单 #1024'); return;
};
ACT['lact'] = function (v, k) {
    S.lact = S.lact === +v ? -1 : +v; return;
};
ACT['rpline'] = function (v, k) {
    S.rpLine = +v; S.rpWhy = ''; S.ov = 'reprice'; S.lact = -1; return;
};
ACT['rpl'] = function (v, k) {
    S.rpLine = +v; return;
};
ACT['rpm'] = function (v, k) {
    S.rpMode = v; S.rpVal = { price: LINES[S.rpLine].p - 2, disc: 9, off: 5 }[v]; return;
};
ACT['rpv'] = function (v, k) {
    S.rpVal = +v; return;
};
ACT['rpw'] = function (v, k) {
    S.rpWhy = v; return;
};
ACT['rpok'] = function (v, k) {
    var rl = LINES[S.rpLine]; rl.p = Math.round(rpPrice(rl) * 100) / 100; rl.rp = true; S.ov = 'none'; toast('已调价：' + rl.n + ' ' + money(rl.p) + ' / 份（' + S.rpWhy + '）'); S.rpWhy = ''; return;
};
ACT['holdwhy'] = function (v, k) {
    S.holdWhy = S.holdWhy === v ? '' : v; return;
};
ACT['holdok'] = function (v, k) {
    ORDERS.unshift(['#1034', S.table, '堂食', 4, LINES.length, '12:31', totals().due, 'order', 'held', '']); LINES = []; S.ov = 'none'; S.batch = false; toast('已挂单 #1034，可在「订单 › 挂起」取回'); return;
};
ACT['sn'] = function (v, k) {
    S.shareN = Math.max(1, S.shareN + (v === '+' ? 1 : -1)); return;
};
ACT['shareok'] = function (v, k) {
    var ts = TABLES.filter(function (x) { return x.id === S.table; })[0]; if (ts) ts.st = 'shared'; S.ov = 'none'; toast(S.table + ' 已拼桌，新开 ' + S.table + '-2（' + S.shareN + ' 人）'); return;
};
ACT['splitsel'] = function (v, k) {
    var n0 = S.sel.length; LINES = LINES.filter(function (l, j) { return S.sel.indexOf(j) < 0; }); S.batch = false; S.bmode = ''; S.sel = []; toast('已拆出新单 #1035（' + n0 + ' 道），可单独结账'); return;
};
ACT['batch'] = function (v, k) {
    var all = LINES.map(function (l, j) { return l.st !== 'new' && l.st !== 'void' ? j : -1; }).filter(function (j) { return j > -1; });
    if (v === 'on' || v === 'split') { S.batch = true; S.sel = []; S.bmode = v === 'split' ? 'split' : ''; S.lact = -1; } else if (v === 'off') { S.batch = false; S.sel = []; S.bmode = ''; } else S.sel = v === 'all' ? all : [];
    return;
};
ACT['pick'] = function (v, k) {
    var p = S.sel.indexOf(+v); if (p > -1) S.sel.splice(p, 1); else S.sel.push(+v); return;
};
ACT['rushsel'] = function (v, k) {
    var nr = 0; S.sel.forEach(function (j) { if (LINES[j].st === 'cook') { LINES[j].st = 'rush'; nr++; } }); S.batch = false; S.sel = []; toast(nr ? '已催菜 ' + nr + ' 道，已通知厨房' : '选中的菜不在制作中，无需催菜'); return;
};
ACT['ntag'] = function (v, k) {
    var dr = (S.noteDraft || S.note).slice(), q = dr.indexOf(v); if (q > -1) dr.splice(q, 1); else dr.push(v); S.noteDraft = dr; return;
};
ACT['notesave'] = function (v, k) {
    if (S.noteDraft) S.note = S.noteDraft; S.noteDraft = null; S.ov = 'none'; toast('已保存整单备注'); return;
};
ACT['void'] = function (v, k) {
    var g = LINES.filter(function (l) { return l.st === 'gift' || l.st === 'done'; })[0]; if (g) g.st = 'void'; S.ov = 'none'; toast('已退菜，已通知厨房'); return;
};
MODULES.order.render = orderWB;
MODULES.order.sections = ['菜单', '结单区'];
