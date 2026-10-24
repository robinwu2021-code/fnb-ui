/* 应用：页首开关、侧滑面板、共用浮层（授权、侧滑）、渲染、交互分发、启动。 */
/* 浮层：各模块把自己的浮层注册到 OVL[键]（键即 S.ov），参数 t = totals() */
function overlay() { var f = OVL[S.ov]; return f ? f(totals()) : ''; }
function sheetBody() {
  if (S.sheet === 'takeout') return ['美团 #88 · 4 件 · ¥96.00', '饿了么 #31 · 2 件 · ¥42.00'].map(function (x) { return '<div class="sh-cells sh-cells--sunk"><div class="sh-cell"><span>' + x + '</span>' + stx('delivery', 'new') + '</div><div class="sh-cell"><span class="sh-help">12:18 下单 · 预计 12:50 送达</span><span class="row">' + btn('拒单', 'sh-btn--muted sh-btn--sm', 'toast:已拒单') + btn('接单', 'sh-btn--soft sh-btn--sm', 'toast:已接单，已送厨') + '</span></div></div>'; }).join('');
  if (S.sheet === 'member') return (S.member ? '<div class="dn-mcard"><div class="t">' + stIcon('member', 'linked') + '<b>' + MEMBER.name + '</b><span class="sh-chip">' + MEMBER.layer + '</span>' + MEMBER.tags.map(function (x) { return '<span class="sh-chip">' + x + '</span>'; }).join('') + '</div><div class="kv"><span><small>本店余额</small><b>¥' + MEMBER.bal.toFixed(2) + '</b></span><span><small>可用券</small><b>' + MEMBER.coupons + ' 张</b></span><span><small>到店</small><b>14 次</b></span></div></div>' +
      '<div class="row">' + btn('充值', 'sh-btn--soft', 'ov:recharge') + btn('会员详情', 'sh-btn--muted', 'gomember') + btn('本单解绑', 'sh-btn--muted', 'munbind') + '</div>'
    : '<div class="sh-empty">' + ico('scan-line') + '<b>请顾客出示会员码</b><span>扫码枪扫到会员码自动识别；或输入完整手机号</span></div>' + '<div class="sh-input sh-input--num">' + (S.mph || '<span class="sh-help">输入 11 位手机号</span>') + '</div>' +
      '<div class="dn-keys">' + ['1', '2', '3', '4', '5', '6', '7', '8', '9', '清空', '0', '⌫'].map(function (k) { return '<span class="' + (k.length > 1 || k === '⌫' ? 'fn' : '') + '" data-act="mph:' + (k === '⌫' ? 'back' : k === '清空' ? 'clr' : k) + '"' + (k === '⌫' ? ' role="button" aria-label="退格">' + ico('delete') : '>' + k) + '</span>'; }).join('') + '</div>' +
      ((S.mph || '').length === 11 ? (/6621$/.test(S.mph) ? '<div class="row">' + btn('识别为 王女士 · 熟客', 'sh-btn--block', 'scan:member') + '</div>' : '<div class="sh-note">没有找到这个手机号的会员。</div><div class="row">' + btn('新建会员', 'sh-btn--soft', 'ov:mnew') + '</div>') : '')) + '<span class="sh-help">识别会员不等于扣款授权；手机号只显示后四位</span>';
  if (S.sheet === 'print') return '<div class="sh-cells sh-cells--sunk"><div class="sh-cell"><span>前台</span>' + stx('device', 'online', '正常') + '</div><div class="sh-cell"><span>热菜档口</span>' + stx('device', 'no_paper', '缺纸 · 2 单待打') + '</div><div class="sh-cell"><span>凉菜档口</span>' + stx('device', 'online', '正常') + '</div><div class="sh-cell"><span>吧台</span>' + stx('device', 'online', '正常') + '</div></div><div class="row">' + btn('补打', 'sh-btn--muted', 'toast:已补打') + btn('开钱箱', 'sh-btn--muted', 'toast:钱箱已打开') + btn('重打失败单', 'sh-btn--soft', 'toast:已重打 2 张厨房单') + '</div>';
  if (S.sheet === 'net') return '<div class="sh-cells sh-cells--sunk"><div class="sh-cell"><span>门店服务器</span>' + (S.banner === 'on' ? stx('device', 'offline', '已断开') : stx('device', 'online', '已连接')) + '</div><div class="sh-cell"><span>支付网关</span>' + (S.banner === 'on' ? stx('device', 'offline', '不可用') : stx('device', 'online', '已连接')) + '</div><div class="sh-cell"><span>待同步</span><span class="v num">' + (S.banner === 'on' ? 3 : 0) + ' 单</span></div></div><div class="sh-note">离线时仍可点菜、送厨（进入待同步队列）、收现金和刷卡；扫码支付不可用。</div>' +
    '<div class="sh-cells sh-cells--sunk"><div class="sh-cell"><span>模拟离线</span><span class="sh-switch' + (S.banner === 'on' ? ' is-on' : '') + '" data-k="banner" data-v="' + (S.banner === 'on' ? 'off' : 'on') + '" role="switch" aria-label="模拟离线"></span></div></div>';
  return styleControls() + btn('打开完整设置', 'sh-btn--soft', 'goset');
}

