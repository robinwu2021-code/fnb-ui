/* 模块 · 会员与营销：会员、标签、储值、活动、优惠券、积分；新建会员、充值、标签、充值方案浮层。 */
/* 会员：[称呼, 手机后四位, 分层, 标签, 本店余额, 到店次数, 累计消费, 最近到店, 来源, 线索] */
var MEMBERS = [['王女士', '6621', '熟客', ['爱吃辣', '带小孩'], 520, 14, 3860, '今天', '下单', 0], ['李先生', '0932', '常客', ['商务宴请'], 1280, 4, 2410, '3 天前', '扫码', 0],
  ['张女士', '7714', '新客', [], 0, 1, 168, '今天', '下单', 0], ['陈先生', '2208', '沉睡', ['爱吃辣'], 60, 9, 1630, '76 天前', '分享', 0], ['刘女士', '5531', '熟客', ['生日月', '素食'], 210, 22, 6120, '昨天', '扫码', 0],
  ['赵先生', '9017', '常客', [], 0, 3, 540, '12 天前', '收藏', 0], ['周女士', '3390', '新客', ['带小孩'], 300, 1, 238, '今天', '手工录入', 1], ['吴先生', '4456', '熟客', ['商务宴请', '爱吃辣'], 860, 31, 9240, '2 天前', '下单', 0]];
var MTAGS = [['爱吃辣', 3], ['带小孩', 2], ['商务宴请', 2], ['素食', 1], ['生日月', 1]];
var PLANS = [{ n: '充 300 送 30', pay: 300, gift: 30, on: true }, { n: '充 500 送 60', pay: 500, gift: 60, on: true }, { n: '充 1000 送 150', pay: 1000, gift: 150, on: true }, { n: '充 2000 送 400', pay: 2000, gift: 400, on: false }];
var RECH = [['R0014', '—', '充 300 送 30', 300, 'wait_collection', '12:28'], ['R0013', '李先生', '充 500 送 60', 500, 'received', '12:20'], ['R0012', '王女士', '充 300 送 30', 300, 'credited', '12:02'], ['R0011', '张女士', '普通储值', 200, 'cancelled', '11:40'], ['R0010', '吴先生', '充 1000 送 150', 1000, 'credited', '10:15']];
/* 活动：[编号, 名称, 类型, 受众, 排期与上限, 状态, 进度] —— 餐饮预置模板 AP020 / AP021 / AP006 / AP024 等 */
var CAMPS = [['AP020', '非高峰 8 折', '折扣 · 最多减 ¥20', '全部顾客', '每天 14:00–17:00 · 预算 ¥3,000', 'running', '已用 126 次 · 预算剩 ¥1,860'], ['AP021', '饮品买二赠一', '买 N 赠 M', '熟客', '长期 · 预算 ¥2,000', 'running', '预算剩 ¥1,240'],
  ['AP006', '生日礼券', '发券 · 兑换 1 次', '生日月会员', '长期 · 限 500 张', 'paused', '已发 32 张'], ['AP024', '充 300 送 30', '充赠', '全部顾客', '2026-10 · 限 200 份', 'running', '已办 88 份'],
  ['AP030', '国庆满 200 减 30', '满减', '全部顾客', '10-01 至 10-07', 'ended', '到期结束'], ['AP031', '新品尝鲜 9 折', '折扣 · 最多减 ¥10', '新客', '未排期', 'draft', '—']];
