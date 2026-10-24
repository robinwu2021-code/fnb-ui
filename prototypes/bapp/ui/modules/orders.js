/* 模块 · 订单：时段导航、状态 / 渠道筛选、列表 / 卡片、订单详情。 */
/* 每单：[单号, 桌号或平台单号, 渠道, 人数, 件数, 下单时间, 金额, 实体, 状态, 上菜进度] */
var ORDERS = [['#1024', 'A03', '堂食', 4, 6, '11:42', 268, 'order', 'open', '3/6'], ['#1025', 'A05', '堂食', 2, 3, '11:58', 96, 'order', 'due', '3/3'],
  ['#1026', '—', '外带', 1, 2, '12:03', 42, 'order', 'held', ''], ['#1027', '美团 #88', '外卖', 0, 4, '12:10', 118, 'delivery', 'accepted', ''],
  ['#1028', 'V02', '堂食', 10, 12, '12:12', 886, 'order', 'open', '5/12'], ['#1029', 'A09', '堂食', 3, 5, '12:20', 172, 'order', 'partial', '5/5'],
  ['#1030', '饿了么 #31', '外卖', 0, 2, '12:21', 42, 'delivery', 'new', ''], ['#1031', '美团 #91', '外卖', 0, 3, '12:05', 76, 'delivery', 'timeout', ''],
  ['#1032', '美团 #79', '外卖', 0, 5, '11:30', 134, 'delivery', 'delivering', ''], ['#1020', 'A01', '堂食', 4, 7, '11:02', 312, 'order', 'paid', '7/7'],
  ['#1021', 'A04', '堂食', 2, 4, '11:10', 136, 'order', 'paid', '4/4'], ['#1019', 'A07', '堂食', 2, 4, '10:48', 158, 'order', 'reopened', '4/4'],
  ['#1018', '—', '外带', 1, 2, '10:40', 38, 'order', 'cancelled', ''], ['#1033', '饿了么 #36', '外卖', 0, 2, '12:24', 56, 'delivery', 'pickup', ''],
  ['#1017', '美团 #70', '外卖', 0, 3, '10:20', 88, 'delivery', 'delivered', ''], ['#1016', '饿了么 #22', '外卖', 0, 1, '10:12', 28, 'delivery', 'refunding', '']];
/* 平台券核销记录（按单）：[券, 张数, 核销时间, 距今小时, 操作人, 状态 redeemed / undone]。当前收银员 = 李敏。
 * 撤销规则：核销后 24 小时内可撤——本人直接撤（写原因）；别人核销的需主管授权；超过 24 小时平台不再受理，只能在平台商家后台走退款。 */
