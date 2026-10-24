/* 模块 · 设置：功能菜单（11 组）、设置页、界面与显示（页首开关、侧滑「设置」共用）。 */
/* 界面与显示：页首开关、侧滑「设置」、设置工作台共用 */
function opts(k, list) { return '<div class="row">' + list.map(function (o) { var on = k === 'shape' ? S.frame + '-' + S.btn === o[0] : S[k] === o[0]; return '<span class="sh-option' + (on ? ' is-on' : '') + '" data-k="' + k + '" data-v="' + o[0] + '">' + o[1] + (o[2] ? '<small>' + o[2] + '</small>' : '') + '</span>'; }).join('') + '</div>'; }
function styleControls() {
  return '<span class="sh-gt"><span>快捷操作 · 结单区头下一排，最多 5 个</span></span><div class="row">' + Object.keys(QUICK).map(function (k) { return '<span class="sh-filter' + (S.quick.indexOf(k) > -1 ? ' is-on' : '') + '" data-act="qk:' + k + '">' + ico(QUICK[k][1]) + QUICK[k][0] + '</span>'; }).join('') + '</div>' +
    '<span class="sh-gt"><span>语言</span></span>' + opts('lang', LANGS) + '<span class="sh-gt"><span>菜名</span></span>' + opts('names', NAMES) +
    '<span class="sh-gt"><span>菜品图</span></span>' + opts('pic', PICS) + '<span class="sh-gt"><span>卡片排布</span></span>' + opts('card', CARDS) +
    '<span class="sh-gt"><span>形状方案</span></span>' + opts('shape', SHAPES) + '<span class="sh-gt"><span>框架圆角 · 面板与卡片</span></span>' + opts('frame', FRAMES) +
    '<span class="sh-gt"><span>按钮形状 · 按钮与控件</span></span>' + opts('btn', BTNS) +
    '<span class="sh-gt"><span>字体 · 中文统一思源黑体</span></span>' + opts('font', FONTS) + '<span class="sh-gt"><span>密度</span></span>' + opts('density', DENS) +
    '<span class="sh-gt"><span>皮肤</span></span><div class="row">' + SKINS.map(function (k) { return '<span class="dn-swatch' + (S.skin === k[0] ? ' is-on' : '') + ' sh-hit" style="--c:' + k[2] + '" title="' + k[1] + '" aria-label="' + k[1] + '" data-k="skin" data-v="' + k[0] + '"></span>'; }).join('') + '</div>' +
    '<span class="sh-gt"><span>布局</span></span><div class="row">' + LAYOUTS.map(function (l) { return '<span class="dn-layout' + (S.nav === l[1] && S.detail === l[2] ? ' is-on' : '') + '" data-k="layout" data-v="' + l[1] + '-' + l[2] + '">' + l[0] + '</span>'; }).join('') + '</div>';
}