var CPNT = [['满 200 减 15', '满减券', '下单抵扣', 320, 96], ['饮品体验券', '兑换券', '到店出示', 150, 41], ['8 折券 · 最多减 ¥30', '折扣券', '下单抵扣', 80, 12], ['生日礼券', '兑换券', '到店出示', 32, 9]];
function mName(m) { return m[0] + (m[9] ? '（线索）' : ''); }
function membersWB() {
  var nav = rail([{ cap: '会员' }, { v: '会员', icon: ico('users'), label: '会员', em: MEMBERS.length }, { v: '标签', icon: ico('tags'), label: '标签', em: MTAGS.length }, { cap: '营销' }, { v: '储值', icon: ico('wallet'), label: '储值' }, { v: '活动', icon: ico('megaphone'), label: '活动', em: CAMPS.filter(function (c) { return c[5] === 'running'; }).length }, { v: '优惠券', icon: ico('ticket'), label: '优惠券' }, { v: '积分', icon: ico('coins'), label: '积分' }], S.msec, 'msec');
  return nav + ({ 会员: mList, 标签: mTags, 储值: mStored, 活动: mCamps, 优惠券: mCoupons, 积分: mPoints }[S.msec] || mList)();
}
function mList() {
  var lay = S.mlayer, list = MEMBERS.filter(function (m) { return lay === '全部' || (lay === '线索' ? m[9] : m[2] === lay && !m[9]); });
  var head = '<div class="sh-toolbar">' + search('输入完整手机号查找（不支持模糊搜索）') + btn(ico('user-plus') + '新建会员', 'sh-btn--soft sh-btn--sm', 'ov:mnew') + '</div><div class="row">' +
    ['全部', '新客', '常客', '熟客', '沉睡', '线索'].map(function (x) { var n = x === '全部' ? MEMBERS.length : MEMBERS.filter(function (m) { return x === '线索' ? m[9] : m[2] === x && !m[9]; }).length; return '<span class="sh-filter' + (lay === x ? ' is-on' : '') + '" data-act="mlayer:' + x + '">' + x + '<em>' + n + '</em></span>'; }).join('') + '</div>';
  var body = '<div class="sh-table" style="--cols:minmax(0,1.2fr) 72px 64px minmax(0,1.4fr) 88px 72px 80px"><div><span>会员</span><span>手机</span><span>分层</span><span>标签</span><span class="r">本店余额</span><span class="r">到店</span><span class="r">最近</span></div>' +
    list.map(function (m) { var i = MEMBERS.indexOf(m); return '<div data-act="msel:' + i + '" class="' + (S.msel === i ? 'is-on' : '') + '"><span><b>' + mName(m) + '</b></span><span class="num">****' + m[1] + '</span><span>' + (m[9] ? stx('member', 'lead', '线索', 'dot') : '<span class="sh-chip">' + m[2] + '</span>') + '</span><span>' + m[3].map(function (t) { return '<span class="sh-chip">' + t + '</span>'; }).join(' ') + '</span><span class="r">¥' + m[4] + '</span><span class="r">' + m[5] + ' 次</span><span class="r">' + m[7] + '</span></div>'; }).join('') + '</div>' +
    '<p class="sh-help">分层（新客 · 常客 · 熟客 · 沉睡）由系统按到店行为计算，只读；标签由门店自定义。手机号只显示后四位，不提供导出。</p>';
  var m = MEMBERS[S.msel] || MEMBERS[0];
  var det = side('<div class="row row--between"><b class="h-title">' + mName(m) + '</b>' + (m[9] ? stx('member', 'lead', '线索会员') : '<span class="sh-chip">' + m[2] + '</span>') + '</div><span class="sh-help">138****' + m[1] + ' · 来源：' + m[8] + ' · 加入 2025-03</span>',
    (m[9] ? '<div class="sh-note sh-note--warn">手工录入的是线索会员：不享受权益、不能推送；顾客本人注册后自动转正。</div>' : '') +
    '<div class="dn-mcard"><div class="kv"><span><small>本店余额</small><b>¥' + m[4].toFixed(2) + '</b></span><span><small>累计消费</small><b>¥' + m[6].toLocaleString() + '</b></span><span><small>到店</small><b>' + m[5] + ' 次</b></span></div></div>' +
    '<div class="dn-block"><div class="dn-sec"><span>标签 · 最多 10 个</span><a class="sh-link sh-hit" data-act="ov:mtag">编辑</a></div><div class="row">' + (m[3].length ? m[3].map(function (t) { return '<span class="sh-chip">' + t + '</span>'; }).join('') : '<span class="sh-help">还没有标签</span>') + '</div></div>' +
    '<div class="dn-block"><div class="dn-sec"><span>收支记录</span><span class="sh-seg">' + ['全部', '储值', '核销'].map(function (x, i) { return '<span class="' + (i === 0 ? 'is-on' : '') + '">' + x + '</span>'; }).join('') + '</span></div><div class="sh-cells">' +
    [['10-23 12:02', '储值 · 充 300 送 30', '+¥330.00', '¥520.00'], ['10-20 19:14', '核销 · 结账 #0998', '−¥186.00', '¥190.00'], ['10-12 18:40', '储值 · 普通储值', '+¥200.00', '¥376.00']].map(function (r) { return '<div class="sh-cell"><span class="k">' + r[0] + '<br><small>' + r[1] + '</small></span><span class="v num">' + r[2] + '<br><small class="sh-help">余额 ' + r[3] + '</small></span></div>'; }).join('') + '</div></div>',
    '<div class="dn-acts">' + btn('编辑资料', 'sh-btn--muted', 'ov:mnew') + btn('充值', 'sh-btn--block', 'ov:recharge') + '</div>');
  return panel(head, body) + det;
}
function mTags() {
  var head = '<div class="row row--between"><b class="h-title">标签</b>' + btn(ico('plus') + '新建标签', 'sh-btn--soft sh-btn--sm', 'ov:mtagnew') + '</div><span class="sh-help">每店最多 50 个，同一品牌下各门店共享；系统分层不可修改。</span>';
  var body = '<div class="dn-block"><div class="dn-sec"><span>自定义标签 · ' + MTAGS.length + ' / 50</span></div><div class="sh-cells">' + MTAGS.map(function (t, i) { return '<div class="sh-cell" data-act="mtsel:' + i + '"><span class="k mlabel">' + ico('tag') + '<b>' + t[0] + '</b></span><span class="v">' + t[1] + ' 人 ›</span></div>'; }).join('') + '</div></div>' +
    '<div class="dn-block"><div class="dn-sec"><span>系统分层 · 只读</span></div><div class="sh-cells">' + [['新客', '累计 1 单'], ['常客', '近 90 天 2–5 单'], ['熟客', '近 90 天 ≥ 6 单'], ['沉睡', '超过 60 天没来']].map(function (r) { return '<div class="sh-cell"><span class="k">' + r[0] + '</span><span class="v">' + r[1] + '</span></div>'; }).join('') + '</div></div>';
  var t = MTAGS[S.mtsel] || MTAGS[0];
  var det = side('<b class="h-title">' + t[0] + '</b><span class="sh-help">' + t[1] + ' 位会员</span>', '<div class="sh-cells">' + MEMBERS.filter(function (m) { return m[3].indexOf(t[0]) > -1; }).map(function (m) { return '<div class="sh-cell"><span class="k">' + mName(m) + '</span><span class="v num">****' + m[1] + '</span></div>'; }).join('') + '</div>' +
    '<div class="sh-note">活动可以用标签限定受众（「受众：标签」），不需要单独建「会员专享」活动。</div>', '<div class="dn-acts">' + btn('重命名', 'sh-btn--muted', 'toast:已重命名') + btn('删除', 'sh-btn--danger', 'toast:已删除标签') + '</div>');
  return panel(head, body) + det;
}
function mStored() {
  var head = '<div class="row row--between"><b class="h-title">储值</b>' + btn(ico('plus') + '新建方案', 'sh-btn--soft sh-btn--sm', 'ov:plannew') + '</div><span class="sh-help">餐饮储值默认关闭，本店已开启。同店共用一个账户，不同门店隔离；不转赠、不自动到期；充值不计入营业额。</span>';
  var body = '<div class="dn-block"><div class="dn-sec"><span>充值方案 · 一次只选一档，店员不能临时改赠送</span></div><div class="dn-plans">' + PLANS.map(function (p, i) { return '<div class="dn-plan' + (p.on ? '' : ' is-off') + '"><b>' + p.n + '</b><span class="sh-help">实收 ¥' + p.pay + ' · 赠送 ¥' + p.gift + ' · 到账 ¥' + (p.pay + p.gift) + '</span><span class="row row--between">' + stx('campaign', p.on ? 'running' : 'paused', p.on ? '已启用' : '已停用', 'dot') + '<span class="sh-switch' + (p.on ? ' is-on' : '') + '" data-act="plan:' + i + '" role="switch" aria-label="启用"></span></span></div>'; }).join('') + '</div></div>' +
    '<div class="dn-block"><div class="dn-sec"><span>今日储值单</span><span>收银员办理 · 核验实收后到账</span></div><div class="sh-table" style="--cols:64px minmax(0,1fr) minmax(0,1.2fr) 80px 120px 56px"><div><span>单号</span><span>会员</span><span>方案</span><span class="r">实收</span><span>状态</span><span class="r">时间</span></div>' +
    RECH.map(function (r) { return '<div><span class="num">' + r[0] + '</span><span>' + r[1] + '</span><span>' + r[2] + '</span><span class="r">¥' + r[3] + '</span><span>' + stx('recharge', r[4], null, 'dot') + '</span><span class="r">' + r[5] + '</span></div>'; }).join('') + '</div></div>';
  var det = side('<b class="h-title">办理储值</b><span class="sh-help">先识别会员，再选方案</span>', '<div class="sh-cells"><div class="sh-cell"><span class="k">今日实收</span><span class="v num">¥2,300.00</span></div><div class="sh-cell"><span class="k">今日赠送</span><span class="v num">¥270.00</span></div><div class="sh-cell"><span class="k">待核验实收</span><span class="v num">1 笔</span></div></div>' +
    '<div class="sh-note">权限：办理储值、核验实收、储值核销分开授权；没有「核验实收」权限的员工看不到确认按钮。首期没有线上退款，有争议登记待办线下处理。</div>', '<div class="dn-acts">' + btn('办理储值', 'sh-btn--block', 'ov:recharge') + '</div>');
  return panel(head, body) + det;
}
function mCamps() {
  var f = S.cst;
  var head = '<div class="row row--between"><b class="h-title">营销活动</b>' + btn(ico('plus') + '从模板新建', 'sh-btn--soft sh-btn--sm', 'toast:已从模板新建草稿') + '</div><div class="row">' + [['all', '全部']].concat(['running', 'paused', 'draft', 'ended'].map(function (k) { return [k, stName('campaign', k)]; })).map(function (x) {
    var n = x[0] === 'all' ? CAMPS.length : CAMPS.filter(function (c) { return c[5] === x[0]; }).length; return '<span class="sh-filter' + (f === x[0] ? ' is-on' : '') + '" data-act="cst:' + x[0] + '">' + (x[0] === 'all' ? '' : stIcon('campaign', x[0])) + '<span data-notr="1">' + x[1] + '</span><em>' + n + '</em></span>'; }).join('') + '</div>';
  var list = CAMPS.filter(function (c) { return f === 'all' || c[5] === f; });
  var body = '<div class="dn-ocards">' + list.map(function (c) { var i = CAMPS.indexOf(c); return '<div class="dn-ocard' + (S.csel === i ? ' is-on' : '') + (c[5] === 'ended' ? ' is-quiet' : '') + '" style="' + stv(stGroup('campaign', c[5])) + '" data-act="csel:' + i + '"><div class="t"><b>' + c[1] + '</b>' + stx('campaign', c[5], null, 'dot') + '</div><span class="sh-help">' + c[2] + ' · ' + c[3] + '</span><span class="sh-help">' + c[4] + '</span><span class="sh-help">' + c[6] + '</span></div>'; }).join('') + '</div>';
  var c = CAMPS[S.csel] || CAMPS[0];
  var acts = c[5] === 'running' ? btn('暂停', 'sh-btn--muted', 'camp:paused') + btn('结束', 'sh-btn--danger', 'camp:ended') : c[5] === 'paused' ? btn('结束', 'sh-btn--danger', 'camp:ended') + btn('恢复', 'sh-btn--block', 'camp:running') : c[5] === 'draft' ? btn('删除', 'sh-btn--danger', 'toast:已删除草稿') + btn('发布', 'sh-btn--block', 'camp:running') : '';
  var det = side('<div class="row row--between"><b class="h-title">' + c[1] + '</b>' + stx('campaign', c[5]) + '</div><span class="sh-help">' + c[0] + ' · ' + c[2] + '</span>',
    '<div class="sh-cells"><div class="sh-cell"><span class="k">受众</span><span class="v">' + c[3] + '</span></div><div class="sh-cell"><span class="k">排期与上限</span><span class="v">' + c[4] + '</span></div><div class="sh-cell"><span class="k">进度</span><span class="v">' + c[6] + '</span></div><div class="sh-cell"><span class="k">与券</span><span class="v">互斥（本单参加活动就不能用券）</span></div><div class="sh-cell"><span class="k">人工打折</span><span class="v">不可再打折</span></div></div>' +
    '<div class="sh-note">进行中只能改结束时间和上限；暂停只停新参与；长期活动必须设份数或预算，用完自动结束。</div>', acts ? '<div class="dn-acts">' + acts + '</div>' : '');
  return panel(head, body) + det;
}
function mCoupons() {
  var head = '<b class="h-title">优惠券</b><span class="sh-help">满减券 · 现金券 · 折扣券（必须封顶）· 兑换券（次数 > 1 即次卡）。核销方式二选一：下单抵扣，或到店出示（不参与算价）。</span>';
  var body = '<div class="sh-table" style="--cols:minmax(0,1.4fr) 80px 88px 72px 72px 72px"><div><span>券</span><span>类型</span><span>核销方式</span><span class="r">已发</span><span class="r">已用</span><span class="r">使用率</span></div>' +
    CPNT.map(function (c) { return '<div><span><b>' + c[0] + '</b></span><span>' + c[1] + '</span><span>' + c[2] + '</span><span class="r">' + c[3] + '</span><span class="r">' + c[4] + '</span><span class="r">' + Math.round(c[4] / c[3] * 100) + '%</span></div>'; }).join('') + '</div>' +
    '<div class="dn-block"><div class="dn-sec"><span>券状态</span></div><div class="row">' + ['unused', 'used', 'expired', 'revoked'].map(function (k) { return stx('coupon', k); }).join('') + '</div></div>';
  var v = S.redeem;
  var det = side('<b class="h-title">核销券</b><span class="sh-help">扫券码或输入券码，先预览再确认</span>', search('扫券码 / 输入券码') + '<div class="row">' + btn('模拟：扫到「饮品体验券」', 'sh-btn--muted sh-btn--sm', 'redeem:show') + '</div>' +
    (v ? '<div class="dn-coupon' + (v === 'done' ? ' is-off' : '') + '"><span class="v">兑</span><span class="tx"><b>饮品体验券</b><small>兑换 1 杯任意饮品 · 顾客 ****6621 · 至 2026-12-31 · 剩 1 次</small></span>' + stx('coupon', v === 'done' ? 'used' : 'unused', null, 'dot') + '</div>' +
      (v === 'done' ? '<div class="sh-note">已核销 · 12:31 · 操作人 李敏。核销不可撤销。</div>' : '<div class="sh-note sh-note--warn">核销后不可撤销；3 秒内重复点击只算一次。</div>') : '<div class="sh-empty"><i></i><span>等待扫码</span></div>'),
    v === 'show' ? '<div class="dn-acts">' + btn('取消', 'sh-btn--muted', 'redeem:') + btn('确认核销', 'sh-btn--danger-solid sh-btn--block', 'redeem:done') + '</div>' : '');
  return panel(head, body) + det;
}
function mPoints() {
  var head = '<b class="h-title">积分</b><span class="sh-help">积分是平台出资的统一积分，不是门店积分。</span>';
  var body = '<div class="sh-note sh-note--warn">按现行规则（ai-shop PRD-积分模块 AC-PTS-08）：线下支付不发积分、不能积分抵现。收银台只显示会员的积分余额。</div>' +
    '<div class="dn-block"><div class="dn-sec"><span>积分账户分桶</span></div><div class="row">' + ['pending', 'available', 'held', 'refund_reserved'].map(function (k) { return stx('points', k); }).join('') + '</div></div>' +
    '<div class="dn-block"><div class="dn-sec"><span>线上订单的规则（供参考）</span></div><div class="sh-cells">' + [['发放', '商品例外 > 类目 > 平台兜底；按件或按现金实付比例'], ['生效', '支付后先「待生效」，结算终局后可用'], ['抵扣', '上限建议 30%，以券后金额为基数（待定）'], ['到期', '闲置 365 天滚动过期'], ['退款', '抵扣按比例退回，赠送按快照收回']].map(function (r) { return '<div class="sh-cell"><span class="k">' + r[0] + '</span><span class="v">' + r[1] + '</span></div>'; }).join('') + '</div></div>';
  return panel(head, body) + side('<b class="h-title">待确认</b>', '<div class="sh-note">是否开放门店积分（发放 + 抵现）需要后端支持，原型暂不提供。</div>');
}