var FONTS = [['inter', 'Inter', '默认'], ['roboto', 'Roboto', '最紧凑'], ['noto', 'Noto Sans', '中英最一致'], ['manrope', 'Manrope', '几何'], ['plex', 'IBM Plex Sans', '工具感']];
var FRAMES = [['f2', '方', '2'], ['f6', '小', '6'], ['f10', '中', '10'], ['f14', '大', '14'], ['f20', '特大', '20']];
var BTNS = [['b2', '方', '2'], ['b4', '小圆', '4'], ['b8', '圆角', '8'], ['b12', '大圆', '12'], ['pill', '胶囊', '全圆']];
/* 形状方案：框架 × 按钮的推荐组合，参照见 docs 第二节 */
var SHAPES = [['f6-b4', '利落', 'Fluent / Ant'], ['f14-b8', '标准', 'Polaris / Atlassian'], ['f20-pill', '柔和', 'Material 3 / B 端'], ['f10-pill', '混搭', '方框 + 胶囊钮'], ['f2-b2', '方正', '票据 / 终端']];
var CARDS = [['v', '上下'], ['h', '左右']];
var DENS = [['compact', '紧凑', '36 · 44 · 52'], ['standard', '标准', '40 · 48 · 56']];
var SKINS = [['mono', '墨黑', '#18181B'], ['brand', '虹选红', '#E1251B'], ['blue', '经典蓝', '#2C69FF'], ['fresh', '微信绿', '#00B578'], ['teal', '青碧', '#008484'], ['amber', '琥珀', '#A46911'], ['violet', '紫', '#7A4CE0']];
var NAMES = [['bi', '中英双语'], ['one', '单语']];
var LANGS = [['zh', '中文'], ['en', 'English']];
var PICS = [['img', '有图', '没图的菜写大字'], ['off', '无图', '纯文字卡']];
var OVS = [['none', '无'], ['x1', '规格'], ['x2', '授权'], ['x3', '选桌'], ['menu', '更多菜单'], ['sheet', '侧滑面板'], ['p3', '收款结果'], ['p4', '支付失败'], ['note', '整单备注'], ['hold', '挂单'], ['share', '拼桌'], ['reprice', '调价'], ['coupons', '选券'], ['pvoucher', '平台券'], ['manual', '整单折扣'], ['mnew', '新建会员'], ['recharge', '充值'], ['s1', '开班']];
function row(label, k, list, extra) {
  return '<div class="row"><span>' + label + '</span>' + list.map(function (o) {
    var on = k === 'layout' ? S.nav + '-' + S.detail === o[0] : k === 'shape' ? S.frame + '-' + S.btn === o[0] : S[k] === o[0];
    return '<button data-k="' + k + '" data-v="' + o[0] + '" aria-pressed="' + on + '">' + (o[3] ? '<i style="background:' + o[3] + '"></i>' : '') + o[1] + (o[2] ? '<small>' + o[2] + '</small>' : '') + '</button>';
  }).join('') + (extra || '') + '</div>';
}
function switches() {
  return row('工作台', 'wb', [['tables', '桌台'], ['order', '点餐'], ['checkout', '结账'], ['orders', '订单'], ['menu', '菜品'], ['members', '会员'], ['reports', '报表'], ['shift', '班次'], ['settings', '设置']]) +
    row('浮层', 'ov', OVS) +
    row('语言', 'lang', LANGS) + row('色彩', 'colors', [['v2', '色彩体系 v2', 'pos-colors.css'], ['v1', '旧配色', '对比用']]) + row('色系', 'tone', [['quiet', '黑白灰', '默认：只留琥珀、红'], ['color', '多色', '9 组状态色 + 11 分类色']]) +
    row('字体', 'font', FONTS, '<small class="pd-note">中文统一 Noto Sans SC · 全部 Google Fonts</small>') +
    row('形状方案', 'shape', SHAPES) + row('框架圆角', 'frame', FRAMES) + row('按钮形状', 'btn', BTNS) + row('密度', 'density', DENS) +
    row('皮肤', 'skin', SKINS.map(function (s) { return [s[0], s[1], '', s[2]]; })) +
    row('布局', 'layout', LAYOUTS.map(function (l) { return [l[1] + '-' + l[2], l[0], l[3]]; })) +
    row('菜品图', 'pic', PICS) + row('卡片排布', 'card', CARDS) + row('菜名', 'names', NAMES) + '<div class="row"><span>模拟扫码</span>' + [['member', '会员码'], ['coupon', '券码'], ['pvoucher', '团购券码'], ['pay', '付款码'], ['pickup', '取餐码']].map(function (x) { return '<button data-scan="' + x[0] + '">' + x[1] + '</button>'; }).join('') + '<small class="pd-note">扫码枪扫到任何码自动识别，不用先点按钮</small></div>' + row('网络', 'banner', [['off', '在线'], ['on', '离线（横幅）']]) + row('数据', 'load', [['off', '已加载'], ['on', '加载中（骨架）']]);
}

