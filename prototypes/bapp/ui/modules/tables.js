/* 模块 · 桌台：桌台宫格（11 态）、桌台详情、选桌 / 清台提示。 */
/* 桌台卡：白底 · 顶部 3px 状态色条 · 状态图标 + 文字 · 一行信息 · 金额；要处理的状态（待处理、异常两组）铺状态浅底 */
function busyTable(st) { return /^(seated|dining|late|due|partial|shared)$/.test(st); }
function tableTile(t) {
  var g = stGroup('table', t.st), alert = g === 'action' || g === 'failed', quiet = t.st === 'free' || t.st === 'disabled';
  var info = { free: t.seats + ' 座', booked: '19:00 · 张先生 · 6 人', seated: t.ppl + '/' + t.seats + ' 人 · 已开台 6 分', dining: t.ppl + '/' + t.seats + ' 人 · ' + t.min + ' 分', late: t.ppl + '/' + t.seats + ' 人 · 超时 2 道',
    due: t.ppl + '/' + t.seats + ' 人 · ' + t.min + ' 分', partial: '待收 ¥148', to_clear: '已结账 3 分', shared: 'A03-1 · A03-2 两组', disabled: '维修中' }[t.st] || '';
  var amt = /^(dining|late|due|shared)$/.test(t.st) ? '<span class="a">¥' + t.amt + '</span>' : t.st === 'partial' ? '<span class="a">¥' + (t.amt + 100) + '</span>' : '';
  return '<span class="dn-table' + (alert ? ' is-alert' : '') + (quiet ? ' is-quiet' : '') + (t.st === 'disabled' ? ' is-dim' : '') + (t.id === S.table ? ' is-cur' : '') + '" style="' + stv(g) + '" data-act="table:' + t.id + '"><b>' + t.id + '</b>' +
    stx('table', t.st, null, 'dot') + '<small>' + info + '</small>' + amt + '</span>';
}
function tablesWB() {
  var cnt = {}; TABLES.forEach(function (t) { cnt[t.st] = (cnt[t.st] || 0) + 1; });
  var order = ['free', 'booked', 'seated', 'dining', 'late', 'due', 'partial', 'to_clear', 'shared', 'disabled'];
  var nav = rail([{ cap: '桌区' }, { v: '全部', em: TABLES.length }, { v: '大厅', em: 12 }, { v: '包间', em: 6 }, { cap: '状态' }].concat(order.filter(function (k) { return cnt[k]; }).map(function (k) { return { v: k, label: stName('table', k), icon: stIcon('table', k), em: cnt[k] }; })), S.tst || '全部', 'tst');
  var list = TABLES.filter(function (t) { return !S.tst || S.tst === '全部' || S.tst === '大厅' || S.tst === '包间' || t.st === S.tst; });
  var tiles = list.map(tableTile).join('');
  var main = panel('<div class="sh-stat sh-stat--sunk"><div><div class="n">' + (cnt.free || 0) + '</div><div class="l">' + stIcon('table', 'free') + '空闲</div></div><div><div class="n">' + ((cnt.seated || 0) + (cnt.dining || 0) + (cnt.late || 0) + (cnt.shared || 0)) + '</div><div class="l">' + stIcon('table', 'dining') + '用餐中</div></div><div><div class="n">' + ((cnt.due || 0) + (cnt.partial || 0)) + '</div><div class="l">' + stIcon('table', 'due') + '待结账</div></div><div><div class="n">1.6</div><div class="l">今日翻台</div></div></div>',
    '<div class="ui-tables">' + tiles + '</div>');
  var t = TABLES.filter(function (x) { return x.id === S.table; })[0] || TABLES[0], busy = busyTable(t.st);
  var det = ledger('<div class="row row--between"><b class="h-title">' + t.id + '</b>' + stx('table', t.st) + '</div>',
    '<div class="dn-block"><div class="sh-cells">' + (busy ? '<div class="sh-cell"><span class="k">人数</span><span class="v num">' + t.ppl + ' / ' + t.seats + '</span></div><div class="sh-cell"><span class="k">开台</span><span class="v num">11:42 · ' + t.min + ' 分钟</span></div><div class="sh-cell"><span class="k">服务员</span><span class="v go">王芳</span></div>'
      : t.st === 'booked' ? '<div class="sh-cell"><span class="k">预订人</span><span class="v">张先生 138****6621</span></div><div class="sh-cell"><span class="k">人数</span><span class="v num">6</span></div><div class="sh-cell"><span class="k">到店</span><span class="v num">19:00</span></div>'
      : '<div class="sh-cell"><span class="k">座位</span><span class="v num">' + t.seats + '</span></div><div class="sh-cell"><span class="k">区域</span><span class="v">大厅</span></div>') + '</div></div>' +
    (busy ? '<div class="dn-block"><div class="dn-sec"><span>已点 · ' + LINES.length + ' 道</span><span>上菜 1/' + LINES.length + '</span></div><div class="dn-olist">' + LINES.map(function (l, i) { return oline(l.st === 'new' ? { n: l.n, q: l.q, p: l.p, st: 'cook' } : l, i, false); }).join('') + '</div></div>' : ''),
    dueFoot(busy ? '未结金额' : '', '¥' + t.amt + '.00', t.ppl + ' 人 · ' + t.min + ' 分钟',
    (busy ? '<div class="row">' + btn('换桌', 'sh-btn--muted sh-btn--sm', 'ov:x3') + btn('并台', 'sh-btn--muted sh-btn--sm', 'ov:x3') + btn('预结', 'sh-btn--muted sh-btn--sm', 'toast:已打印预结单') + btn('清台', 'sh-btn--danger sh-btn--sm', 'ov:confirm') + '</div>' : '') +
    (t.st === 'due' || t.st === 'partial' ? btn('去结账', 'sh-btn--block', 'gopay') : busy ? btn('加菜', 'sh-btn--block', 'goorder') : t.st === 'to_clear' ? btn('清台', 'sh-btn--block', 'cleartable') : t.st === 'disabled' ? btn('启用', 'sh-btn--block sh-btn--soft', 'toast:已启用') : btn(t.st === 'booked' ? '到店开台' : '开台', 'sh-btn--block', 'seat'))));
  return nav + main + det;
}