var REDEEMED = { '#1020': [['mt100', 2, '11:46', 0.8, '李敏', 'redeemed']], '#1021': [['dy116', 1, '11:12', 1.3, '王芳', 'redeemed']], '#1019': [['mt100', 1, '10-22 10:31', 26, '李敏', 'redeemed']] };
var ME = '李敏';
function pvUndoWhy(r) { return r[5] === 'undone' ? 'done' : r[3] >= 24 ? 'late' : r[4] !== ME ? 'auth' : ''; }
function pvRecords(cur) {
  var rs = REDEEMED[cur[0]], en = S.lang === 'en';
  if (!rs) return '<div class="sh-note">' + (cur[8] === 'paid' ? '已付清 · 微信 ' + money(cur[6]) : '还没有付款。') + '</div>';
  var pvSum = 0, undone = 0;
  return rs.map(function (r, i) {
    var v = pvOf(r[0]), why = pvUndoWhy(r), amt = v.face * r[1]; if (r[5] !== 'undone') pvSum += amt; else undone += amt;
    var meta = '<small data-notr="1">' + (en ? 'Redeemed ' + r[2] + ' · ' + r[4] + ' · ×' + r[1] + ' · −' + money(amt) : '核销 ' + r[2] + ' · ' + r[4] + ' · ×' + r[1] + ' · −' + money(amt)) + '</small>';
    var tail = why === 'done' ? stx('coupon', 'unused', en ? 'Undone · returned' : '已撤销 · 券已退回', 'dot')
      : why === 'late' ? '<span class="sh-help">' + (en ? 'Over 24 h · refund in the platform backend' : '超过 24 小时 · 请到平台商家后台退款') + '</span>'
      : '<a class="sh-link sh-hit" data-act="pvundo:' + cur[0] + '|' + i + '">' + (why === 'auth' ? '撤销核销 · 需授权' : '撤销核销') + '</a>';
    return '<div class="dn-coupon' + (why === 'done' ? ' is-off' : '') + '"><span class="v">¥' + v.face + '</span><span class="tx" data-notr="1"><b>' + platMark(v.plat) + pvName(v) + '</b>' + meta + '</span></div><div class="row row--between">' + stx('coupon', why === 'done' ? 'unused' : 'used', why === 'done' ? (en ? 'Returned' : '已退回') : (en ? 'Redeemed' : '已核销'), 'dot') + tail + '</div>';
  }).join('') + (cur[6] - pvSum - undone > 0 ? '<div class="sh-note">其余 · 微信 ' + money(cur[6] - pvSum - undone) + '</div>' : '') + (undone ? '<div class="sh-note sh-note--warn">待补收 ' + money(undone) + '</div>' : '');
}
var OST = { order: ['open', 'due', 'partial', 'held', 'paid', 'reopened', 'cancelled'], delivery: ['new', 'accepted', 'pickup', 'delivering', 'delivered', 'rejected', 'refunding', 'timeout'] };
var ALERT = { due: 1, partial: 1, new: 1, timeout: 1 };   /* 要人处理的状态：计数用危险色，卡片加状态色竖条 */
function orderList() {
  var ent = S.och === '外卖' ? 'delivery' : 'order';
  var base = ORDERS.filter(function (o) { return S.och === '全部' ? true : o[2] === S.och; });
  var shown = base.filter(function (o) { return S.ost === 'all' || (o[7] === ent && o[8] === S.ost); });
  return { ent: ent, base: base, shown: shown };
}
function ordersWB() {
  var nav = rail([{ cap: '时段' }, { v: '今天', em: 46 }, { v: '本班', em: 16 }, { v: '昨天', em: 52 }, { v: '近 7 天', em: 341 }], S.oper, 'oper');
  var L0 = orderList(), ent = L0.ent;
  var chips = '<div class="row"><span class="sh-filter' + (S.ost === 'all' ? ' is-on' : '') + '" data-act="ost:all">全部<em>' + L0.base.length + '</em></span>' +
    OST[ent].map(function (k) {
      var n = L0.base.filter(function (o) { return o[7] === ent && o[8] === k; }).length;
      return '<span class="sh-filter' + (S.ost === k ? ' is-on' : '') + '" data-act="ost:' + k + '">' + stIcon(ent, k) + '<span data-notr="1">' + stName(ent, k) + '</span><em' + (ALERT[k] && n ? ' class="is-alert"' : '') + '>' + n + '</em></span>';
    }).join('') + '</div>';
  var chans = '<span class="sh-seg">' + ['全部', '堂食', '外带', '外卖'].map(function (c) { return '<span class="' + (S.och === c ? 'is-on' : '') + '" data-act="och:' + c + '">' + c + '</span>'; }).join('') + '</span>';
  var views = '<span class="sh-seg">' + [['list', '列表'], ['card', '卡片']].map(function (v) { return '<span class="' + (S.oview === v[0] ? 'is-on' : '') + '" data-k="oview" data-v="' + v[0] + '">' + v[1] + '</span>'; }).join('') + '</span>';
  var sum = L0.shown.reduce(function (a, o) { return a + o[6]; }, 0);
  var head = '<div class="sh-toolbar">' + search('搜单号 / 桌号 / 手机尾号') + chans + views + '</div>' + chips;
  var body;
  if (S.load === 'on') body = '<div class="dn-orows">' + [0, 1, 2, 3, 4, 5].map(function () { return '<div class="dn-orow" aria-hidden="true"><span class="sh-skel"></span><span class="sh-skel"></span><span class="sh-skel"></span><span class="sh-skel sh-skel--m"></span><span class="sh-skel"></span></div>'; }).join('') + '</div><div class="sh-empty"><i class="sh-spin"></i><span>正在加载订单…</span></div>';
  else if (!L0.shown.length) body = '<div class="sh-empty sh-empty--fill"><i></i><b>没有符合条件的订单</b><span>换一个状态或渠道试试</span></div>';
  else {
    var where = function (o) { return o[2] === '外卖' ? o[1] : o[1] === '—' ? o[2] : o[1] + ' · ' + o[2]; };
    var tag = function (o) { return stx(o[7], o[8], null, S.oview === 'list' ? 'dot' : null); };
    body = '<div class="dn-sec"><span>共 ' + L0.shown.length + ' 单 · ' + money(sum) + '</span><span>按下单时间</span></div>' + (S.oview === 'card'
      ? '<div class="dn-ocards">' + L0.shown.map(function (o) {
          var alert = ALERT[o[8]] ? ' is-alert' : '';
          return '<div class="dn-ocard' + (S.osel === o[0] ? ' is-on' : '') + alert + (stGroup(o[7], o[8]) === 'closed' ? ' is-quiet' : '') + '" style="' + stv(stGroup(o[7], o[8])) + '" data-act="osel:' + o[0] + '"><div class="t"><b>' + o[0] + '</b>' + tag(o) + '</div>' +
            '<span class="sh-help">' + where(o) + (o[3] ? ' · ' + o[3] + ' 人' : '') + '</span><span class="sh-help">' + o[4] + ' 件 · ' + o[5] + (o[9] ? ' · 上菜 ' + o[9] : '') + '</span><span class="a">' + money(o[6]) + '</span></div>';
        }).join('') + '</div>'
      : '<div class="dn-orows">' + L0.shown.map(function (o) {
          return '<div class="dn-orow' + (S.osel === o[0] ? ' is-on' : '') + '" data-act="osel:' + o[0] + '">' + tag(o) + '<b>' + o[0] + '</b><span>' + where(o) + '</span><span class="sh-help">' + o[4] + ' 件 · ' + o[5] + '</span><span class="a">' + money(o[6]) + '</span></div>';
        }).join('') + '</div>');
  }
  var main = panel(head, body);
  var cur = ORDERS.filter(function (o) { return o[0] === S.osel; })[0] || ORDERS[0];
  var det = ledger('<div class="row row--between"><b class="h-title">' + cur[0] + '</b>' + stx(cur[7], cur[8]) + '</div><span class="sh-help">' + (cur[2] === '外卖' ? cur[1] : cur[1] === '—' ? cur[2] : cur[1] + ' · ' + cur[2]) + ' · ' + cur[5] + '</span>', '<div class="dn-block"><div class="dn-sec"><span>菜品 · ' + LINES.length + ' 道</span></div><div class="dn-olist">' +
    LINES.map(function (l, i) { return oline(l, i, false); }).join('') +
    '</div></div><div class="dn-block"><div class="dn-sec"><span>支付记录</span>' + (REDEEMED[cur[0]] ? '<span>平台券撤销：24 小时内</span>' : '') + '</div>' + pvRecords(cur) + '</div>',
    dueFoot('订单金额', money(cur[6]), cur[4] + ' 件 · ' + cur[5] + ' 下单', '<div class="row">' + btn('合单', 'sh-btn--muted sh-btn--sm', 'ov:x3') + btn('补打', 'sh-btn--muted sh-btn--sm', 'toast:已补打 ' + cur[0] + ' 结账单') + btn('反结账', 'sh-btn--danger sh-btn--sm', 'ov:x2') + '</div>' + btn('取单', 'sh-btn--block', 'goorder')));
  return nav + main + det;
}

