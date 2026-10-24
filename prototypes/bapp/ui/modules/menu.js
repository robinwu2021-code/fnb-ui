/* 模块 · 菜品管理：菜品、分类、规格与做法、套餐、渠道同步。 */
var CHANNELS = [['dine', '堂食', 'utensils'], ['mini', '扫码点餐', 'qr-code'], ['mt', '美团外卖', 'bike'], ['elm', '饿了么', 'bike']];
/* 每道菜在各渠道的同步状态（业务状态 sync 实体）：已同步 · 待同步 · 同步中 · 同步失败 · 未上架 */
var SYNC = {};
DISHES.forEach(function (d, i) { SYNC[d.n] = { dine: 'synced', mini: 'synced', mt: i % 5 === 3 ? 'failed' : i % 4 === 1 ? 'pending' : d.c === '套餐' ? 'unlisted' : 'synced', elm: i % 6 === 2 ? 'pending' : i === 9 ? 'failed' : 'synced' }; });
var SYNCWHY = { 拍黄瓜: '图片尺寸小于 600×450', 米饭: '分类「主食」未映射到平台类目', 担担面: '平台最低价 ¥25，当前 ¥22' };
var MODS = [['份量', '单选 · 必选', ['大份 +¥0', '小份 −¥14'], 6], ['辣度', '单选 · 必选', ['不辣', '微辣', '中辣', '特辣'], 9], ['加料', '多选 · 最多 3 项', ['加花生 +¥3', '加鸡肉 +¥12', '加蛋 +¥2'], 4], ['做法', '多选', ['少油', '少盐', '不要葱', '不要香菜'], 15], ['温度', '单选', ['常温', '加冰', '热饮'], 3]];
var SETS = [{ n: '双人套餐', en: 'Set for Two', p: 168, groups: [['凉菜 · 任选 1', ['拍黄瓜', '夫妻肺片', '蒜蓉西兰花']], ['热菜 · 任选 2', ['宫保鸡丁', '麻婆豆腐', '小炒黄牛肉', '酸菜鱼 +¥10']], ['主食 · 2 份', ['米饭']], ['饮品 · 2 杯', ['酸梅汤', '鲜榨橙汁 +¥6']]], swap: true },
  { n: '工作日午市单人餐', en: 'Weekday Lunch', p: 38, groups: [['热菜 · 任选 1', ['宫保鸡丁', '麻婆豆腐']], ['主食 · 1 份', ['米饭']], ['汤 · 1 份', ['番茄蛋汤']]], swap: false }];