/* 每项：[键, 中文, English, 设置行[[中文, English, 值]]]；值 on / off 画开关，其余画「值 ›」 */
var MENU = [
  ['ops', '运营', 'Operations', [
    ['overview', '营业概况', 'Overview'], ['handover', '交接班', 'Shift handover', [['交班需清点钱箱', 'Count drawer at handover', 'on'], ['交班打印报表', 'Print report at handover', 'on'], ['允许跨班挂单', 'Carry held orders across shifts', 'off']]],
    ['dayclose', '日结', 'Day close', [['自动日结时间', 'Auto day close at', '04:00'], ['未结订单提醒', 'Warn on open orders', 'on']]],
    ['approvals', '审批记录', 'Approvals', [['待审批', 'Pending', '3'], ['授权日志', 'Approval log', '›']]]]],
  ['tables', '桌台', 'Tables', [
    ['areas', '区域与桌台', 'Areas & tables', [['大厅', 'Hall', '12 桌'], ['包间', 'Rooms', '6 桌'], ['外带', 'Takeaway', '4 个']]],
    ['qr', '桌台码', 'Table QR codes', [['扫码点餐', 'QR ordering', 'on'], ['下载桌台码', 'Download codes', '›']]],
    ['booking', '预订规则', 'Reservations', [['保留时长', 'Hold for', '15 分钟'], ['预订提醒', 'Reminder', 'on']]],
    ['turn', '开台与清台', 'Seating & clearing', [['开台必填人数', 'Guests required', 'on'], ['结账后自动清台', 'Clear after payment', 'on'], ['茶位费', 'Tea charge', '¥3 / 人']]]]],
  ['pay', '订单与支付', 'Orders & payment', [
    ['methods', '支付方式', 'Payment methods'],
    ['fees', '服务费与打包费', 'Service & packaging', [['服务费（堂食）', 'Service (dine-in)', '5%'], ['打包费（外带、外卖）', 'Packaging (to go)', '¥2 / 单']]],
    ['rounding', '抹零规则', 'Rounding', [['抹零方式', 'Rounding', '抹分'], ['允许手动抹零', 'Manual rounding', 'off']]],
    ['discount', '折扣与改价', 'Discounts & price edits', [['整单折扣需授权', 'Order discount needs approval', 'on'], ['最低折扣', 'Lowest discount', '8 折'], ['改价需授权', 'Price edit needs approval', 'on']]],
    ['refund', '退菜与反结账', 'Voids & reopen', [['退菜原因', 'Void reasons', '4 条'], ['反结账需授权', 'Reopen needs approval', 'on'], ['退款原路返回', 'Refund to original method', 'on']]],
    ['invoice', '发票', 'Invoices', [['电子发票', 'E-invoice', 'on'], ['开票方', 'Issuer', '已配置']]]]],
  ['channel', '外卖与渠道', 'Delivery & channels', [
    ['platforms', '平台对接', 'Platforms', [['美团', 'Meituan', '已连接'], ['饿了么', 'Ele.me', '已连接'], ['小程序', 'Mini program', '已连接']]],
    ['accept', '接单规则', 'Order intake', [['自动接单', 'Auto-accept', 'off'], ['接单后自动送厨', 'Send to kitchen on accept', 'on'], ['新单提示音', 'New order sound', 'on']]],
    ['ready', '出餐通知', 'Ready alerts', [['出餐后通知骑手', 'Notify rider when ready', 'on']]],
    ['vouchers', '团购验券', 'Group-buy vouchers', [['美团·点评', 'Meituan · Dianping', '已授权'], ['抖音来客', 'Douyin', '已授权'], ['快手', 'Kuaishou', '去授权 ›'], ['小红书', 'Xiaohongshu', '未开通'],
      ['与店内活动同享', 'Combine with store campaigns', 'off'], ['与本店券同享', 'Combine with store coupons', 'off'], ['每单最多张数', 'Max per order', '3 张'], ['核销时机', 'Redeem at', '收款完成后'], ['撤销核销', 'Undo redemption within', '24 小时内']]]]],
  ['staff', '员工与权限', 'Staff & roles', [
    ['people', '员工', 'Staff'], ['roles', '角色与权限', 'Roles & permissions'],
    ['codes', '授权码', 'Approval codes', [['主管授权码', 'Manager code', '已设置'], ['授权码有效期', 'Code expiry', '90 天']]],
    ['schedule', '排班', 'Schedules', [['早班', 'Morning', '09:00–15:00'], ['晚班', 'Evening', '15:00–23:00']]]]],
  ['device', '设备', 'Devices', [
    ['printers', '打印机', 'Printers'], ['routing', '档口路由', 'Station routing'],
    ['drawer', '钱箱', 'Cash drawer', [['收现金时自动开箱', 'Open on cash payment', 'on'], ['开箱记录', 'Drawer log', '›']]],
    ['display', '客显屏', 'Customer display', [['显示订单明细', 'Show order lines', 'on'], ['空闲时轮播', 'Slideshow when idle', 'on']]],
    ['kds', '厨房屏', 'Kitchen display', [['厨房屏', 'KDS screens', '2 台'], ['超时提醒', 'Late alert after', '15 分钟']]],
    ['scale', '电子秤', 'Scale', [['称重菜品', 'Weighed items', '3 个'], ['连接', 'Connection', '未连接']]],
    ['scanner', '扫码枪', 'Scanner', [['扫码枪', 'Scanner', '已连接']]],
    ['network', '网络与离线', 'Network & offline', [['离线收银', 'Offline mode', 'on'], ['恢复后自动同步', 'Sync when back online', 'on']]]]],
  ['store', '门店', 'Store', [
    ['info', '门店信息', 'Store info', [['门店名称', 'Store name', '虹选小馆 · 南山店'], ['联系电话', 'Phone', '0755-8888 6666']]],
    ['hours', '营业时间', 'Opening hours', [['营业时间', 'Hours', '10:00–22:00'], ['休息日', 'Closed on', '无']]],
    ['receipts', '小票模板', 'Receipt templates', [['结账单', 'Bill', '›'], ['厨房单', 'Kitchen ticket', '›'], ['小票打印语言', 'Receipt language', '中英双语']]],
    ['tax', '税率与币种', 'Tax & currency', [['税率', 'Tax rate', '6%'], ['币种', 'Currency', 'CNY ¥']]]]],
  ['display', '界面与显示', 'Display', [['shape', '界面与显示', 'Display']]],
  ['system', '系统', 'System', [
    ['sync', '数据同步', 'Data sync', [['上次同步', 'Last sync', '12:28'], ['立即同步', 'Sync now', '›']]],
    ['updates', '版本更新', 'Updates', [['当前版本', 'Version', '2.6.0'], ['自动更新', 'Auto-update', 'on']]],
    ['audit', '操作日志', 'Audit log', [['今日操作', 'Today', '128 条'], ['敏感操作', 'Sensitive', '6 条']]],
    ['help', '帮助与反馈', 'Help & feedback', [['使用手册', 'Manual', '›'], ['联系客服', 'Support', '400-800-8888']]]]]];