var stage = document.getElementById('stage'), dev = document.getElementById('dev'), toastTimer;
function render() {
  ['skin', 'font', 'frame', 'btn', 'density'].forEach(function (k) { root.setAttribute('data-' + k, S[k]); });
  root.setAttribute('data-tone', S.tone);
  if (S.colors === 'v2') root.setAttribute('data-colors', 'v2'); else root.removeAttribute('data-colors');
  root.setAttribute('lang', S.lang === 'en' ? 'en' : 'zh-CN');
  var body = MODULES[S.wb].render();
  var g = layoutOf(S.wb);
  dev.innerHTML = sysbar() + '<div class="ui-banner">' + (S.banner === 'on' ? '<div class="dn-banner">' + stIcon('device', 'offline') + '网络已断开，3 单待同步；扫码支付暂不可用<b class="sh-hit" data-act="sheet:net">查看</b></div>' : '') + '</div>' +
    '<div class="ui-body" style="grid-template-areas:' + g[0].replace(/"/g, "'") + ';grid-template-columns:' + g[1] + ';grid-template-rows:' + g[2] + '">' + body + '</div>' +
    overlay() + (S.toast ? '<div class="ui-toast"><span class="sh-toast">' + S.toast + '</span></div>' : '');
  document.getElementById('sw').innerHTML = switches();
  if (S.lang === 'en') { translate(dev); translate(document.getElementById('sw')); }
  translateStatic(S.lang === 'en');
  if (window.lucide) lucide.createIcons({ attrs: { 'stroke-width': 1.75 } });
  /* 导航很长时（设置菜单）把当前项滚进可见区 */
  var nav = dev.querySelector('.ui-nav'), cur = nav && nav.querySelector('.is-on');
  if (cur && (cur.offsetTop < nav.scrollTop || cur.offsetTop + cur.offsetHeight > nav.scrollTop + nav.clientHeight)) nav.scrollTop = cur.offsetTop - nav.clientHeight / 3;
  try { var o = {}; STYLE.forEach(function (k) { o[k] = S[k]; }); localStorage.setItem(KEY, JSON.stringify(o)); } catch (e) {}
}
function toast(msg) { S.toast = msg; clearTimeout(toastTimer); toastTimer = setTimeout(function () { S.toast = ''; render(); }, 2600); }
function fit() { var s = Math.min(1, stage.clientWidth / 1280); dev.style.transform = 'scale(' + s + ')'; stage.style.height = (800 * s + 20) + 'px'; }

/* 交互：各模块把自己的动作注册到 ACT[动作名]（data-act="名:值"），参数 v 是值、k 是名 */
function act(a) {
  var i = a.indexOf(':'), k = i < 0 ? a : a.slice(0, i), v = i < 0 ? '' : a.slice(i + 1);
  var f = ACT[k]; if (f) f(v, k);
}
document.addEventListener('click', function (e) {
  var scn = e.target.closest('[data-scan]'); if (scn) { act('scan:' + scn.dataset.scan); render(); return; }
  var sw = e.target.closest('[data-k]');
  if (sw) {
    var k = sw.dataset.k, v = sw.dataset.v;
    if (k === 'layout') { var p = v.split('-'); S.nav = p[0]; S.detail = p[1]; } else if (k === 'shape') { var sp = v.split('-'); S.frame = sp[0]; S.btn = sp[1]; } else S[k] = v;
    if (k === 'wb') { S.ov = 'none'; S.batch = false; S.sel = []; }
    if (k === 'ov') S.noteDraft = null;
    render(); return;
  }
  var a = e.target.closest('[data-act]');
  if (!a || !dev.contains(a)) return;
  if (a.dataset.act === 'ov:none' && e.target.closest('[data-stop]')) return;
  act(a.dataset.act); render();
});
new ResizeObserver(fit).observe(stage);
render(); fit();
OVL['x2'] = function (t) {
    return '<div class="sh-mask"><div class="sh-dialog"><span class="h">主管授权 · 退菜</span><span class="d">酸梅汤 ×1 · ¥12.00</span>' +
    '<div class="row"><span class="sh-option is-on">上错菜</span><span class="sh-option">客人退菜</span><span class="sh-option">菜品质量</span><span class="sh-option">等待太久</span></div>' +
    '<div class="dn-pins"><i class="on"></i><i class="on"></i><i></i><i></i></div><div class="dn-keys">' + ['1', '2', '3', '4', '5', '6', '7', '8', '9', '取消', '0', '⌫'].map(key).join('') + '</div>' +
    '<div class="acts">' + btn('取消', 'sh-btn--muted', 'ov:none') + btn('确认', '', 'void') + '</div><span class="sh-help">记录原因、操作人、授权人</span></div></div>';
};
OVL['sheet'] = function (t) {
    return '<div class="sh-mask sh-mask--side" data-act="ov:none"><div class="sh-sheet" data-stop="1"><div class="hd"><b>' + { takeout: '外卖', member: '会员', print: '打印', net: '网络', set: '设置' }[S.sheet] + '</b>' + btn(ico('x'), 'sh-btn--muted sh-btn--icon sh-btn--sm', 'ov:none', '关闭') + '</div>' +
    '<div class="sh-tabs">' + [['takeout', '外卖'], ['member', '会员'], ['print', '打印'], ['net', '网络'], ['set', '设置']].map(function (x) { return '<span class="' + (S.sheet === x[0] ? 'is-on' : '') + '" data-act="sheet:' + x[0] + '">' + x[1] + '</span>'; }).join('') + '</div>' + sheetBody() + '</div></div>';
};
ACT['ov'] = function (v, k) {
    S.ov = v; if (v !== 'note') S.noteDraft = null; return;
};
ACT['sheet'] = function (v, k) {
    S.ov = 'sheet'; S.sheet = v; return;
};
ACT['toast'] = function (v, k) {
    S.ov = 'none'; toast(v); return;
};
ACT['scan'] = function (v, k) {
    if (v === 'member') { S.member = true; S.mph = ''; toast('已识别会员 ' + MEMBER.phone + ' · ' + MEMBER.layer); }
    else if (v === 'coupon') { S.cpScan = true; toast('已识别券：饮品体验券，结账时可用'); }
    else if (v === 'pvoucher') { if (S.wb === 'order' || S.wb === 'checkout') { S.pvLast = 'mt100'; S.pvN = 1; S.ov = 'pvoucher'; } else toast('团购券在点餐或结账时验券'); }
    else if (v === 'pay') { if (S.wb === 'checkout') S.ov = 'p3'; else toast('付款码只在结账时使用'); }
    else if (v === 'pickup') { S.wb = 'orders'; S.och = '外卖'; S.ost = 'all'; S.osel = '#1033'; toast('已打开取餐单 饿了么 #36'); }
    return;
};
ACT['goorder'] = function (v, k) {
    S.wb = 'order'; return;
};