OVL['x3'] = function (t) {
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog wide" data-stop="1"><span class="h">选择目标桌</span><span class="sh-seg"><span class="is-on">换桌</span><span>并台</span><span>转菜</span><span>合单</span></span>' +
    '<span class="sh-help">换桌只能选空闲桌；并台、转菜选用餐中的桌；合单选进行中的订单。</span><div class="ui-tables">' +
    TABLES.filter(function (x) { return x.st === 'free'; }).map(function (x, i) { return '<span class="dn-table is-quiet' + (i === 1 ? ' is-cur' : '') + '"><b>' + x.id + '</b><small>空闲 · ' + x.seats + ' 座</small></span>'; }).join('') + '</div>' +
    '<div class="acts">' + btn('取消', 'sh-btn--muted', 'ov:none') + btn('换到 A07', '', 'toast:已换到 A07') + '</div></div></div>';
};
OVL['confirm'] = function (t) {
    return '<div class="sh-mask"><div class="sh-dialog"><span class="h">还不能清台</span><span class="d">这桌还有未结账的菜品。先结账或退菜，再清台。</span><div class="acts">' + btn('知道了', 'sh-btn--muted', 'ov:none') + btn('去结账', '', 'gopay') + '</div></div></div>';
};
ACT['table'] = function (v, k) {
    S.table = v; return;
};
ACT['tst'] = function (v, k) {
    S.tst = v; return;
};
ACT['cleartable'] = function (v, k) {
    var tc = TABLES.filter(function (x) { return x.id === S.table; })[0]; if (tc) tc.st = 'free'; toast(S.table + ' 已清台'); return;
};
ACT['seat'] = function (v, k) {
    var t = TABLES.filter(function (x) { return x.id === S.table; })[0]; if (t) t.st = 'seated'; S.wb = 'order'; LINES = []; toast(S.table + ' 已开台'); return;
};
MODULES.tables.render = tablesWB;
MODULES.tables.sections = ['全部桌台', '按状态筛选'];