/* 撤销核销：选原因（必填）→ 别人核销的要主管授权 → 平台撤销成功后券退回顾客，本单变「部分支付」待补收 */
OVL['pvundo'] = function (t) {
    var p = (S.pvu || '').split('|'), r = (REDEEMED[p[0]] || [])[+p[1]]; if (!r) return '';
    var v = pvOf(r[0]), amt = v.face * r[1], auth = pvUndoWhy(r) === 'auth', why = S.pvuWhy || '';
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog" data-stop="1"><span class="h">撤销核销' + (auth ? ' · 主管授权' : '') + '</span>' +
      '<span class="d" data-notr="1">' + platMark(v.plat) + pvName(v) + ' ×' + r[1] + ' · ' + money(amt) + ' · ' + (S.lang === 'en' ? 'redeemed ' + r[2] + ' by ' + r[4] : r[2] + ' ' + r[4] + ' 核销') + '</span>' +
      '<span class="sh-gt"><span>原因 · 必选</span></span><div class="row">' + ['验错券', '顾客改用其他方式付款', '重复核销', '其他'].map(function (x) { return '<span class="sh-option' + (why === x ? ' is-on' : '') + '" data-act="pvuwhy:' + x + '">' + x + '</span>'; }).join('') + '</div>' +
      (auth ? '<span class="sh-help">这张券由 ' + r[4] + ' 核销，撤销需要主管输入授权码</span><div class="dn-pins"><i class="on"></i><i class="on"></i><i></i><i></i></div><div class="dn-keys">' + ['1', '2', '3', '4', '5', '6', '7', '8', '9', '取消', '0', '⌫'].map(key).join('') + '</div>' : '') +
      '<div class="sh-note sh-note--warn" data-notr="1">' + (S.lang === 'en' ? 'The voucher returns to the customer\'s platform account; this order will have ' + money(amt) + ' to collect.' : '撤销后券退回顾客的平台账户；本单变为部分支付，待补收 ' + money(amt) + '。') + '</div>' +
      '<div class="acts">' + btn('取消', 'sh-btn--muted', 'ov:none') + (why ? btn('确认撤销', 'sh-btn--danger', 'pvundook') : '<span class="sh-btn sh-btn--danger is-off" aria-disabled="true">确认撤销</span>') + '</div><span class="sh-help">记录原因、操作人' + (auth ? '、授权人' : '') + '；撤销结果以平台返回为准</span></div></div>';
};
ACT['pvundo'] = function (v, k) {
    S.pvu = v; S.pvuWhy = ''; S.ov = 'pvundo'; return;
};
ACT['pvuwhy'] = function (v, k) {
    S.pvuWhy = v; return;
};
ACT['pvundook'] = function (v, k) {
    var p = S.pvu.split('|'), r = REDEEMED[p[0]][+p[1]], o = ORDERS.filter(function (x) { return x[0] === p[0]; })[0];
    r[5] = 'undone'; if (o) o[8] = 'partial'; S.ov = 'none'; toast((S.lang === 'en' ? 'Redemption undone · ' : '已撤销核销 · ') + pvName(pvOf(r[0])) + ' · ' + S.pvuWhy); return;
};
ACT['ost'] = function (v, k) {
    S.ost = v; return;
};
ACT['och'] = function (v, k) {
    S.och = v; S.ost = 'all'; return;
};
ACT['oper'] = function (v, k) {
    S.oper = v; return;
};
ACT['osel'] = function (v, k) {
    S.osel = v; return;
};
MODULES.orders.render = ordersWB;
MODULES.orders.sections = ['订单列表', '订单卡片', '订单详情'];