function markPending(d) { ['mt', 'elm'].forEach(function (ch) { if (SYNC[d.n][ch] === 'synced') SYNC[d.n][ch] = 'pending'; }); }
function chIcons(d) { return CHANNELS.map(function (c) { var st = SYNC[d.n] ? SYNC[d.n][c[0]] : 'synced'; return '<span class="dn-ch" title="' + c[1] + '">' + stIcon('sync', st) + '</span>'; }).join(''); }
function dishStock(d) { var rest = left(d); return d.timed ? stx('stock', 'timed', d.timed, 'dot') : rest === 0 ? stx('stock', 'sold_out', null, 'dot') : rest != null && rest <= 5 ? stx('stock', 'low', '仅剩 ' + rest, 'dot') : stx('stock', 'in_stock', rest == null ? '不限' : '库存 ' + rest, 'dot'); }
function menuWB() {
  var nav = rail([{ cap: '菜品' }, { v: '菜品', icon: ico('soup'), label: '菜品', em: DISHES.length }, { v: '分类', icon: ico('list-tree'), label: '分类', em: CATS.length }, { v: '规格', icon: ico('sliders-horizontal'), label: '规格', em: MODS.length }, { v: '套餐', icon: ico('layers'), label: '套餐', em: SETS.length },
    { cap: '渠道' }, { v: '同步', icon: ico('refresh-cw'), label: '渠道同步', em: Object.keys(SYNC).filter(function (k) { return SYNC[k].mt === 'failed' || SYNC[k].elm === 'failed'; }).length || null }], S.msub, 'msub');
  return nav + ({ 菜品: mdDishes, 分类: mdCats, 规格: mdMods, 套餐: mdSets, 同步: mdSync }[S.msub] || mdDishes)();
}
function mdDishes() {
  var f = S.mdf, cat = S.mdc;
  var list = DISHES.filter(function (d) { var r = left(d); return (cat === '全部' || d.c === cat) && (f === 'all' || (f === 'sold_out' ? r === 0 : f === 'timed' ? d.timed : f === 'low' ? r != null && r > 0 && r <= 5 : f === 'sync' ? SYNC[d.n].mt === 'failed' || SYNC[d.n].elm === 'failed' || SYNC[d.n].mt === 'pending' || SYNC[d.n].elm === 'pending' : true)); });
  var head = '<div class="sh-toolbar">' + search('搜索菜名 / 编号') + btn(ico('plus') + '新建菜品', 'sh-btn--soft sh-btn--sm', 'mdnew') + '</div>' +
    '<div class="row">' + [['all', '全部', ''], ['low', '低库存', stIcon('stock', 'low')], ['sold_out', '已沽清', stIcon('stock', 'sold_out')], ['timed', '限时供应', stIcon('stock', 'timed')], ['sync', '渠道待处理', stIcon('sync', 'failed')]].map(function (x) { return '<span class="sh-filter' + (f === x[0] ? ' is-on' : '') + '" data-act="mdf:' + x[0] + '">' + x[2] + x[1] + '</span>'; }).join('') +
    '<span class="sh-seg">' + ['全部'].concat(CATS.slice(1).map(function (c) { return c[0]; })).concat(['甜品']).map(function (c) { return '<span class="' + (cat === c ? 'is-on' : '') + '" data-act="mdc:' + c + '">' + c + '</span>'; }).join('') + '</span></div>';
  var body = '<div class="sh-table" style="--cols:44px minmax(0,1.6fr) 72px 72px 112px 104px 52px"><div><span></span><span>菜品</span><span>分类</span><span class="r">价格</span><span>售卖</span><span>渠道</span><span class="r">上架</span></div>' +
    list.map(function (d) { var i = DISHES.indexOf(d);
      return '<div data-act="mdsel:' + i + '" class="' + (S.mdsel === i ? 'is-on' : '') + '"><span class="th k-' + (CK[d.c] || 'gray') + '">' + (d.img ? '<img src="' + d.img + '" alt="" loading="lazy">' : '<span data-notr="1">' + (S.lang === 'en' ? d.en[0] : d.n[0]) + '</span>') + '</span>' +
        '<span class="nm2" data-notr="1"><b>' + (S.lang === 'en' ? d.en : d.n) + ' ' + tagIcons(d) + '</b><small>' + (S.lang === 'en' ? d.n : d.en) + '</small></span><span>' + d.c + '</span><span class="r">¥' + d.p + (d.spec ? ' 起' : '') + '</span><span>' + dishStock(d) + '</span><span class="row">' + chIcons(d) + '</span><span class="r">' + '<span class="sh-switch' + (d.off ? '' : ' is-on') + '"></span></span></div>'; }).join('') + '</div>' +
    '<p class="sh-help">渠道图标依次是 堂食 · 扫码点餐 · 美团外卖 · 饿了么；改了菜品后各渠道变「待同步」，到「渠道同步」一起推送。</p>';
  var d = DISHES[S.mdsel] || DISHES[0];
  var det = side('<div class="row row--between"><b class="h-title">' + d.n + '</b>' + dishStock(d) + '</div><span class="sh-help">' + d.en + ' · ' + d.c + '</span>',
    '<div class="dn-mdpic' + (d.img ? '' : ' k-' + (CK[d.c] || 'gray')) + '">' + (d.img ? '<img src="' + d.img + '" alt="">' : '<span data-notr="1">' + d.n.slice(0, 2) + '</span>') + '<span class="sh-chip sh-chip--on-card">更换图片</span></div>' +
    '<div class="sh-cells"><div class="sh-cell"><span class="k">中文名</span><span class="v go">' + d.n + '</span></div><div class="sh-cell"><span class="k">英文名</span><span class="v go" data-notr="1">' + d.en + '</span></div><div class="sh-cell"><span class="k">分类</span><span class="v go">' + d.c + '</span></div><div class="sh-cell"><span class="k">价格</span><span class="v go">¥' + d.p + (d.spec ? ' 起' : '') + '</span></div><div class="sh-cell"><span class="k">会员价</span><span class="v">待定</span></div></div>' +
    '<div class="dn-block"><div class="dn-sec"><span>规格与做法</span><a class="sh-link sh-hit" data-act="msub:规格">管理</a></div><div class="row">' + (d.spec ? ['份量', '辣度', '加料'] : ['做法']).map(function (x) { return '<span class="sh-chip">' + x + '</span>'; }).join('') + '</div></div>' +
    '<div class="dn-block"><div class="dn-sec"><span>标记</span></div><div class="row">' + Object.keys(TAGS).map(function (t) { var on = (d.tg || []).indexOf(t) > -1; return '<span class="sh-filter' + (on ? ' is-on' : '') + '" data-act="mdtag:' + t + '">' + ico(TAGS[t]) + t + '</span>'; }).join('') + '</div></div>' +
    '<div class="dn-block"><div class="dn-sec"><span>售卖</span></div><div class="sh-cells"><div class="sh-cell"><span class="k">今日库存</span><span class="v go">' + (d.s == null ? '不限' : d.s) + '</span></div><div class="sh-cell"><span class="k">限时供应</span><span class="v go">' + (d.timed || '全天') + '</span></div><div class="sh-cell"><span class="k">沽清</span><span class="sh-switch' + (left(d) === 0 ? ' is-on' : '') + '" data-act="mdsold" role="switch" aria-label="沽清"></span></div></div></div>' +
    '<div class="dn-block"><div class="dn-sec"><span>渠道</span><a class="sh-link sh-hit" data-act="msub:同步">去同步</a></div><div class="sh-cells">' + CHANNELS.map(function (c) { var st = SYNC[d.n][c[0]]; return '<div class="sh-cell"><span class="k mlabel">' + ico(c[2]) + c[1] + '</span><span class="v">' + stx('sync', st, null, 'dot') + (st === 'failed' && SYNCWHY[d.n] ? '<br><small class="sh-help">' + SYNCWHY[d.n] + '</small>' : '') + '</span></div>'; }).join('') + '</div></div>',
    '<div class="dn-acts">' + btn('下架', 'sh-btn--muted', 'toast:已下架，各渠道同步后生效') + btn('保存', 'sh-btn--block', 'mdsave') + '</div>');
  return panel(head, body) + det;
}
function mdCats() {
  var all = CATS.slice(1).concat([['甜品', 'brown', 'Desserts']]);
  var head = '<div class="row row--between"><b class="h-title">分类</b>' + btn(ico('plus') + '新建分类', 'sh-btn--soft sh-btn--sm', 'toast:已新建分类') + '</div><span class="sh-help">拖动排序；「热销」是自动分类，不在这里维护。分类要映射到外卖平台类目才能同步。</span>';
  var body = '<div class="sh-table" style="--cols:24px 24px minmax(0,1fr) minmax(0,1fr) 64px 120px 52px"><div><span></span><span></span><span>中文名</span><span>English</span><span class="r">菜品</span><span>平台类目</span><span class="r">显示</span></div>' +
    all.map(function (c, i) { var n = DISHES.filter(function (d) { return d.c === c[0]; }).length; return '<div data-act="mcsel:' + i + '" class="' + (S.mcsel === i ? 'is-on' : '') + '"><span class="dn-grip">' + ico('grip-vertical') + '</span><span><i class="dn-mark k-' + c[1] + '"></i></span><span data-notr="1">' + c[0] + '</span><span data-notr="1">' + c[2] + '</span><span class="r">' + n + '</span><span>' + (c[0] === '主食' ? stx('sync', 'failed', '未映射', 'dot') : stx('sync', 'synced', '已映射', 'dot')) + '</span><span class="r"><span class="sh-switch is-on"></span></span></div>'; }).join('') + '</div>';
  var c = all[S.mcsel] || all[0];
  var det = side('<b class="h-title">' + c[0] + '</b><span class="sh-help">' + c[2] + '</span>', '<div class="sh-cells"><div class="sh-cell"><span class="k">中文名</span><span class="v go">' + c[0] + '</span></div><div class="sh-cell"><span class="k">英文名</span><span class="v go">' + c[2] + '</span></div><div class="sh-cell"><span class="k">打印到</span><span class="v go">' + ({ 凉菜: '凉菜档口', 饮品: '吧台' }[c[0]] || '热菜档口') + '</span></div><div class="sh-cell"><span class="k">美团类目</span><span class="v go">' + (c[0] === '主食' ? '未映射' : '中餐 › ' + c[0]) + '</span></div><div class="sh-cell"><span class="k">饿了么类目</span><span class="v go">中餐 › ' + c[0] + '</span></div></div>' +
    '<span class="sh-gt"><span>分类色</span></span><div class="row">' + ['red', 'orange', 'yellow', 'green', 'teal', 'blue', 'purple', 'brown'].map(function (k) { return '<span class="dn-swatch sh-hit k-' + k + (k === c[1] ? ' is-on' : '') + '" style="--c:var(--ks)"></span>'; }).join('') + '</div>', '<div class="dn-acts">' + btn('删除', 'sh-btn--danger', 'toast:分类下还有菜品，不能删除') + btn('保存', 'sh-btn--block', 'toast:已保存分类') + '</div>');
  return panel(head, body) + det;
}
function mdMods() {
  var head = '<div class="row row--between"><b class="h-title">规格与做法</b>' + btn(ico('plus') + '新建规格组', 'sh-btn--soft sh-btn--sm', 'toast:已新建规格组') + '</div><span class="sh-help">规格组可以挂到多道菜上；点菜时按组弹出选择（规格弹窗）。</span>';
  var body = '<div class="sh-table" style="--cols:minmax(0,1fr) 120px minmax(0,2fr) 72px"><div><span>规格组</span><span>规则</span><span>选项</span><span class="r">菜品</span></div>' +
    MODS.map(function (m, i) { return '<div data-act="mmsel:' + i + '" class="' + (S.mmsel === i ? 'is-on' : '') + '"><span><b>' + m[0] + '</b></span><span class="sh-help">' + m[1] + '</span><span>' + m[2].map(function (o) { return '<span class="sh-chip">' + o + '</span>'; }).join(' ') + '</span><span class="r">' + m[3] + ' 道</span></div>'; }).join('') + '</div>';
  var m = MODS[S.mmsel] || MODS[0];
  var det = side('<b class="h-title">' + m[0] + '</b><span class="sh-help">' + m[1] + ' · 用在 ' + m[3] + ' 道菜</span>', '<span class="sh-gt"><span>选项 · 加价</span></span><div class="sh-cells">' + m[2].map(function (o) { var p = o.split(' '); return '<div class="sh-cell"><span class="k mlabel"><span class="dn-grip">' + ico('grip-vertical') + '</span>' + p[0] + '</span><span class="v go">' + (p[1] || '不加价') + '</span></div>'; }).join('') + '</div>' + btn(ico('plus') + '加选项', 'sh-btn--muted sh-btn--sm', 'toast:已加选项') +
    '<span class="sh-gt"><span>规则</span></span><div class="sh-cells"><div class="sh-cell"><span class="k">必选</span><span class="sh-switch' + (/必选/.test(m[1]) ? ' is-on' : '') + '"></span></div><div class="sh-cell"><span class="k">多选</span><span class="sh-switch' + (/多选/.test(m[1]) ? ' is-on' : '') + '"></span></div><div class="sh-cell"><span class="k">同步到外卖平台</span><span class="sh-switch is-on"></span></div></div>', '<div class="dn-acts">' + btn('保存', 'sh-btn--block', 'toast:已保存规格组，相关菜品变为待同步') + '</div>');
  return panel(head, body) + det;
}
function mdSets() {
  var head = '<div class="row row--between"><b class="h-title">套餐</b>' + btn(ico('plus') + '新建套餐', 'sh-btn--soft sh-btn--sm', 'toast:已新建套餐') + '</div><span class="sh-help">套餐由若干「分组」组成，每组规定选几份；可设置是否允许换菜、换菜补差价。</span>';
  var body = '<div class="dn-ocards">' + SETS.map(function (x, i) { return '<div class="dn-ocard' + (S.mssel === i ? ' is-on' : '') + '" style="--d:var(--c-st-progress-dot)" data-act="mssel:' + i + '"><div class="t"><b>' + x.n + '</b>' + stx('stock', 'in_stock', '在售', 'dot') + '</div><span class="sh-help">' + x.groups.map(function (g) { return g[0]; }).join(' · ') + '</span><span class="sh-help">' + (x.swap ? '允许换菜 · 补差价' : '不可换菜') + '</span><span class="a">¥' + x.p + '</span></div>'; }).join('') + '</div>';
  var x = SETS[S.mssel] || SETS[0];
  var det = side('<div class="row row--between"><b class="h-title">' + x.n + '</b><b class="num">¥' + x.p + '</b></div><span class="sh-help" data-notr="1">' + x.en + '</span>',
    x.groups.map(function (g) { return '<div class="dn-block"><div class="dn-sec"><span>' + g[0] + '</span><a class="sh-link sh-hit" data-act="toast:已打开分组编辑">编辑</a></div><div class="row">' + g[1].map(function (n) { return '<span class="sh-chip">' + n + '</span>'; }).join('') + '</div></div>'; }).join('') +
    '<div class="sh-cells"><div class="sh-cell"><span class="k">允许换菜</span><span class="sh-switch' + (x.swap ? ' is-on' : '') + '"></span></div><div class="sh-cell"><span class="k">换菜补差价</span><span class="sh-switch' + (x.swap ? ' is-on' : '') + '"></span></div><div class="sh-cell"><span class="k">渠道</span><span class="v">堂食 · 扫码点餐</span></div></div>' +
    '<div class="sh-note">外卖平台的套餐需要按平台规则单独上架，默认不同步。</div>', '<div class="dn-acts">' + btn(ico('plus') + '加分组', 'sh-btn--muted', 'toast:已加分组') + btn('保存', 'sh-btn--block', 'toast:已保存套餐') + '</div>');
  return panel(head, body) + det;
}
function mdSync() {
  var cnt = function (ch, st) { return Object.keys(SYNC).filter(function (k) { return SYNC[k][ch] === st; }).length; };
  var head = '<div class="row row--between"><b class="h-title">渠道同步</b>' + btn(ico('refresh-cw') + (S.syncing ? '同步中…' : '全部同步'), S.syncing ? 'sh-btn--off sh-btn--sm' : 'sh-btn--sm', S.syncing ? '' : 'syncall') + '</div><span class="sh-help">把菜品、分类、规格、价格、库存推送到扫码点餐和外卖平台。平台审核可能需要几分钟。</span>';
  var cards = '<div class="dn-ocards">' + CHANNELS.slice(1).map(function (c) {
    var f = cnt(c[0], 'failed'), p = cnt(c[0], 'pending'), ok = cnt(c[0], 'synced');
    return '<div class="dn-ocard' + (f ? ' is-alert' : '') + '" style="' + stv(f ? 'failed' : p ? 'waiting' : 'done') + '"><div class="t"><b>' + c[1] + '</b>' + stx('device', 'online', '已连接', 'dot') + '</div><span class="sh-help">上次同步 ' + (S.synced ? '刚刚' : '12:28') + '</span><span class="row">' + stx('sync', 'synced', '已同步 ' + ok, 'dot') + stx('sync', 'pending', '待同步 ' + p, 'dot') + (f ? stx('sync', 'failed', '失败 ' + f, 'dot') : '') + '</span></div>';
  }).join('') + '</div>';
  var issues = Object.keys(SYNC).filter(function (k) { return SYNC[k].mt === 'failed' || SYNC[k].elm === 'failed' || SYNC[k].mt === 'pending' || SYNC[k].elm === 'pending'; });
  var body = cards + '<div class="dn-block"><div class="dn-sec"><span>待处理 · ' + issues.length + ' 道菜</span><span>失败的先改，再同步</span></div><div class="sh-table" style="--cols:minmax(0,1fr) 112px 112px minmax(0,1.4fr) 88px"><div><span>菜品</span><span>美团外卖</span><span>饿了么</span><span>原因</span><span></span></div>' +
    issues.map(function (k) { var x = SYNC[k], d = DISHES.filter(function (y) { return y.n === k; })[0]; return '<div><span><b>' + k + '</b></span><span>' + stx('sync', x.mt, null, 'dot') + '</span><span>' + stx('sync', x.elm, null, 'dot') + '</span><span class="sh-help">' + (SYNCWHY[k] || '菜品信息有改动') + '</span><span class="r">' + btn(SYNCWHY[k] ? '去修改' : '同步', 'sh-btn--muted sh-btn--sm', SYNCWHY[k] ? 'mdgo:' + DISHES.indexOf(d) : 'sync1:' + k) + '</span></div>'; }).join('') + '</div></div>';
  var det = side('<b class="h-title">同步记录</b>', '<div class="sh-cells">' + [['12:28', '美团外卖', '推送 15 道 · 失败 2', 'failed'], ['12:28', '饿了么', '推送 15 道 · 成功', 'synced'], ['09:02', '扫码点餐', '推送 15 道 · 成功', 'synced'], ['昨天 22:10', '美团外卖', '下架 1 道（双人套餐）', 'unlisted']].map(function (r) { return '<div class="sh-cell"><span class="k">' + r[0] + '<br><small>' + r[1] + ' · ' + r[2] + '</small></span>' + stx('sync', r[3], null, 'dot') + '</div>'; }).join('') + '</div>' +
    '<div class="sh-note">平台价可以和堂食价不同（设置 › 价格方案 › 外卖价）；库存和沽清实时同步，其余改动需要手动推送。</div>');
  return panel(head, body) + det;
}
ACT['msub'] = function (v, k) {
    S.msub = v; return;
};
ACT['mdf'] = function (v, k) {
    S.mdf = v; return;
};
ACT['mdc'] = function (v, k) {
    S.mdc = v; return;
};
ACT['mdsel'] = ACT['mdgo'] = function (v, k) {
    S.mdsel = +v; if (k === 'mdgo') S.msub = '菜品'; return;
};
ACT['mcsel'] = function (v, k) {
    S.mcsel = +v; return;
};
ACT['mmsel'] = function (v, k) {
    S.mmsel = +v; return;
};
ACT['mssel'] = function (v, k) {
    S.mssel = +v; return;
};
ACT['mdtag'] = function (v, k) {
    var dd = DISHES[S.mdsel] || DISHES[0], tl = dd.tg || (dd.tg = []), tx = tl.indexOf(v); if (tx > -1) tl.splice(tx, 1); else tl.push(v); markPending(dd); return;
};
ACT['mdsold'] = function (v, k) {
    var ds = DISHES[S.mdsel] || DISHES[0]; ds.s = left(ds) === 0 ? 20 : qtyOf(ds.n); toast(left(ds) === 0 ? ds.n + ' 已沽清，各渠道实时同步' : ds.n + ' 已恢复售卖'); return;
};
ACT['mdsave'] = function (v, k) {
    markPending(DISHES[S.mdsel] || DISHES[0]); toast('已保存，美团、饿了么变为待同步'); return;
};
ACT['mdnew'] = function (v, k) {
    toast('已新建菜品草稿'); return;
};
ACT['syncall'] = function (v, k) {
    S.syncing = true; Object.keys(SYNC).forEach(function (n) { ['mt', 'elm'].forEach(function (ch) { if (SYNC[n][ch] === 'pending') SYNC[n][ch] = 'syncing'; }); }); setTimeout(function () { Object.keys(SYNC).forEach(function (n) { ['mt', 'elm'].forEach(function (ch) { if (SYNC[n][ch] === 'syncing') SYNC[n][ch] = 'synced'; }); }); S.syncing = false; S.synced = true; toast('同步完成：待同步的已推送；失败的 3 道需要先修改'); render(); }, 1200); return;
};
ACT['sync1'] = function (v, k) {
    SYNC[v].mt = SYNC[v].mt === 'pending' ? 'synced' : SYNC[v].mt; SYNC[v].elm = SYNC[v].elm === 'pending' ? 'synced' : SYNC[v].elm; toast(v + ' 已同步'); return;
};
MODULES.menu.render = menuWB;
MODULES.menu.sections = ['菜品', '分类', '规格', '套餐', '同步'];
