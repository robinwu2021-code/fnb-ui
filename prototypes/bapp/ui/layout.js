/* 布局：工作台的网格（导航 · 内容 · 详情）与系统栏。各模块的布局形态见 MODULES。 */
var LAYOUTS = [['L1', 'left', 'right', '导航左 · 详情右'], ['L2', 'top', 'right', '导航上 · 详情右'], ['L3', 'top', 'left', '导航上 · 详情左'],
               ['L4', 'right', 'left', '导航右 · 详情左'], ['L5', 'left', 'left', '导航左 · 详情左'], ['L6', 'right', 'right', '导航右 · 详情右']];
/* 工作台布局四种（详见 docs/全线点餐POS-布局与组件清单.md）：
 *   W1 三栏：导航 · 内容 · 详情     W2 两栏：账单清单 · 收款（无导航，清单固定在左）
 *   W3 两栏：内容 · 详情（无导航）  W4 两栏：导航 · 内容（无详情；设置页在内容里再分二级菜单与设置页）
 * 导航、详情可按页首「布局」L1–L6 换位；栏宽由各模块在 MODULES 里声明；home 为 settings 的模块不上系统栏页签，从设置菜单进入（页签「设置」保持选中）。各模块文件登记 render（模块渲染函数）与 sections（页内分区）。 */
/* 导航宽三档：窄 = 图标 + 两字（点餐分类）· 中 = 桌区、时段、菜品、会员 · 宽 = 多字菜单（报表、设置） */
var NAV = { sm: '104px', md: '128px', lg: '160px' };
var MODULES = {
  tables:   { name: '桌台', layout: 'W1', nav: NAV.md },
  order:    { name: '点餐', layout: 'W1', nav: NAV.sm },
  checkout: { name: '结账', layout: 'W2', detail: '440px', detailSide: 'left' },
  orders:   { name: '订单', layout: 'W1', nav: NAV.md },
  menu:     { name: '菜品', layout: 'W1', nav: NAV.md, home: 'settings' },
  members:  { name: '会员', layout: 'W1', nav: NAV.md, home: 'settings' },
  reports:  { name: '报表', layout: 'W4', nav: NAV.lg },
  shift:    { name: '班次', layout: 'W3', home: 'settings' },
  settings: { name: '设置', layout: 'W4', nav: NAV.lg }
};
/* 网格：返回 [grid-template-areas, grid-template-columns, grid-template-rows] */
function gridNoDetail(w) {
  if (S.nav === 'top') return ['"nav" "content"', 'minmax(0,1fr)', 'auto minmax(0,1fr)'];
  return S.nav === 'right' ? ['"content nav"', 'minmax(0,1fr) ' + w, 'minmax(0,1fr)'] : ['"nav content"', w + ' minmax(0,1fr)', 'minmax(0,1fr)'];
}
function grid(hasNav, N, D, det) {
  var nav = S.nav;
  if (!hasNav) return det === 'right' ? ['"content detail"', '1fr ' + D, 'minmax(0,1fr)'] : ['"detail content"', D + ' 1fr', 'minmax(0,1fr)'];
  if (nav === 'top') return det === 'right' ? ['"nav detail" "content detail"', '1fr ' + D, 'auto minmax(0,1fr)'] : ['"detail nav" "detail content"', D + ' 1fr', 'auto minmax(0,1fr)'];
  var order = { 'left-right': ['nav', 'content', 'detail'], 'right-left': ['detail', 'content', 'nav'], 'left-left': ['nav', 'detail', 'content'], 'right-right': ['content', 'nav', 'detail'] }[nav + '-' + det];
  var w = { nav: N, content: '1fr', detail: D };
  return ['"' + order.join(' ') + '"', order.map(function (a) { return w[a]; }).join(' '), 'minmax(0,1fr)'];
}
function layoutOf(wb) {
  var m = MODULES[wb];
  if (m.layout === 'W4') return gridNoDetail(m.nav);
  return grid(m.layout === 'W1', m.nav || NAV.sm, m.detail || '360px', m.detailSide || S.detail);
}
function sysbar() {
  var tabs = Object.keys(MODULES).filter(function (k) { return !MODULES[k].home; }).map(function (k) { return [k, MODULES[k].name]; }), on = MODULES[S.wb].home || S.wb;
  var entry = function (act, icon, label, extra) { return '<span class="dn-entry dn-entry--icon ent-btn" data-act="' + act + '" role="button" title="' + label + '" aria-label="' + label + '">' + icon + (extra || '') + '</span>'; };
  return '<header class="dn-sysbar"><span class="st">虹选小馆 · 南山店<small>李敏 · 早班 09:00 起</small></span>' +
    '<span class="dn-wbtabs">' + tabs.map(function (t) { return '<span class="' + (on === t[0] ? 'is-on' : '') + '" data-k="wb" data-v="' + t[0] + '">' + t[1] + '</span>'; }).join('') + '</span>' +
    entry('sheet:print', stIcon('device', 'no_paper'), '打印 · 缺纸') +
    entry('sheet:net', stIcon('device', S.banner === 'on' ? 'offline' : 'online'), S.banner === 'on' ? '网络 · 离线' : '网络 · 在线') +
    entry('sheet:takeout', ico('bike'), '外卖 · 2 单待接', '<i class="sh-badge">2</i>') +
    entry('sheet:member', ico('id-card'), '会员') +
    entry('sheet:set', ico('sliders-horizontal'), '显示') + '<span class="tm num">12:30</span></header>';
}

