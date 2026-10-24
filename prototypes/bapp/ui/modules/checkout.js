/* 模块 · 结账：左「账单与优惠」右「收款」、完成页、支付失败、选券、整单折扣。 */
var PAYS = [['wx', '微信', '扫码', '#07C160', '微'], ['ali', '支付宝', '扫码', '#1677FF', '支'], ['cash', '现金', '找零', '#A46911', '¥'], ['card', '银行卡', '刷卡', '#5552BB', '卡'], ['sv', '储值', '会员余额', '#8747AA', '储'], ['mix', '组合支付', '多种方式', '#5E5E5E', '组']];
var PAYNAME = { wx: '微信', ali: '支付宝', cash: '现金', card: '银行卡', sv: '储值' };
function svWhy(t) { return !S.member ? '先识别会员' : MEMBER.bal < t.rest ? '余额不足，差 ' + money(t.rest - MEMBER.bal) : (S.paid || []).length ? '储值不能和其他方式组合' : ''; }
function payRow(t) {
  return '<div class="dn-paylist dn-paylist--h">' + PAYS.map(function (p) {
    var why = S.banner === 'on' && (p[0] === 'wx' || p[0] === 'ali') ? '离线不可用' : p[0] === 'sv' ? svWhy(t) : '', on = S.pay === p[0];
    return '<span class="dn-pay dn-pay--row' + (on ? ' is-on' : '') + (why ? ' is-off' : '') + '" style="--pay:' + p[3] + '"' + (why ? ' aria-disabled="true"' : ' data-act="pay:' + p[0] + '"') + '><i>' + p[4] + '</i><span class="nm">' + p[1] + '<small>' + (why || (p[0] === 'sv' && S.member ? '余额 ¥' + MEMBER.bal : p[2])) + '</small></span></span>';
  }).join('') + '</div>';
}
/* 现金：实收大字 · 找零或还差 · 快捷金额 | 键盘（确认键在右下，跨两行） */
function cashBlock(due, okAct, okLabel) {
  var short = S.got < due, quick = [due, Math.ceil(due / 10) * 10, Math.ceil(due / 100) * 100, 500].filter(function (v, i, a) { return v >= due && a.indexOf(v) === i; });
  var ok = short ? '<span class="ok is-off" aria-disabled="true">还差<b>' + money(due - S.got) + '</b></span>' : '<span class="ok" data-act="' + (okAct || 'cashok') + '" role="button">' + (okLabel || '确认收款') + '<b>' + money(due) + '</b></span>';
  return '<div class="ui-split"><div class="stack"><div class="sh-input sh-input--num sh-input--amount"><span class="sh-help">实收</span><span class="dn-amount"><b>' + money(S.got) + '</b></span></div>' +
    (short ? '<div class="dn-amount dn-amount--sm dn-amount--short"><span class="sh-help">还差</span><b>' + money(due - S.got) + '</b></div>' : '<div class="dn-amount dn-amount--sm"><span class="sh-help">找零</span><b>' + money(S.got - due) + '</b></div>') +
    '<span class="sh-gt"><span>快捷金额</span></span><div class="row">' + quick.map(function (v) { return '<span class="sh-filter' + (S.got === v ? ' is-on' : '') + '" data-act="got:' + v + '">¥' + v + '</span>'; }).join('') + '</div></div>' +
    '<div class="dn-keys dn-keys--4">' + ['7', '8', '9', '⌫', '4', '5', '6', '清空', '1', '2', '3'].map(function (k) { return key(k, true); }).join('') + ok + ['00', '0', '.'].map(function (k) { return key(k, true); }).join('') + '</div></div>';
}
/* 设备收款（扫码、刷卡）：等待 + 设备状态 */
function waitBlock(title, hint, dev, devLabel) {
  return '<div class="sh-empty sh-empty--fill"><i class="sh-spin"></i><b>' + title + '</b><span>' + hint + '</span>' + stx('device', dev, devLabel) + '</div>';
}
/* 优惠行：✓ 已用 / ○ 可选 / 置灰写原因；金额靠右 */
function promoRow(on, title, desc, amt, act, why) {
  return '<div class="dn-promo' + (on ? ' is-on' : '') + (why ? ' is-off' : '') + '"' + (act && !why ? ' data-act="' + act + '" role="button"' : '') + '>' + ico(on ? 'circle-check' : why ? 'circle-slash' : 'circle') +
    '<span class="tx"><b>' + title + '</b><small>' + (why || desc) + '</small></span><span class="am">' + (on && amt ? '−' + money(amt) : act && !why ? '›' : '') + '</span></div>';
}
function billSide(t) {
  var mem = S.member ? '<div class="dn-mcard"><div class="t">' + stIcon('member', 'linked') + '<b>' + MEMBER.name + '</b><span class="sh-chip">' + MEMBER.layer + '</span>' + MEMBER.tags.map(function (x) { return '<span class="sh-chip">' + x + '</span>'; }).join('') + '<a class="sh-link sh-hit" data-act="sheet:member">换会员</a></div>' +
    '<div class="kv"><span><small>本店余额</small><b>¥' + MEMBER.bal.toFixed(2) + '</b></span><span><small>可用券</small><b>' + MEMBER.coupons + ' 张</b></span><span><small>积分</small><b>2,310</b></span></div></div>'
    : '<div class="row">' + btn(ico('scan-line') + '扫会员码', 'sh-btn--muted sh-btn--sm', 'scan:member') + btn('输手机号', 'sh-btn--muted sh-btn--sm', 'sheet:member') + btn('开会员', 'sh-btn--muted sh-btn--sm', 'mnew') + '</div><span class="sh-help">识别会员不等于扣款授权；没有会员也可以直接结账</span>';
  var c = t.coupon, act = t.act > 0;
  var pvs = '<span data-notr="1">' + (S.pv || []).map(function (x) { return pvName(pvOf(x.id)) + (x.n > 1 ? ' ×' + x.n : ''); }).join(S.lang === 'en' ? ', ' : '、') + '</span>', pvNo = '已用平台券，不与店内优惠同享';
  var promos = promoRow(!!t.pv, t.pv ? '平台券 · ' + pvCount() + ' 张' : '平台券 · 团购', t.pv ? pvs : '美团·点评、抖音等团购券，扫码验券', t.pv, 'ov:pvoucher', S.banner === 'on' && !t.pv ? '离线不能验券，联网后再验' : '') +
    promoRow(act, '非高峰 8 折 · 活动', '自动匹配 · 最多减 ¥20 · 点一下改用券', t.act, 'pact', t.pv ? pvNo : '') +
    (S.member ? promoRow(false, '熟客专享 · 饮品买二赠一', '', 0, '', '本单饮品不足 2 杯') : '') +
    promoRow(!!c, c ? c.kind + ' · ' + c.name : '优惠券', c ? '已选 · 点一下换券' : (S.member || S.cpScan ? '可用 2 张 · 也可以扫券码' : '识别会员后可选，也可以扫券码'), t.cp, 'ov:coupons', t.pv ? pvNo : act ? '本单已参加活动，不能用券' : (!S.member && !S.cpScan ? '' : '')) +
    promoRow(false, '会员价', '', 0, '', '会员价规则待定，暂不计算') +
    promoRow(false, '积分', '', 0, '', '平台积分，线下支付不抵扣') +
    promoRow(t.manual > 0, '整单折扣 / 免单', t.manual ? (S.mwhy || '') : '必须选原因 · 超额度需授权', t.manual, 'ov:manual', t.pv ? pvNo : act || c ? '已用活动或券，不能再人工打折' : '') +
    promoRow(t.round > 0, '抹零', t.round ? '抹掉 ' + money(t.round) : '', t.round, '', t.pv ? '用平台券后不抹零' : c ? '用券后不能抹零' : t.round ? '' : '没有零头');
  return ledger('<div class="row row--between"><b class="h-title">' + S.table + ' 结账</b><span class="row"><span class="sh-help">#1024 · 4 人</span>' + btn('改单', 'sh-btn--muted sh-btn--sm', 'goorder') + '</span></div>',
    '<div class="dn-block"><div class="dn-sec"><span>① 账单 · ' + LINES.length + ' 道 · ' + money(t.sub) + '</span><a class="sh-link sh-hit" data-act="lines:' + (S.lines ? 'off' : 'on') + '">' + (S.lines ? '收起' : '展开') + '</a></div>' + (S.lines ? '<div class="dn-olist">' + LINES.map(function (l, i) { return oline(l, i, false); }).join('') + '</div>' : '') + '</div>' +
    '<div class="dn-block"><div class="dn-sec"><span>② 会员</span></div>' + mem + '</div>' +
    '<div class="dn-block"><div class="dn-sec"><span>③ 费用与优惠</span><span>平台券 → 调价 → 活动 → 券 → 储值 → 抹零</span></div><div class="dn-promos"><div class="dn-promo is-on">' + ico('receipt') + '<span class="tx"><b>服务费 5%</b><small>堂食收取</small></span><span class="am">+' + money(t.svc) + '</span></div>' + promos + '</div></div>',
    dueFoot('应付', money(t.due), dishCount() + ' 道 · 已优惠 ' + money(t.mem + t.round)));
}
function checkoutWB() {
  var t = totals(), body, foot = '', paid = S.paid || [];
  if (!t.rest && paid.length) body = '<div class="sh-empty sh-empty--fill">' + ico('circle-check') + '<b>已收齐</b><span>点「完成」</span></div>';
  else if (!t.due && t.pv) body = '<div class="sh-empty sh-empty--fill">' + ico('ticket-check') + '<b>平台券已抵扣全部金额</b><span data-notr="1">' + (S.lang === 'en' ? (t.pvOver ? 'Vouchers exceed the bill by ' + money(t.pvOver) + ' (no change or refund). ' : '') + 'Tap Done to redeem with the platform' : (t.pvOver ? '券面额超出 ' + money(t.pvOver) + '，不找零、不退回；' : '') + '点「完成」向平台核销') + '</span></div>';
  else if (S.pay === 'cash') body = cashBlock(t.rest);
  else if (S.pay === 'mix') {
    body = '<div class="dn-block"><div class="dn-sec"><span>已付 · ' + paid.length + ' 笔</span><span>待收 ' + money(t.rest) + '</span></div>' + (paid.length ? '<div class="sh-cells">' + paid.map(function (p, i) { return '<div class="sh-cell"><span class="k mlabel">' + stIcon('payment', 'paid') + PAYNAME[p[0]] + '</span><span class="row"><b class="num">' + money(p[1]) + '</b><a class="sh-link sh-hit" data-act="undo:' + i + '">撤销</a></span></div>'; }).join('') + '</div>' : '<div class="sh-note">先选这一笔的方式，再输金额。储值不能参与组合（ai-shop 首期规则）。</div>') + '</div>' +
      '<div class="dn-block"><div class="row row--between"><span class="sh-seg">' + ['cash', 'wx', 'ali', 'card'].map(function (m) { return '<span class="' + (S.mixm === m ? 'is-on' : '') + '" data-act="mixm:' + m + '">' + PAYNAME[m] + '</span>'; }).join('') + '</span><span class="sh-help">这一笔：' + PAYNAME[S.mixm] + '</span></div></div>' +
      cashBlock(Math.min(t.rest, S.got || t.rest), 'mixadd', '收这一笔');
  }
  else if (S.pay === 'card') { body = waitBlock('请在刷卡机上操作', '刷卡、插卡或挥卡；完成后自动到账', 'online', '刷卡机已连接'); foot = btn('取消收款', 'sh-btn--muted', 'pay:cash') + btn('手动确认到账', 'sh-btn--soft sh-btn--block', 'payok'); }
  else if (S.pay === 'sv') {
    body = S.svWait ? waitBlock('请顾客在手机上确认', '顾客确认订单号、金额、门店后自动扣款；识别会员不等于扣款授权', 'online', '已推送到 ' + MEMBER.phone)
      : '<div class="sh-cells"><div class="sh-cell"><span class="k">会员</span><span class="v">' + MEMBER.name + ' · ' + MEMBER.phone + '</span></div><div class="sh-cell"><span class="k">本店余额</span><span class="v num">¥' + MEMBER.bal.toFixed(2) + '</span></div><div class="sh-cell"><span class="k">本次扣款</span><span class="v hot num">' + money(t.rest) + '</span></div><div class="sh-cell"><span class="k">扣后余额</span><span class="v num">' + money(MEMBER.bal - t.rest) + '</span></div></div>' +
        '<div class="sh-note">储值必须全额支付，不能和现金等方式组合；首期不支持自动退回，反结账时登记待办线下处理。</div>';
    foot = S.svWait ? btn('取消扣款', 'sh-btn--muted', 'svcancel') + btn('顾客已确认（模拟）', 'sh-btn--soft sh-btn--block', 'svok') : btn('取消', 'sh-btn--muted', 'pay:cash') + btn('发起扣款 ' + money(t.rest), 'sh-btn--block', 'svgo');
  }
  else { body = waitBlock('请顾客出示付款码', '用扫码枪扫顾客的付款码，或让顾客扫桌上的收款码；也可以点页首「模拟扫码 › 付款码」', 'online', '扫码枪已连接'); foot = btn('取消收款', 'sh-btn--muted', 'pay:cash') + btn('手动确认到账', 'sh-btn--soft sh-btn--block', 'payok'); }
  if (!t.rest && (paid.length || t.pv)) foot = btn('完成', 'sh-btn--block', 'ov:p3');
  var steps = '<div class="dn-paysum dn-paysum--5"><div><span>应收</span><b>' + money(t.sub + t.svc) + '</b></div><div><span>优惠</span><b>−' + money(t.mem + t.round) + '</b></div><div class="due"><span>应付</span><b>' + money(t.due) + '</b></div><div><span>已收</span><b>' + money(t.paid) + '</b></div><div class="' + (t.rest ? '' : 'is-done') + '"><span>待收</span><b>' + money(t.rest) + '</b></div></div>';
  var main = panel(steps + '<span class="sh-gt"><span>④ 收款方式</span></span>' + payRow(t), body, foot ? '<div class="dn-acts">' + foot + '</div>' : '', 'sh-panel--fill');
  return billSide(t) + main;
}


