/* 模块 · 报表：概况、时段、菜品排行、支付方式、会员与储值、优惠、退菜与赠送。 */
/* 图表规则：单一系列只用一种颜色（主色，墨黑皮肤下是墨色）、不画网格和灰底轨道；每根柱子悬停给出数值；每张图下面都有同样数据的表 */
var RP = {
  hours: [['10', 320], ['11', 980], ['12', 2860], ['13', 2410], ['14', 620], ['15', 280], ['16', 240], ['17', 410], ['18', 1980], ['19', 2640], ['20', 1720], ['21', 540], ['22', 120]],
  dishes: [['宫保鸡丁', 38, 1824], ['米饭', 96, 288], ['酸菜鱼', 21, 1638], ['麻婆豆腐', 26, 676], ['水煮鱼', 14, 1232], ['酸梅汤', 33, 396], ['夫妻肺片', 17, 646], ['小炒黄牛肉', 12, 816]],
  pays: [['微信', 4102, 22], ['支付宝', 2340, 12], ['现金', 1280, 6], ['银行卡', 620, 3], ['储值', 300, 3]],
  promos: [['非高峰 8 折（活动）', 412, 31], ['满 200 减 15（券）', 90, 6], ['抹零', 38, 41], ['人工折扣', 72, 3]],
  pvs: [['美团·点评 团购券', 1240, 14], ['抖音 团购券', 696, 6]],
  voids: [['11:52', '酸梅汤 ×1', '退菜', '上错菜', '李敏', '张伟'], ['12:20', '水煮鱼 ×1', '赠送', '服务补偿', '王芳', '张伟'], ['12:41', '米饭 ×2', '退菜', '客人退菜', '李敏', '—'], ['13:05', '整单 ¥20', '人工折扣', '出品问题', '李敏', '张伟']],
  layers: [['新客', 12], ['常客', 21], ['熟客', 9], ['沉睡', 4]]
};
function vbars(data, fmt, unit) {
  var max = Math.max.apply(null, data.map(function (d) { return d[1]; }));
  return '<div class="dn-vbars" role="img" aria-label="' + unit + '">' + data.map(function (d) { return '<span class="col" title="' + d[0] + ' · ' + fmt(d[1]) + '"><i style="--v:' + Math.max(2, Math.round(d[1] / max * 100)) + '%"></i><small>' + d[0] + '</small></span>'; }).join('') + '</div>';
}
function hbars(data, fmt, idx) {
  var max = Math.max.apply(null, data.map(function (d) { return d[idx]; }));
  return '<div class="dn-hbars">' + data.map(function (d) { return '<div class="r" title="' + d[0] + ' · ' + fmt(d[idx]) + '"><span class="l">' + d[0] + '</span><span class="t"><i style="--v:' + Math.max(1, Math.round(d[idx] / max * 100)) + '%"></i></span><span class="v num">' + fmt(d[idx]) + '</span></div>'; }).join('') + '</div>';
}
function yuan(v) { return '¥' + v.toLocaleString(); }
function tile(label, value, note, st) { return '<div class="dn-kpi"><span class="l">' + label + '</span><b class="num">' + value + '</b>' + (note ? '<span class="d' + (st ? ' is-' + st : '') + '">' + note + '</span>' : '') + '</div>'; }
function card(title, body, foot) { return '<div class="dn-block"><div class="dn-sec"><span>' + title + '</span>' + (foot || '') + '</div>' + body + '</div>'; }
function reportsWB() {
  var nav = rail([{ cap: '报表' }, { v: '概况', icon: ico('layout-dashboard'), label: '营业概况' }, { v: '时段', icon: ico('clock'), label: '时段' }, { v: '菜品', icon: ico('soup'), label: '菜品排行' }, { v: '支付', icon: ico('wallet'), label: '支付方式' },
    { v: '会员', icon: ico('users'), label: '会员与储值' }, { v: '优惠', icon: ico('ticket-percent'), label: '优惠' }, { v: '退赠', icon: ico('undo-2'), label: '退菜与赠送' }], S.rsec, 'rsec');
  var head = '<div class="sh-toolbar"><span class="sh-seg">' + ['今天', '昨天', '本周', '本月'].map(function (x) { return '<span class="' + (S.rrange === x ? 'is-on' : '') + '" data-act="rrange:' + x + '">' + x + '</span>'; }).join('') + '</span><span class="sh-help">南山店 · 截至 12:30 · 数据每 5 分钟更新</span><span class="dn-sp"></span>' + btn(ico('printer') + '打印', 'sh-btn--muted sh-btn--sm', 'toast:已发送到前台打印机') + btn(ico('download') + '导出', 'sh-btn--muted sh-btn--sm', 'toast:已导出 Excel，发到店长邮箱') + '</div>';
  var tot = RP.hours.reduce(function (a, h) { return a + h[1]; }, 0), sec = S.rsec, body;
  var tHours = '<div class="sh-table" style="--cols:repeat(13,minmax(0,1fr))"><div>' + RP.hours.map(function (h) { return '<span class="r">' + h[0] + ' 时</span>'; }).join('') + '</div><div>' + RP.hours.map(function (h) { return '<span class="r">' + h[1] + '</span>'; }).join('') + '</div></div>';
  if (sec === '时段') body = card('营业额 · 按小时', vbars(RP.hours, yuan, '营业额按小时'), '<span>午市 12–13 时、晚市 19 时最高</span>') + card('数据', tHours);
  else if (sec === '菜品') {
    var by = S.rby === 'amt' ? 2 : 1, list = RP.dishes.slice().sort(function (a, b) { return b[by] - a[by]; });
    body = card('菜品排行 · ' + (by === 2 ? '按金额' : '按份数'), hbars(list, by === 2 ? yuan : function (v) { return v + ' 份'; }, by), '<span class="sh-seg">' + [['qty', '份数'], ['amt', '金额']].map(function (x) { return '<span class="' + (S.rby === x[0] ? 'is-on' : '') + '" data-act="rby:' + x[0] + '">' + x[1] + '</span>'; }).join('') + '</span>') +
      card('数据', '<div class="sh-table" style="--cols:32px minmax(0,1fr) 80px 96px 80px"><div><span>#</span><span>菜品</span><span class="r">份数</span><span class="r">金额</span><span class="r">占比</span></div>' + list.map(function (d, i) { return '<div><span>' + (i + 1) + '</span><span>' + d[0] + '</span><span class="r">' + d[1] + '</span><span class="r">' + yuan(d[2]) + '</span><span class="r">' + Math.round(d[2] / 8642 * 100) + '%</span></div>'; }).join('') + '</div>');
  }
  else if (sec === '支付') body = card('支付方式 · 金额', hbars(RP.pays, yuan, 1), '<span>储值消费不重复计入营业额</span>') + card('数据', '<div class="sh-table" style="--cols:minmax(0,1fr) 96px 72px 72px"><div><span>方式</span><span class="r">金额</span><span class="r">笔数</span><span class="r">占比</span></div>' + RP.pays.map(function (p) { return '<div><span>' + p[0] + '</span><span class="r">' + yuan(p[1]) + '</span><span class="r">' + p[2] + '</span><span class="r">' + Math.round(p[1] / 8642 * 100) + '%</span></div>'; }).join('') + '</div>');
  else if (sec === '会员') body = '<div class="dn-kpis">' + tile('会员消费占比', '38%', '较昨天 +4 个百分点', 'up') + tile('新增会员', '6', '其中线索 2') + tile('储值收入', '¥2,300', '不计入营业额') + tile('储值消费', '¥1,180', '13 笔') + tile('储值余额（本店）', '¥48,320', '共 126 人') + '</div>' +
    card('会员分层 · 今天到店', hbars(RP.layers, function (v) { return v + ' 人'; }, 1)) + card('说明', '<p class="sh-help">积分是平台积分，线下支付不发不抵，本店报表不统计积分。</p>');
  else if (sec === '优惠') body = '<div class="dn-kpis">' + tile('优惠合计', '¥612', '占营业额 7.1%') + tile('活动', '¥412', '31 单') + tile('券', '¥90', '6 张') + tile('人工折扣与抹零', '¥110', '44 笔 · 3 笔需授权') + tile('平台券抵扣', '¥1,936', '20 张 · 平台结算，不计优惠') + '</div>' +
    card('优惠构成 · 金额', hbars(RP.promos, yuan, 1)) + card('数据', '<div class="sh-table" style="--cols:minmax(0,1fr) 96px 72px"><div><span>来源</span><span class="r">金额</span><span class="r">单数</span></div>' + RP.promos.map(function (p) { return '<div><span>' + p[0] + '</span><span class="r">' + yuan(p[1]) + '</span><span class="r">' + p[2] + '</span></div>'; }).join('') + '</div>') +
    card('平台券抵扣 · 平台结算', hbars(RP.pvs, yuan, 1), '<span>按核销金额计，到账以平台结算单为准</span>');
  else if (sec === '退赠') body = '<div class="dn-kpis">' + tile('退菜', '2 笔', '¥18.00') + tile('赠送', '1 笔', '¥88.00') + tile('人工折扣', '1 笔', '¥20.00') + tile('需授权', '3 笔', '全部已授权') + '</div>' +
    card('明细', '<div class="sh-table" style="--cols:56px minmax(0,1fr) 80px 96px 64px 64px"><div><span>时间</span><span>内容</span><span>类型</span><span>原因</span><span>操作人</span><span>授权人</span></div>' + RP.voids.map(function (v) { return '<div>' + v.map(function (x, i) { return '<span>' + (i === 2 ? stx('item', x === '退菜' ? 'voided' : x === '赠送' ? 'comp' : 'repriced', x, 'dot') : x) + '</span>'; }).join('') + '</div>'; }).join('') + '</div>');
  else body = '<div class="dn-kpis">' + tile('营业额', '¥8,642', '较昨天同时段 +6.2%', 'up') + tile('单数', '46', '+3') + tile('客单价', '¥187.9', '+2.1%', 'up') + tile('翻台率', '1.6', '−0.1', 'down') + tile('优惠合计', '¥612', '7.1%') + tile('储值收入', '¥2,300', '不计营业额') + '</div>' +
    '<div class="dn-rgrid">' + card('营业额 · 按小时', vbars(RP.hours, yuan, '营业额按小时'), '<a class="sh-link sh-hit" data-act="rsec:时段">详情</a>') + card('支付方式', hbars(RP.pays, yuan, 1), '<a class="sh-link sh-hit" data-act="rsec:支付">详情</a>') +
    card('菜品 · 前 5（份数）', hbars(RP.dishes.slice().sort(function (a, b) { return b[1] - a[1]; }).slice(0, 5), function (v) { return v + ' 份'; }, 1), '<a class="sh-link sh-hit" data-act="rsec:菜品">详情</a>') + card('桌台', '<div class="dn-kpis dn-kpis--sm">' + tile('用餐中', '8 桌') + tile('待结账', '3 桌') + tile('平均用餐', '52 分') + tile('超时未上菜', '1 桌', '', 'down') + '</div>') + '</div>';
  return nav + panel(head, body);
}
ACT['rsec'] = function (v, k) {
    S.rsec = v; return;
};
ACT['rrange'] = function (v, k) {
    S.rrange = v; toast('已切换到「' + v + '」'); return;
};
ACT['rby'] = function (v, k) {
    S.rby = v; return;
};
MODULES.reports.render = reportsWB;
MODULES.reports.sections = ['概况', '时段', '菜品', '支付', '会员', '优惠', '退赠'];