OVL['mnew'] = function (t) {
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog" data-stop="1"><span class="h">新建会员</span><span class="d">输入完整 11 位手机号；先查重，没有才新建。手工录入的是线索会员，本人注册后转正。</span>' +
    '<span class="sh-gt"><span>手机号 · 必填</span></span><div class="sh-input">138 0000 7714</div><span class="sh-gt"><span>称呼</span></span><div class="sh-input ph-">例如「张女士」</div><span class="sh-gt"><span>生日 · 可选</span></span><div class="sh-input ph-">月 / 日</div>' +
    '<span class="sh-gt"><span>标签 · 可选</span></span><div class="row">' + MTAGS.map(function (t) { return '<span class="sh-filter">' + t[0] + '</span>'; }).join('') + '</div>' +
    '<div class="acts">' + btn('仅保存', 'sh-btn--muted', 'msave:only') + btn('保存并储值', '', 'msave:sv') + '</div></div></div>';
};
OVL['recharge'] = function (t) {
    var pl = PLANS.filter(function (p) { return p.on; }), cur = pl[S.plan] || pl[0], rs = S.rcState;
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog wide" data-stop="1"><span class="h">办理储值 · ' + MEMBER.name + ' ****' + MEMBERS[0][1] + '</span>' +
      (rs ? '<div class="dn-result' + (rs === 'fail' ? ' dn-result--bad' : '') + '">' + (rs === 'wait' ? '<i class="sh-spin"></i><span class="t">处理中…</span><span class="sh-help">正在入账，请勿重复提交</span>' : '<span class="ic"></span><span class="t">已到账 ¥' + (cur.pay + cur.gift) + '</span><span class="sh-help">实收 ¥' + cur.pay + ' · 赠送 ¥' + cur.gift + ' · 余额 ¥' + MEMBER.bal.toFixed(2) + '</span>') + '</div>' +
        (rs === 'done' ? '<div class="acts">' + btn('打印凭条', 'sh-btn--muted', 'toast:已打印储值凭条') + btn('完成', '', 'rcdone') + '</div>' : '')
      : '<span class="sh-gt"><span>选方案 · 只能选，不能改赠送</span></span><div class="dn-plans">' + pl.map(function (p, i) { return '<div class="dn-plan' + (S.plan === i ? ' is-on' : '') + '" data-act="plan_s:' + i + '"><b>' + p.n + '</b><span class="sh-help">到账 ¥' + (p.pay + p.gift) + '</span></div>'; }).join('') + '</div>' +
        '<span class="sh-gt"><span>实收方式</span></span><span class="sh-seg">' + ['现金', '微信', '支付宝', '银行卡'].map(function (x, i) { return '<span class="' + (S.rcm === i ? 'is-on' : '') + '" data-act="rcm:' + i + '">' + x + '</span>'; }).join('') + '</span>' +
        '<span class="sh-help">点确认 = 确认已收到顾客的钱；新会员在首次到账时开户。</span><div class="acts">' + btn('取消', 'sh-btn--muted', 'ov:none') + btn('确认储值 · 实收 ¥' + cur.pay, '', 'rcgo') + '</div>') + '</div></div>';
};
OVL['mtag'] = OVL['mtagnew'] = function (t) {
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog" data-stop="1"><span class="h">' + (S.ov === 'mtag' ? '编辑标签 · ' + mName(MEMBERS[S.msel] || MEMBERS[0]) : '新建标签') + '</span>' +
    (S.ov === 'mtag' ? '<div class="row">' + MTAGS.map(function (t) { var on = (MEMBERS[S.msel] || MEMBERS[0])[3].indexOf(t[0]) > -1; return '<span class="sh-filter' + (on ? ' is-on' : '') + '" data-act="mtog:' + t[0] + '">' + t[0] + '</span>'; }).join('') + '</div><span class="sh-help">每位会员最多 10 个标签</span>'
      : '<span class="sh-gt"><span>标签名</span></span><div class="sh-input">爱喝茶</div><span class="sh-help">每店最多 50 个；同品牌各门店共享</span>') +
    '<div class="acts">' + btn('取消', 'sh-btn--muted', 'ov:none') + btn('保存', '', S.ov === 'mtag' ? 'ov:none' : 'mtagadd') + '</div></div></div>';
};
OVL['plannew'] = function (t) {
    return '<div class="sh-mask" data-act="ov:none"><div class="sh-dialog" data-stop="1"><span class="h">新建充值方案</span>' +
    '<span class="sh-gt"><span>实收金额</span></span><div class="sh-input sh-input--num">¥ 800.00</div><span class="sh-gt"><span>赠送金额（可为 0）</span></span><div class="sh-input sh-input--num">¥ 100.00</div>' +
    '<div class="dn-amount dn-amount--sm"><span class="sh-help">到账合计</span><b>¥900.00</b></div><span class="sh-help">实收 > 0、最多两位小数；同一实收金额不能重复。停用只阻止新办理，不影响已有余额。</span>' +
    '<div class="acts">' + btn('取消', 'sh-btn--muted', 'ov:none') + btn('保存并启用', '', 'plansave') + '</div></div></div>';
};
ACT['mnew'] = function (v, k) {
    S.ov = 'sheet'; S.sheet = 'member'; S.mtab = 'new'; return;
};
ACT['msec'] = function (v, k) {
    S.msec = v; return;
};
ACT['mlayer'] = function (v, k) {
    S.mlayer = v; return;
};
ACT['msel'] = function (v, k) {
    S.msel = +v; return;
};
ACT['mtsel'] = function (v, k) {
    S.mtsel = +v; return;
};
ACT['csel'] = function (v, k) {
    S.csel = +v; return;
};
ACT['cst'] = function (v, k) {
    S.cst = v; return;
};
ACT['camp'] = function (v, k) {
    var cc = CAMPS[S.csel] || CAMPS[0]; cc[5] = v; toast(cc[1] + ' ' + stName('campaign', v)); return;
};
ACT['plan'] = function (v, k) {
    PLANS[+v].on = !PLANS[+v].on; toast(PLANS[+v].n + (PLANS[+v].on ? ' 已启用' : ' 已停用，已有余额不受影响')); return;
};
ACT['plan_s'] = function (v, k) {
    S.plan = +v; return;
};
ACT['rcm'] = function (v, k) {
    S.rcm = +v; return;
};
ACT['rcgo'] = function (v, k) {
    S.rcState = 'wait'; setTimeout(function () { var p = PLANS.filter(function (x) { return x.on; })[S.plan] || PLANS[0]; MEMBER.bal += p.pay + p.gift; MEMBERS[0][4] = MEMBER.bal; S.rcState = 'done'; render(); }, 900); return;
};
ACT['rcdone'] = function (v, k) {
    S.rcState = ''; S.ov = 'none'; return;
};
ACT['msave'] = function (v, k) {
    MEMBERS.push(['张女士', '7714', '新客', [], 0, 0, 0, '今天', '手工录入', 1]); S.msel = MEMBERS.length - 1; if (v === 'sv') { S.ov = 'recharge'; } else { S.ov = 'none'; toast('已保存为线索会员（本人注册后转正）'); } return;
};
ACT['mtog'] = function (v, k) {
    var mm = MEMBERS[S.msel] || MEMBERS[0], ti = mm[3].indexOf(v); if (ti > -1) mm[3].splice(ti, 1); else if (mm[3].length < 10) mm[3].push(v); return;
};
ACT['mtagadd'] = function (v, k) {
    MTAGS.push(['爱喝茶', 0]); S.ov = 'none'; toast('已新建标签「爱喝茶」'); return;
};
ACT['plansave'] = function (v, k) {
    PLANS.push({ n: '充 800 送 100', pay: 800, gift: 100, on: true }); S.ov = 'none'; toast('已新建并启用「充 800 送 100」'); return;
};
ACT['redeem'] = function (v, k) {
    S.redeem = v; if (v === 'done') toast('已核销 饮品体验券'); return;
};
ACT['mph'] = function (v, k) {
    var ph = S.mph || ''; S.mph = v === 'back' ? ph.slice(0, -1) : v === 'clr' ? '' : ph.length < 11 ? ph + v : ph; return;
};
ACT['gomember'] = function (v, k) {
    S.wb = 'members'; S.msec = '会员'; S.msel = 0; S.ov = 'none'; return;
};
ACT['munbind'] = function (v, k) {
    S.member = false; toast('已解绑本单会员'); return;
};
MODULES.members.render = membersWB;
MODULES.members.sections = ['会员', '标签', '储值', '活动', '优惠券', '积分'];