function mItem(g, k) { var it = null; MENU.forEach(function (x) { if (x[0] === g) x[3].forEach(function (y) { if (y[0] === k) it = y; }); }); return it; }
function valCell(v) { return v === 'on' || v === 'off' ? '<span class="sh-switch' + (v === 'on' ? ' is-on' : '') + '"></span>' : '<span class="v' + (v === '›' ? '' : ' go') + '">' + (v === '›' ? '›' : v) + '</span>'; }
function settingsWB() {
  var grp = MENU.filter(function (x) { return x[0] === S.sg; })[0] || MENU[0];
  var item = mItem(grp[0], S.si) || grp[3][0];
  /* 两列：左边一列菜单（管理模块 + 各组设置项），右边设置页。会员、菜品、班次从这里进各自的工作台 */
  var items = [{ cap: S.lang === 'en' ? 'Manage' : '管理' }].concat(Object.keys(MODULES).filter(function (k) { return MODULES[k].home === 'settings'; }).map(function (k) { return { v: 'wb.' + k, label: MODULES[k].name, en: MODEN[k], em: '›' }; }));
  MENU.forEach(function (g) { items.push({ cap: S.lang === 'en' ? g[2] : g[1] }); g[3].forEach(function (y) { items.push({ v: g[0] + '.' + y[0], label: y[1], en: y[2] }); }); });
  var nav = rail(items, grp[0] + '.' + item[0], 'snav');
  var page = '<div class="sh-panel sh-panel--page"><div class="hd"><div class="t"><b>' + biName(item[1], item[2]) + '</b><small data-notr="1">' + (S.lang === 'en' ? grp[2] + ' › ' + item[2] : grp[1] + ' › ' + item[1]) + '</small></div>' + (item[0] === 'shape' ? '' : btn('保存', 'sh-btn--sm', 'toast:已保存')) + '</div><div class="bd">' + settingsPage(grp, item) + '</div></div>';
  return nav + '<section class="ui-main"><div class="ui-set">' + page + '</div></section>';
}
function settingsPage(grp, item) {
  var k = item[0];
  if (k === 'shape') return styleControls();
  if (k === 'overview') return '<div class="sh-stat"><div><div class="n">¥8,642</div><div class="l">营业额</div></div><div><div class="n">46</div><div class="l">单数</div></div><div><div class="n">¥187.9</div><div class="l">客单价</div></div><div><div class="n">1.6</div><div class="l">今日翻台</div></div></div>' +
    '<div class="sh-cells"><div class="sh-cell"><span class="k">用餐中</span><span class="v num">8 桌</span></div><div class="sh-cell"><span class="k">待结账</span><span class="v num">2 桌</span></div><div class="sh-cell"><span class="k">外卖待接</span><span class="v num">2 单</span></div><div class="sh-cell"><span class="k">待审批</span><span class="v go num">3</span></div></div>';
  if (k === 'items' || k === 'stock') {
    var head = k === 'items' ? ['', '菜品', '分类', '价格', '库存', '上架'] : ['', '菜品', '分类', '今日库存', '余量', '沽清'];
    return '<div class="sh-table" style="--cols:44px minmax(0,1.6fr) minmax(0,1fr) 72px 72px 52px"><div>' + head.map(function (h, i) { return '<span' + (i > 2 ? ' class="r"' : '') + '>' + h + '</span>'; }).join('') + '</div>' +
      DISHES.map(function (d) {
        var c = CATS.filter(function (x) { return x[0] === d.c; })[0] || ['甜品', 'brown', 'Desserts'], rest = left(d);
        var th = '<span class="th k-' + (CK[d.c] || 'gray') + '">' + (d.img ? '<img src="' + d.img + '" alt="" loading="lazy">' : '<span data-notr="1">' + (S.lang === 'en' ? d.en[0] : d.n[0]) + '</span>') + '</span>';
        var nm = '<span class="nm2" data-notr="1"><b>' + (S.lang === 'en' ? d.en : d.n) + '</b><small>' + (S.lang === 'en' ? d.n : d.en) + '</small></span>';
        var cat = '<span>' + biName(c[0], c[2]) + '</span>';
        return '<div>' + th + nm + cat + (k === 'items' ? '<span class="r">¥' + d.p + (d.spec ? ' 起' : '') + '</span><span class="r">' + (d.s == null ? '不限' : d.s) + '</span><span class="r">' + valCell(rest === 0 ? 'off' : 'on') + '</span>'
          : '<span class="r">' + (d.s == null ? '不限' : d.s) + '</span><span class="r">' + (rest == null ? '—' : rest === 0 ? stx('stock', 'sold_out', '0', 'dot') : rest <= 5 ? stx('stock', 'low', String(rest), 'dot') : rest) + '</span><span class="r">' + valCell(rest === 0 ? 'on' : 'off') + '</span>') + '</div>';
      }).join('') + '</div>';
  }
  if (k === 'cats') return '<div class="sh-table" style="--cols:24px minmax(0,1fr) minmax(0,1fr) 72px 52px"><div><span></span><span>中文名</span><span>English</span><span class="r">菜品数</span><span class="r">显示</span></div>' +
    CATS.concat([['甜品', 'brown', 'Desserts']]).map(function (c) { var n = DISHES.filter(function (d) { return c[0] === '热销' ? d.hot : d.c === c[0]; }).length; return '<div><span><i class="dn-mark k-' + c[1] + '"></i></span><span data-notr="1">' + c[0] + '</span><span data-notr="1">' + c[2] + '</span><span class="r">' + n + '</span><span class="r">' + valCell('on') + '</span></div>'; }).join('') + '</div>';
  if (k === 'printers') return '<div class="sh-table" style="--cols:minmax(0,1.2fr) minmax(0,1fr) minmax(0,1.2fr) 96px 88px"><div><span>名称</span><span>用途</span><span>连接</span><span>状态</span><span></span></div>' +
    [['前台打印机', '结账单 · 小票', '192.168.1.21', 'online', '正常'], ['热菜档口', '厨房单', '192.168.1.31', 'no_paper', '缺纸'], ['凉菜档口', '厨房单', '192.168.1.32', 'online', '正常'], ['吧台', '饮品单', '192.168.1.33', 'online', '正常'], ['标签打印机', '外卖标签', 'USB', 'offline', '离线']].map(function (r) {
      return '<div><span>' + r[0] + '</span><span>' + r[1] + '</span><span class="num">' + r[2] + '</span><span>' + stx('device', r[3], r[4]) + '</span><span>' + btn('测试打印', 'sh-btn--muted sh-btn--sm', 'toast:已发送测试页') + '</span></div>'; }).join('') + '</div>' + btn('添加打印机', 'sh-btn--soft');
  if (k === 'routing') return '<div class="sh-table" style="--cols:minmax(0,1fr) minmax(0,1fr) 72px"><div><span>分类</span><span>打印到</span><span class="r">份数</span></div>' +
    CATS.slice(1).map(function (c) { var to = { 凉菜: '凉菜档口', 饮品: '吧台', 主食: '热菜档口', 汤羹: '热菜档口' }[c[0]] || '热菜档口'; return '<div><span>' + biName(c[0], c[2]) + '</span><span class="go">' + to + ' ›</span><span class="r">1</span></div>'; }).join('') + '</div>';
  if (k === 'methods') return '<div class="sh-cells">' + [['微信', '扫码，离线不可用', 'on'], ['支付宝', '扫码，离线不可用', 'on'], ['现金', '找零，自动开钱箱', 'on'], ['银行卡', '刷卡', 'on'], ['会员储值', '需验证手机号', 'on'], ['组合支付', '一单多种方式', 'on'], ['挂账', '企业客户月结', 'off']].map(function (r) { return '<div class="sh-cell"><span>' + r[0] + ' <span class="sh-help">' + r[1] + '</span></span>' + valCell(r[2]) + '</div>'; }).join('') + '</div>';
  if (k === 'people') return '<div class="sh-table" style="--cols:minmax(0,1fr) minmax(0,1fr) minmax(0,1fr) 64px"><div><span>姓名</span><span>角色</span><span>手机</span><span class="r">在职</span></div>' +
    [['李敏', '收银员'], ['王芳', '服务员'], ['张伟', '店长'], ['刘洋', '厨师长'], ['陈静', '收银员']].map(function (r, i) { return '<div><span>' + r[0] + '</span><span><span class="sh-chip">' + r[1] + '</span></span><span class="num">138****' + (6621 + i * 7) + '</span><span class="r">' + valCell('on') + '</span></div>'; }).join('') + '</div>' + btn('添加员工', 'sh-btn--soft');
  if (k === 'roles') return '<div class="sh-table" style="--cols:minmax(0,1fr) repeat(5,64px)"><div><span>权限</span><span class="r">店长</span><span class="r">收银员</span><span class="r">服务员</span><span class="r">厨师长</span><span class="r">财务</span></div>' +
    [['点餐与送厨', 'on', 'on', 'on', 'off', 'off'], ['收款', 'on', 'on', 'off', 'off', 'off'], ['赠送与退菜', 'on', 'off', 'off', 'off', 'off'], ['整单折扣', 'on', 'off', 'off', 'off', 'off'], ['沽清', 'on', 'on', 'off', 'on', 'off'], ['报表', 'on', 'off', 'off', 'off', 'on'], ['设置', 'on', 'off', 'off', 'off', 'off']].map(function (r) { return '<div><span>' + r[0] + '</span>' + r.slice(1).map(function (v) { return '<span class="r">' + valCell(v) + '</span>'; }).join('') + '</div>'; }).join('') + '</div>' +
    '<div class="sh-note">关掉的权限在需要时可以用主管授权码临时放行，每次放行都会记录。</div>';
  var rows = item[3] || [];
  return '<div class="sh-cells">' + rows.map(function (r) { return '<div class="sh-cell sh-cell--strong"><span class="k">' + biName(r[0], r[1]) + '</span>' + valCell(r[2]) + '</div>'; }).join('') + '</div>';
}

ACT['sg'] = function (v, k) {
    S.sg = v; var g0 = MENU.filter(function (x) { return x[0] === v; })[0]; S.si = g0 ? g0[3][0][0] : ''; return;
};
ACT['si'] = function (v, k) {
    S.si = v; return;
};
var MODEN = { menu: 'Menu', members: 'Members', shift: 'Shift' };
ACT['snav'] = function (v, k) {
    var p = v.split('.');
    if (p[0] === 'wb') { S.wb = p[1]; S.ov = 'none'; S.batch = false; S.sel = []; return; }
    S.sg = p[0]; S.si = p[1]; return;
};
ACT['goset'] = function (v, k) {
    S.wb = 'settings'; S.sg = 'display'; S.si = 'shape'; S.ov = 'none'; return;
};
ACT['qk'] = function (v, k) {
    var qi = S.quick.indexOf(v); if (qi > -1) S.quick.splice(qi, 1); else if (S.quick.length >= 5) { toast('最多 5 个，先去掉一个'); return; } else S.quick.push(v); return;
};
MODULES.settings.render = settingsWB;
MODULES.settings.sections = MENU.map(function (g) { return g[1]; });