OVL['p4'] = function (t) {
    var how = { wx: '微信', ali: '支付宝', card: '银行卡', vip: '会员储值' }[S.pay] || '微信';
    return '<div class="sh-mask"><div class="sh-dialog"><div class="dn-result dn-result--bad"><span class="ic"></span><span class="t">支付失败</span><span class="sh-help">' + how + ' ' + money(t.due) + ' · 顾客零钱不足</span></div>' +
      '<div class="sh-note">这笔钱没有扣成功。可以请顾客换一张卡或换一种方式，也可以重新发起。</div><div class="acts">' + btn('换一种方式', 'sh-btn--muted', 'payother') + btn('重新发起', '', 'toast:已重新发起，请顾客再次付款') + '</div></div></div>';
};
OVL['p3'] = function (t) {
    var pl = (S.paid || []).length ? S.paid : [[S.pay, t.due]];
    return '<div class="sh-mask"><div class="sh-dialog"><div class="dn-result"><span class="ic"></span><span class="t">⑤ 收款成功 ' + money(t.due) + '</span>' + (S.change ? '<span class="dn-amount dn-amount--sm"><span class="sh-help">找零</span><b>' + money(S.change) + '</b></span>' : '') + '</div>' +
      '<div class="sh-cells">' + pl.map(function (p) { return '<div class="sh-cell"><span class="k mlabel">' + stIcon('payment', 'paid') + (PAYNAME[p[0]] || '收款') + '</span><span class="v num">' + money(p[1]) + '</span></div>'; }).join('') +
      (t.mem ? '<div class="sh-cell"><span class="k">本单优惠</span><span class="v num">−' + money(t.mem + t.round) + '</span></div>' : '') + (S.member ? '<div class="sh-cell"><span class="k">储值余额</span><span class="v num">¥' + MEMBER.bal.toFixed(2) + '</span></div>' : '') + '</div>' +
      '<span class="sh-help">小票已自动打印 · 线下支付不累计平台积分 · 5 秒无操作回到桌台</span>' +
      '<div class="acts">' + btn('补打小票', 'sh-btn--muted', 'toast:正在打印小票') + btn('开发票', 'sh-btn--muted', 'toast:已生成开票二维码，请顾客扫码填写抬头') + btn('下一桌', '', 'paid') + '</div></div></div>';
};
OVL['coupons'] = function (t) {
    var t0 = totals();
    return '<div class="sh-mask sh-mask--side" data-act="ov:none"><div class="sh-sheet" data-stop="1"><div class="hd"><b>选择优惠券</b>' + btn(ico('x'), 'sh-btn--muted sh-btn--icon sh-btn--sm', 'ov:none', '关闭') + '</div>' +
      '<div class="sh-search">' + ico('scan-line') + '<span>扫券码或输入券码</span></div><span class="sh-help">一单最多 1 张折扣券 + 1 张金额券；到店出示的券不参与算价，在「核销券」里单独核销</span>' +
      COUPONS.map(function (c) { var why = cpWhy(c, t0.sub), on = S.cpn === c.id; return '<div class="dn-coupon' + (on ? ' is-on' : '') + (why ? ' is-off' : '') + '"' + (why ? '' : ' data-act="cpn:' + c.id + '"') + '><span class="v">' + (c.disc ? '8 折' : '¥' + c.amt) + '</span><span class="tx"><b>' + c.name + '</b><small>' + c.kind + ' · ' + (why || '有效期至 2026-12-31') + '</small></span>' + (on ? ico('circle-check') : '') + '</div>'; }).join('') +
      btn('不用券', 'sh-btn--muted', 'cpn:none') + '</div></div>';
};
/* 平台券 · 验券：选平台（或自动识别）→ 扫码 / 输券码 → 平台返回券信息 → 选张数加入本单；收款完成时才向平台核销，取消结账不核销 */
function platMark(k) { var p = plat(k); return '<i class="dn-plat" style="--pay:' + p[2] + ';--pay-fg:' + p[4] + '" aria-hidden="true" data-notr="1">' + p[3] + '</i>'; }
function pvCard(v, n, foot) {
  var why = pvWhy(v), en = S.lang === 'en';
  var d = en ? (v.items ? 'Set voucher' : 'Cash voucher') + ' · sold at ¥' + v.price + ' · ' + (why ? 'items not on this order' : (v.items ? 'covers ' + v.items.length + ' dishes' : 'no change') + ' · until ' + v.till)
    : v.kind + ' · 售价 ¥' + v.price + ' · ' + (why || (v.items ? '含 ' + v.items.join('、') : '不找零') + ' · 至 ' + v.till);
  return '<div class="dn-coupon' + (why ? ' is-off' : '') + '"><span class="v">¥' + v.face + '</span><span class="tx" data-notr="1"><b>' + platMark(v.plat) + pvName(v) + '</b><small>' + d + '</small><small class="num">' + (en ? 'Code ' + v.code + ' · ' + v.left + ' left' : '券码 ' + v.code + ' · 剩 ' + v.left + ' 次') + '</small></span>' + (foot || '') + '</div>';
}
OVL['pvoucher'] = function (t) {
    var t0 = totals(), last = S.pvLast, err = last && PVERR[last], v = last && pvOf(last), used = (S.pv || []).filter(function (x) { return x.id === last; })[0];
    var plats = '<div class="row">' + PLATS.map(function (p) { var off = p[5] !== '已授权'; return '<span class="sh-filter' + (off ? ' is-off' : '') + '"' + (off ? ' aria-disabled="true"' : '') + '>' + platMark(p[0]) + p[1] + '<em>' + p[5] + '</em></span>'; }).join('') + '</div>';
    var sim = '<span class="sh-gt"><span>模拟扫码</span></span><div class="row">' + [['mt100', '美团 · 代金券'], ['dy116', '抖音 · 套餐券'], ['used', '已用过的券'], ['ks', '快手券']].map(function (x) { return btn(x[1], 'sh-btn--muted sh-btn--sm', 'pvscan:' + x[0]); }).join('') + '</div>';
    var res = S.banner === 'on' ? '<div class="sh-note sh-note--warn">离线不能验券：平台券要先向平台确认没被用过，联网后再验。</div>' : err ? '<div class="sh-note sh-note--warn">' + platMark(err[0]) + '<b>' + err[1] + '</b> · ' + err[2] + '</div>'
      : v ? pvCard(v, 0, used ? stx('coupon', 'unused', S.lang === 'en' ? 'Added ' + used.n : '已加入 ' + used.n + ' 张', 'dot') : '') + (used ? '' : '<div class="row row--between"><span class="sh-help">本次使用</span>' + stepper(S.pvN || 1, 'pvn:-1', 'pvn:1') + '</div>' + btn('加入本单 · −¥' + v.face * (S.pvN || 1), 'sh-btn--block', 'pvadd:' + v.id))
      : '<div class="sh-empty">' + ico('scan-line') + '<b>请顾客出示团购券码</b><span>扫码枪扫到券码自动识别平台；也可以输入 12 位券码</span></div>';
    var list = (S.pv || []).length ? '<span class="sh-gt"><span data-notr="1">' + (S.lang === 'en' ? 'On this order · ' + pvCount() + ' · −' : '本单已用 · ' + pvCount() + ' 张 · −') + money(t0.pv) + '</span></span>' + S.pv.map(function (x) { return pvCard(pvOf(x.id), x.n, '<a class="sh-link sh-hit" data-act="pvdel:' + x.id + '">移除</a>'); }).join('') +
      (t0.pvOver ? '<div class="sh-note sh-note--warn" data-notr="1">' + (S.lang === 'en' ? 'Vouchers exceed the bill by ' + money(t0.pvOver) + ': no change or refund. Remove one or add items.' : '券面额比应付多 ' + money(t0.pvOver) + '：平台券不找零、不退回，建议移除一张或请顾客加菜。') + '</div>' : '') : '';
    return '<div class="sh-mask sh-mask--side" data-act="ov:none"><div class="sh-sheet" data-stop="1"><div class="hd"><b>平台券 · 验券</b>' + btn(ico('x'), 'sh-btn--muted sh-btn--icon sh-btn--sm', 'ov:none', '关闭') + '</div>' +
      plats + '<div class="sh-search">' + ico('scan-line') + '<span>扫券码或输入券码 · 自动识别平台</span></div>' + sim + res + list +
      '<span class="sh-help">一单最多 3 张；不与店内活动、本店券、人工折扣同享，用后不抹零。收款完成时才向平台核销，取消结账不核销；核销后 24 小时内可在订单详情撤销。</span>' +
      '<div class="dn-acts">' + ((S.pv || []).length ? btn('全部移除', 'sh-btn--muted', 'pvdel:all') : '') + btn('完成', 'sh-btn--block', 'ov:none') + '</div></div></div>';
};
OVL['manual'] = function (t) {
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog" data-stop="1"><span class="h">整单折扣 / 免单</span><span class="d">人工优惠必须选原因；超过 ¥20 或免单需要主管授权。</span>' +
    '<div class="row">' + [5, 10, 20, 50].map(function (v) { return '<span class="sh-filter' + (S.mval === v ? ' is-on' : '') + '" data-act="mval:' + v + '">减 ¥' + v + '</span>'; }).join('') + '<span class="sh-filter' + (S.mval === -1 ? ' is-on' : '') + '" data-act="mval:-1">免单</span></div>' +
    '<span class="sh-gt"><span>原因 · 必选</span></span><div class="row">' + ['服务补偿', '出品问题', '获准议价', '尾数减免', '其他'].map(function (x) { return '<span class="sh-filter' + (S.mwhy === x ? ' is-on' : '') + '" data-act="mwhy:' + x + '">' + x + '</span>'; }).join('') + '</div>' +
    '<div class="acts">' + btn('取消', 'sh-btn--muted', 'ov:none') + (S.mwhy && S.mval ? btn(S.mval > 20 || S.mval < 0 ? '申请授权' : '确认', '', S.mval > 20 || S.mval < 0 ? 'ov:x2' : 'manualok') : btn('先选金额和原因', 'sh-btn--off')) + '</div></div></div>';
};
ACT['pay'] = function (v, k) {
    S.pay = v; S.ent = null; S.svWait = false; S.got = v === 'cash' ? Math.ceil(totals().rest / 100) * 100 : totals().rest; return;
};
ACT['cashok'] = function (v, k) {
    var tc0 = totals(); S.change = Math.round((S.got - tc0.rest) * 100) / 100; S.paid.push(['cash', tc0.rest]); S.ov = 'p3'; return;
};
ACT['payok'] = ACT['svok'] = function (v, k) {
    var tp = totals(); S.paid.push([S.pay, tp.rest]); if (S.pay === 'sv') MEMBER.bal = Math.round((MEMBER.bal - tp.rest) * 100) / 100; S.svWait = false; S.change = 0; S.ov = 'p3'; return;
};
ACT['svgo'] = function (v, k) {
    S.svWait = true; return;
};
ACT['svcancel'] = function (v, k) {
    S.svWait = false; return;
};
ACT['mixm'] = function (v, k) {
    S.mixm = v; return;
};
ACT['mixadd'] = function (v, k) {
    var tm = totals(), amt = Math.min(tm.rest, S.got || tm.rest); S.paid.push([S.mixm, amt]); S.ent = null; S.got = Math.max(0, Math.round((tm.rest - amt) * 100) / 100); toast('已收 ' + PAYNAME[S.mixm] + ' ' + money(amt)); return;
};
ACT['undo'] = function (v, k) {
    S.paid.splice(+v, 1); toast('已撤销这笔，原路退回'); return;
};
ACT['pact'] = function (v, k) {
    S.pAct = !S.pAct; if (S.pAct) S.cpn = ''; return;
};
ACT['pvscan'] = function (v, k) {
    if (S.banner === 'on') { S.ov = 'pvoucher'; return; }
    S.pvLast = v; S.pvN = 1; S.ov = 'pvoucher'; return;
};
ACT['pvn'] = function (v, k) {
    var pv = pvOf(S.pvLast); if (pv) S.pvN = Math.max(1, Math.min(pv.left, 3 - pvCount(), (S.pvN || 1) + Number(v))); return;
};
ACT['pvadd'] = function (v, k) {
    if (pvCount() + (S.pvN || 1) > 3) { toast('一单最多 3 张平台券'); return; }
    S.pv = (S.pv || []).concat([{ id: v, n: S.pvN || 1 }]); S.pAct = false; S.cpn = ''; S.manual = 0; toast((S.lang === 'en' ? 'Added ' : '已加入 ') + pvName(pvOf(v))); return;
};
ACT['pvdel'] = function (v, k) {
    S.pv = v === 'all' ? [] : (S.pv || []).filter(function (x) { return x.id !== v; }); if (!S.pv.length) S.pAct = true; return;
};
ACT['cpn'] = function (v, k) {
    S.cpn = v === 'none' ? '' : v; if (S.cpn) S.pAct = false; S.ov = 'none'; return;
};
ACT['mval'] = function (v, k) {
    S.mval = +v; return;
};
ACT['mwhy'] = function (v, k) {
    S.mwhy = v; return;
};
ACT['manualok'] = function (v, k) {
    S.manual = S.mval; S.ov = 'none'; toast('已减 ¥' + S.mval + '（' + S.mwhy + '）'); return;
};
ACT['lines'] = function (v, k) {
    S.lines = v === 'on'; return;
};
ACT['kp'] = function (v, k) {
    var e = S.ent == null ? '' : S.ent; if (v === 'back') e = e.slice(0, -1); else if (v === 'clr') e = ''; else if (!(v === '.' && e.indexOf('.') > -1) && e.length < 8) e += v; S.ent = e; S.got = +e || 0; return;
};
ACT['got'] = function (v, k) {
    S.got = +v; S.ent = null; return;
};
ACT['payother'] = function (v, k) {
    S.ov = 'none'; S.pay = 'cash'; S.got = Math.ceil(totals().rest / 100) * 100; return;
};
ACT['gopay'] = function (v, k) {
    S.wb = 'checkout'; S.ov = 'none'; S.got = Math.ceil(totals().rest / 100) * 100; return;
};
ACT['paid'] = function (v, k) {
    var tb = TABLES.filter(function (x) { return x.id === S.table; })[0]; if (tb) tb.st = 'to_clear'; S.ov = 'none'; S.wb = 'tables'; S.paid = []; S.change = 0; if ((S.pv || []).length) REDEEMED['#1024'] = S.pv.map(function (x) { return [x.id, x.n, '12:31', 0, ME, 'redeemed']; }); S.cpn = ''; S.pv = []; S.pvLast = ''; S.manual = 0; S.pAct = true; S.member = false; toast(S.table + ' 已结账'); return;
};
MODULES.checkout.render = checkoutWB;
MODULES.checkout.sections = ['账单与优惠', '收款', '完成页'];
