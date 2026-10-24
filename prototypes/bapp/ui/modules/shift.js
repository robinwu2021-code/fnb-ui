/* 模块 · 班次：当班数据、钱箱实盘、开班、确认交班。 */
function shiftWB() {
  var stats = [['营业额', '¥8,642'], ['单数', '46'], ['客单价', '¥187.9']], pays = [['现金', '¥1,280', 'yellow'], ['微信', '¥4,102', 'green'], ['支付宝', '¥2,340', 'blue'], ['银行卡', '¥620', 'indigo'], ['会员储值', '¥300', 'purple']];
  var main = panel('<div class="row row--between"><b class="h-title">当班数据</b><span class="sh-help">早班 09:00 – 12:30 · 李敏</span></div>' +
    '<div class="sh-stat sh-stat--sunk">' + stats.map(function (s) { return '<div><div class="n">' + s[1] + '</div><div class="l">' + s[0] + '</div></div>'; }).join('') + '</div>',
    '<div class="dn-block"><div class="dn-sec"><span>按支付方式</span></div><div class="sh-cells sh-cells--sunk">' + pays.map(function (p) { return '<div class="sh-cell"><span class="k mlabel"><i class="dn-mark k-' + p[2] + '"></i>' + p[0] + '</span><span class="v num">' + p[1] + '</span></div>'; }).join('') + '</div>' +
    '</div><div class="dn-block"><div class="dn-sec"><span>异常操作</span></div><div class="row">' + stx('item', 'voided', '退菜 2 笔', 'chip') + stx('item', 'comp', '赠送 1 笔', 'chip') + stx('item', 'repriced', '折扣 3 笔', 'chip') + '<span class="sh-chip">开钱箱 9 次</span></div></div>');
  var det = ledger('<b class="h-title">钱箱实盘</b><span class="sh-help">按面额清点，张数输入后自动合计</span>', '<div class="dn-sec"><span>面额 · 张数</span></div><div class="stack">' +
    ['100 元', '50 元', '20 元', '10 元', '5 元', '1 元'].map(function (d, i) { return '<div class="dn-count"><span class="sh-help">' + d + '</span><div class="sh-input sh-input--num">' + [12, 3, 5, 4, 2, 10][i] + ' 张</div></div>'; }).join('') + '</div>',
    dueFoot('差异', '−¥10.00', '应有 ¥1,780.00 · 实盘 ¥1,770.00', '<div class="dn-acts">' + btn('打印报表', 'sh-btn--muted') + btn('确认交班', 'sh-btn--block', 'ov:confirm-shift') + '</div>', 'is-short'));
  return main + det;
}

OVL['confirm-shift'] = function (t) {
    return '<div class="sh-mask"><div class="sh-dialog"><span class="h">确认交班？</span><span class="d">钱箱实盘比应有现金少 ¥10.00，交班后差异会记在你名下。</span><div class="acts">' + btn('再核对', 'sh-btn--muted', 'ov:none') + btn('确认交班', 'sh-btn--danger-solid', 'toast:已交班，报表已打印') + '</div></div></div>';
};
OVL['s1'] = function (t) {
    return '<div class="sh-mask"><div class="sh-dialog full"><span class="h">开班</span><div class="sh-cells sh-cells--sunk"><div class="sh-cell"><span class="k">收银台</span><span class="v go">1 号收银台</span></div><div class="sh-cell"><span class="k">收银员</span><span class="v">李敏</span></div></div>' +
    '<span class="sh-gt"><span>备用金</span></span><div class="sh-input sh-input--num">¥ 500.00</div><span class="sh-gt"><span>设备自检</span></span><div class="sh-cells sh-cells--sunk"><div class="sh-cell"><span>前台打印机</span>' + stx('device', 'online', '正常') + '</div><div class="sh-cell"><span>热菜档口打印机</span>' + stx('device', 'no_paper') + '</div><div class="sh-cell"><span>网络</span>' + stx('device', 'online', '正常') + '</div><div class="sh-cell"><span>钱箱</span>' + stx('device', 'online', '正常') + '</div></div>' +
    '<div class="sh-note sh-note--warn">热菜档口缺纸。可以开班，热菜单会排队等打印。</div><div class="acts">' + btn('开班', 'sh-btn--lg', 'toast:已开班，备用金 ¥500.00') + '</div></div></div>';
};
MODULES.shift.render = shiftWB;
MODULES.shift.sections = ['当班数据', '钱箱实盘'];
