// 示例数据：从 prototypes/bapp/ui/core.js 原样搬来（本地示例，不接后端）
import { voucherUndo } from '@/domain/aftersale';
export type Cat = { key: string; k: string; en: string };
export const CATS: Cat[] = [['热销', 'red', 'Popular'], ['招牌', 'rose', 'Signature'], ['凉菜', 'green', 'Cold'], ['热菜', 'orange', 'Hot'], ['主食', 'yellow', 'Staples'], ['汤羹', 'teal', 'Soups'], ['饮品', 'blue', 'Drinks'], ['套餐', 'purple', 'Sets']].map(([key, k, en]) => ({ key, k, en }));
export const CK: Record<string, string> = Object.fromEntries(CATS.map((c) => [c.key, c.k]));
CK['甜品'] = 'brown';

export interface Dish { n: string; en: string; p: number; c: string; hot?: 1; spec?: 1; s: number | null; img?: string; tg?: string[]; timed?: string; noimg?: 1 }
const W = 'https://thumb.wikimedia.org/wikipedia/commons/thumb/';
export const DISHES: Dish[] = [
  { img: W + '8/8e/Kung_Pao_Chicken_at_Yujiayan_Restaurant_%2820230510123120%29.jpg/500px-Kung_Pao_Chicken_at_Yujiayan_Restaurant_%2820230510123120%29.jpg', tg: ['招牌', '辣'], n: '宫保鸡丁', en: 'Kung Pao Chicken', p: 48, c: '热菜', hot: 1, spec: 1, s: 32 },
  { img: W + '4/44/Shuizhuyu_at_Hamarkand_Silk_Road_Cuisine%2C_Beiqinglu_%2820230121154615%29.jpg/500px-Shuizhuyu_at_Hamarkand_Silk_Road_Cuisine%2C_Beiqinglu_%2820230121154615%29.jpg', tg: ['招牌', '辣'], n: '水煮鱼', en: 'Sichuan Boiled Fish', p: 88, c: '招牌', hot: 1, spec: 1, s: 8 },
  { img: W + '1/13/%E6%B9%98%E8%8F%9C%E9%A6%86%E4%B9%8B%E5%B0%8F%E7%82%92%E9%BB%84%E7%89%9B%E8%82%89.jpg/500px-%E6%B9%98%E8%8F%9C%E9%A6%86%E4%B9%8B%E5%B0%8F%E7%82%92%E9%BB%84%E7%89%9B%E8%82%89.jpg', n: '小炒黄牛肉', en: 'Stir-fried Beef', p: 68, c: '热菜', hot: 1, s: 15 },
  { tg: ['素'], n: '蒜蓉西兰花', en: 'Garlic Broccoli', p: 28, c: '凉菜', noimg: 1, s: 40 },
  { img: W + '8/88/Chen_Mapo_Tofu.jpg/500px-Chen_Mapo_Tofu.jpg', tg: ['辣'], n: '麻婆豆腐', en: 'Mapo Tofu', p: 26, c: '热菜', hot: 1, s: 26 },
  { img: W + '5/54/%E9%85%B8%E8%8F%9C%E9%B1%BC_Preserved_Mustard_Green_with_Fish_-_Charming_Spice_AUD24.80_%284104355401%29.jpg/500px-%E9%85%B8%E8%8F%9C%E9%B1%BC_Preserved_Mustard_Green_with_Fish_-_Charming_Spice_AUD24.80_%284104355401%29.jpg', n: '酸菜鱼', en: 'Pickled Cabbage Fish', p: 78, c: '招牌', hot: 1, s: 5 },
  { img: W + '4/45/Fuqi_Feipian.jpg/500px-Fuqi_Feipian.jpg', n: '夫妻肺片', en: 'Spicy Beef Slices', p: 38, c: '凉菜', hot: 1, s: 12 },
  { tg: ['素'], n: '拍黄瓜', en: 'Smashed Cucumber', p: 16, c: '凉菜', noimg: 1, s: 50 },
  { n: '米饭', en: 'Steamed Rice', p: 3, c: '主食', hot: 1, noimg: 1, s: null },
  { img: W + 'd/d2/Dan-dan_noodles%2C_Shanghai.jpg/500px-Dan-dan_noodles%2C_Shanghai.jpg', n: '担担面', en: 'Dan Dan Noodles', p: 22, c: '主食', s: 0 },
  { n: '番茄蛋汤', en: 'Tomato Egg Soup', p: 18, c: '汤羹', noimg: 1, s: 20 },
  { img: W + '6/60/Sour_plum_drink.jpg/500px-Sour_plum_drink.jpg', tg: ['新品'], n: '酸梅汤', en: 'Sour Plum Drink', p: 12, c: '饮品', hot: 1, s: 60 },
  { n: '鲜榨橙汁', en: 'Fresh Orange Juice', p: 18, c: '饮品', noimg: 1, s: 3 },
  { tg: ['新品'], timed: '14:00 起', n: '红糖糍粑', en: 'Brown Sugar Rice Cake', p: 22, c: '甜品', noimg: 1, s: 10 },
  { tg: ['套餐'], n: '双人套餐', en: 'Set for Two', p: 168, c: '套餐', spec: 1, noimg: 1, s: 6 },
];
export function dish(n: string): Dish { return DISHES.find((d) => d.n === n) ?? { n, en: n, p: 0, c: '', s: null }; }

/* 订单行：st = new 待下单 · cook 制作中 · done 已上 · rush 催菜中 · gift 赠送 · void 已退 */
export type LineSt = 'new' | 'cook' | 'done' | 'rush' | 'gift' | 'void';
export interface Line { n: string; q: number; p: number; st: LineSt; sub?: string; rp?: boolean }
export const INITIAL_LINES: Line[] = [
  { n: '宫保鸡丁', q: 1, p: 48, sub: 'spec.sample', st: 'new' }, { n: '米饭', q: 4, p: 3, st: 'new' },
  { n: '酸菜鱼', q: 1, p: 78, st: 'cook' }, { n: '夫妻肺片', q: 1, p: 38, st: 'done' }, { n: '小炒黄牛肉', q: 1, p: 68, st: 'rush' }, { n: '酸梅汤', q: 1, p: 12, st: 'gift', sub: '原价 ¥12.00' },
];
/* 订单行状态 → 业务状态字典 item 实体 */
export const LINE_ST: Record<LineSt, string> = { new: 'draft', cook: 'cooking', done: 'served', rush: 'rushed', gift: 'comp', void: 'voided' };

export type TableSt = 'free' | 'booked' | 'seated' | 'dining' | 'late' | 'due' | 'partial' | 'to_clear' | 'merged' | 'shared' | 'disabled';
export interface Table { id: string; st: TableSt; seats: number; ppl: number; min: number; amt: number }
export const INITIAL_TABLES: Table[] = (['dining', 'dining', 'dining', 'free', 'due', 'late', 'seated', 'booked', 'dining', 'free', 'partial', 'due', 'free', 'dining', 'to_clear', 'shared', 'free', 'disabled'] as TableSt[])
  .map((st, i) => ({ id: i < 12 ? 'A' + ('0' + (i + 1)).slice(-2) : 'V0' + (i - 11), st, seats: i < 12 ? 4 : 10, ppl: 2 + (i % 3), min: 15 + i * 7, amt: 120 + i * 31 }));
export const busyTable = (st: TableSt) => /^(seated|dining|late|due|partial|shared)$/.test(st);

export interface Coupon { id: string; name: string; kind: string; amt?: number; min?: number; need?: string; disc?: number; cap?: number }
export const COUPONS: Coupon[] = [{ id: 'c15', name: '满 200 减 15', kind: '满减券', amt: 15, min: 200 }, { id: 'drink', name: '饮品体验券', kind: '兑换券', amt: 12, need: '酸梅汤' },
  { id: 'c30', name: '满 300 减 30', kind: '满减券', amt: 30, min: 300 }, { id: 'd8', name: '8 折券 · 最多减 ¥30', kind: '折扣券', disc: 0.2, cap: 30 }];

/* 平台券：[key, 名, 底色, 字, 字色, 授权状态, 英文名] */
export interface Plat { key: string; name: string; bg: string; ch: string; fg: string; auth: string; en: string }
export const PLATS: Plat[] = [['mt', '美团·点评', '#FFC300', '美', '#181A1C', '已授权', 'Meituan'], ['dy', '抖音', '#161823', '抖', '#FFFFFF', '已授权', 'Douyin'], ['ks', '快手', '#FF4906', '快', '#FFFFFF', '未授权', 'Kuaishou'], ['xhs', '小红书', '#FF2442', '红', '#FFFFFF', '未开通', 'Xiaohongshu']]
  .map(([key, name, bg, ch, fg, auth, en]) => ({ key, name, bg, ch, fg, auth, en }));
export const plat = (k: string) => PLATS.find((p) => p.key === k)!;
export interface PVoucher { id: string; plat: string; kind: string; name: string; en: string; face: number; price: number; left: number; code: string; till: string; items?: string[] }
export const PVOUCHERS: PVoucher[] = [
  { id: 'mt100', plat: 'mt', kind: '代金券', name: '100 元代金券', en: '¥100 voucher', face: 100, price: 88, left: 2, code: '6021 8873 5510', till: '2026-12-31' },
  { id: 'dy116', plat: 'dy', kind: '套餐券', name: '招牌双拼套餐', en: 'Signature set for two', face: 116, price: 99, left: 1, items: ['酸菜鱼', '夫妻肺片'], code: '8801 2297 3364', till: '2026-11-30' }];
export const pvOf = (id: string) => PVOUCHERS.find((v) => v.id === id);
export const PVERR: Record<string, [string, string, string]> = { used: ['mt', '券已使用', '这张券 10-21 19:02 已在本店核销，不能重复使用'], ks: ['ks', '平台未授权', '快手团购还没有授权给本店；到「设置 › 外卖与渠道 › 团购验券」授权后再验'] };

export const MEMBER = { name: '王女士', phone: '138****6621', layer: '熟客', tags: ['爱吃辣', '带小孩'], bal: 520, coupons: 2, since: '2025-03' };

/* 快捷操作：结单区头下一排 5 个，设置里可换。[标签 key, 图标, 动作] */
export const QUICK: Record<string, [string, string, string]> = {
  member: ['quick.member', 'scan-line', 'sheet:member'], hold: ['quick.hold', 'pause', 'ov:hold'], split: ['quick.split', 'split', 'batch:split'], share: ['quick.share', 'users', 'ov:share'], reprice: ['quick.reprice', 'tag', 'ov:reprice'],
  note: ['quick.note', 'notebook-pen', 'ov:note'], move: ['quick.move', 'arrow-right-left', 'ov:x3'], comp: ['quick.comp', 'gift', 'ov:x2'], prebill: ['quick.prebill', 'printer', 'toast:toast.prebill'], pvoucher: ['quick.pvoucher', 'ticket-check', 'ov:pvoucher'], drawer: ['quick.drawer', 'archive', 'toast:toast.drawer'],
  soldout: ['quick.soldout', 'circle-slash', 'toast:toast.soldoutMode'], custom: ['quick.custom', 'file-plus', 'toast:toast.customItem'], call: ['quick.call', 'megaphone', 'toast:toast.call'],
};
/* 菜品标记（属性）：不用状态色，Lucide 图标 + 次要色 */
export const TAGS: Record<string, string> = { 招牌: 'award', 新品: 'sparkle', 辣: 'flame', 素: 'leaf', 称重: 'scale', 套餐: 'layers' };

/* 收款方式：[key, 名, 说明, 品牌色, 一个字]。品牌色是规范 §3.4 的例外，靠它认支付方式 */
export interface Pay { key: string; name: string; desc: string; color: string; ch: string }
export const PAYS: Pay[] = [['wx', '微信', '扫码', '#07C160', '微'], ['ali', '支付宝', '扫码', '#1677FF', '支'], ['cash', '现金', '找零', '#A46911', '¥'], ['card', '银行卡', '刷卡', '#5552BB', '卡'], ['sv', '储值', '会员余额', '#8747AA', '储'], ['mix', '组合支付', '多种方式', 'var(--c-control-off)', '组']]
  .map(([key, name, desc, color, ch]) => ({ key, name, desc, color, ch }));
export const PAY_EN: Record<string, [string, string]> = { wx: ['WeChat', 'QR'], ali: ['Alipay', 'QR'], cash: ['Cash', 'change'], card: ['Card', 'swipe'], sv: ['Stored value', 'member balance'], mix: ['Split', 'several methods'] };
export const payOf = (k: string) => PAYS.find((p) => p.key === k);

/* 订单：[单号, 桌号或平台单号（外卖写 P1 / P2 #号，显示时按地区映射到已连接的平台，见 lib/channels platRef）, 渠道, 人数, 件数, 下单时间, 金额, 实体, 状态, 上菜进度] */
export type OrderRow = [string, string, string, number, number, string, number, string, string, string];
export const INITIAL_ORDERS: OrderRow[] = [['#1024', 'A03', '堂食', 4, 6, '11:42', 268, 'order', 'open', '3/6'], ['#1025', 'A05', '堂食', 2, 3, '11:58', 96, 'order', 'due', '3/3'],
  ['#1026', '—', '外带', 1, 2, '12:03', 42, 'order', 'held', ''], ['#1027', 'P1 #88', '外卖', 0, 4, '12:10', 118, 'delivery', 'accepted', ''],
  ['#1028', 'V02', '堂食', 10, 12, '12:12', 886, 'order', 'open', '5/12'], ['#1029', 'A09', '堂食', 3, 5, '12:20', 172, 'order', 'partial', '5/5'],
  ['#1030', 'P2 #31', '外卖', 0, 2, '12:21', 42, 'delivery', 'new', ''], ['#1031', 'P1 #91', '外卖', 0, 3, '12:05', 76, 'delivery', 'timeout', ''],
  ['#1032', 'P1 #79', '外卖', 0, 5, '11:30', 134, 'delivery', 'delivering', ''], ['#1020', 'A01', '堂食', 4, 7, '11:02', 312, 'order', 'paid', '7/7'],
  ['#1021', 'A04', '堂食', 2, 4, '11:10', 136, 'order', 'paid', '4/4'], ['#1019', 'A07', '堂食', 2, 4, '10:48', 158, 'order', 'reopened', '4/4'],
  ['#1018', '—', '外带', 1, 2, '10:40', 38, 'order', 'cancelled', ''], ['#1033', 'P2 #36', '外卖', 0, 2, '12:24', 56, 'delivery', 'pickup', ''],
  ['#1017', 'P1 #70', '外卖', 0, 3, '10:20', 88, 'delivery', 'delivered', ''], ['#1016', 'P2 #22', '外卖', 0, 1, '10:12', 28, 'delivery', 'refunding', '']];
/* 平台券核销记录（按单）：[券, 张数, 核销时间, 距今小时, 操作人, 状态 redeemed / undone]。当前收银员 = 李敏。
 * 撤销规则（平台券方案 §七）：24 小时内本人直接撤（写原因）；别人核销的需主管授权码；超过 24 小时平台不再受理，只能去平台商家后台退款。 */
export type Redeemed = [string, number, string, number, string, 'redeemed' | 'undone'];
export const INITIAL_REDEEMED: Record<string, Redeemed[]> = { '#1020': [['mt100', 2, '11:46', 0.8, '李敏', 'redeemed']], '#1021': [['dy116', 1, '11:12', 1.3, '王芳', 'redeemed']], '#1019': [['mt100', 1, '10-22 10:31', 26, '李敏', 'redeemed']] };
export const ME = '李敏';
/** 撤销核销的前置判断：规则在 domain/aftersale.voucherUndo */
export const pvUndoWhy = (r: Redeemed) => voucherUndo({ undone: r[5] === 'undone', hoursAgo: r[3], operator: r[4] }, ME);
export const OST: Record<string, string[]> = { order: ['open', 'due', 'partial', 'held', 'paid', 'reopened', 'cancelled'], delivery: ['new', 'accepted', 'pickup', 'delivering', 'delivered', 'rejected', 'refunding', 'timeout'] };
export const ALERT: Record<string, 1> = { due: 1, partial: 1, new: 1, timeout: 1 };

/* ── 第 3 步：菜品管理 · 会员与营销 · 报表 · 设置 ── */
// 渠道与同步：见 data/platforms.ts（按地区的平台）和 lib/channels.ts
export const MODS: [string, string, string[], number, string, string][] = [['份量', '单选 · 必选', ['大份 +¥0', '小份 −¥14'], 6, 'Size', 'single · required'], ['辣度', '单选 · 必选', ['不辣', '微辣', '中辣', '特辣'], 9, 'Spice', 'single · required'], ['加料', '多选 · 最多 3 项', ['加花生 +¥3', '加鸡肉 +¥12', '加蛋 +¥2'], 4, 'Add-ons', 'multiple · max 3'], ['做法', '多选', ['少油', '少盐', '不要葱', '不要香菜'], 15, 'Preparation', 'multiple'], ['温度', '单选', ['常温', '加冰', '热饮'], 3, 'Temperature', 'single']];
export const SETS = [{ n: '双人套餐', en: 'Set for Two', p: 168, groups: [['凉菜 · 任选 1', ['拍黄瓜', '夫妻肺片', '蒜蓉西兰花']], ['热菜 · 任选 2', ['宫保鸡丁', '麻婆豆腐', '小炒黄牛肉', '酸菜鱼 +¥10']], ['主食 · 2 份', ['米饭']], ['饮品 · 2 杯', ['酸梅汤', '鲜榨橙汁 +¥6']]] as [string, string[]][], swap: true },
  { n: '工作日午市单人餐', en: 'Weekday Lunch', p: 38, groups: [['热菜 · 任选 1', ['宫保鸡丁', '麻婆豆腐']], ['主食 · 1 份', ['米饭']], ['汤 · 1 份', ['番茄蛋汤']]] as [string, string[]][], swap: false }];
export const CAT_ALL = [...CATS.slice(1), { key: '甜品', k: 'brown', en: 'Desserts' }];

/* 会员：[称呼, 手机后四位, 分层, 标签, 本店余额, 到店次数, 累计消费, 最近到店, 来源, 线索] */
export type Member = [string, string, string, string[], number, number, number, string, string, number];
export const INITIAL_MEMBERS: Member[] = [['王女士', '6621', '熟客', ['爱吃辣', '带小孩'], 520, 14, 3860, '今天', '下单', 0], ['李先生', '0932', '常客', ['商务宴请'], 1280, 4, 2410, '3 天前', '扫码', 0],
  ['张女士', '7714', '新客', [], 0, 1, 168, '今天', '下单', 0], ['陈先生', '2208', '沉睡', ['爱吃辣'], 60, 9, 1630, '76 天前', '分享', 0], ['刘女士', '5531', '熟客', ['生日月', '素食'], 210, 22, 6120, '昨天', '扫码', 0],
  ['赵先生', '9017', '常客', [], 0, 3, 540, '12 天前', '收藏', 0], ['周女士', '3390', '新客', ['带小孩'], 300, 1, 238, '今天', '手工录入', 1], ['吴先生', '4456', '熟客', ['商务宴请', '爱吃辣'], 860, 31, 9240, '2 天前', '下单', 0]];
export const INITIAL_MTAGS: [string, number][] = [['爱吃辣', 3], ['带小孩', 2], ['商务宴请', 2], ['素食', 1], ['生日月', 1]];
export const LAYERS = ['新客', '常客', '熟客', '沉睡'];
export const LAYER_EN: Record<string, string> = { 新客: 'New', 常客: 'Regular', 熟客: 'Loyal', 沉睡: 'Dormant', 线索: 'Lead', 全部: 'All' };
export interface Plan { n: string; pay: number; gift: number; on: boolean }
export const INITIAL_PLANS: Plan[] = [{ n: '充 300 送 30', pay: 300, gift: 30, on: true }, { n: '充 500 送 60', pay: 500, gift: 60, on: true }, { n: '充 1000 送 150', pay: 1000, gift: 150, on: true }, { n: '充 2000 送 400', pay: 2000, gift: 400, on: false }];
export const RECH: [string, string, string, number, string, string][] = [['R0014', '—', '充 300 送 30', 300, 'wait_collection', '12:28'], ['R0013', '李先生', '充 500 送 60', 500, 'received', '12:20'], ['R0012', '王女士', '充 300 送 30', 300, 'credited', '12:02'], ['R0011', '张女士', '普通储值', 200, 'cancelled', '11:40'], ['R0010', '吴先生', '充 1000 送 150', 1000, 'credited', '10:15']];
/* 活动：[编号, 名称, 类型, 受众, 排期与上限, 状态, 进度] */
export type Camp = [string, string, string, string, string, string, string];
export const INITIAL_CAMPS: Camp[] = [['AP020', '非高峰 8 折', '折扣 · 最多减 ¥20', '全部顾客', '每天 14:00–17:00 · 预算 ¥3,000', 'running', '已用 126 次 · 预算剩 ¥1,860'], ['AP021', '饮品买二赠一', '买 N 赠 M', '熟客', '长期 · 预算 ¥2,000', 'running', '预算剩 ¥1,240'],
  ['AP006', '生日礼券', '发券 · 兑换 1 次', '生日月会员', '长期 · 限 500 张', 'paused', '已发 32 张'], ['AP024', '充 300 送 30', '充赠', '全部顾客', '2026-10 · 限 200 份', 'running', '已办 88 份'],
  ['AP030', '国庆满 200 减 30', '满减', '全部顾客', '10-01 至 10-07', 'ended', '到期结束'], ['AP031', '新品尝鲜 9 折', '折扣 · 最多减 ¥10', '新客', '未排期', 'draft', '—']];
export const CPNT: [string, string, string, number, number][] = [['满 200 减 15', '满减券', '下单抵扣', 320, 96], ['饮品体验券', '兑换券', '到店出示', 150, 41], ['8 折券 · 最多减 ¥30', '折扣券', '下单抵扣', 80, 12], ['生日礼券', '兑换券', '到店出示', 32, 9]];

/* 报表 */
export const RP = {
  hours: [['10', 320], ['11', 980], ['12', 2860], ['13', 2410], ['14', 620], ['15', 280], ['16', 240], ['17', 410], ['18', 1980], ['19', 2640], ['20', 1720], ['21', 540], ['22', 120]] as [string, number][],
  dishes: [['宫保鸡丁', 38, 1824], ['米饭', 96, 288], ['酸菜鱼', 21, 1638], ['麻婆豆腐', 26, 676], ['水煮鱼', 14, 1232], ['酸梅汤', 33, 396], ['夫妻肺片', 17, 646], ['小炒黄牛肉', 12, 816]] as [string, number, number][],
  pays: [['微信', 4102, 22], ['支付宝', 2340, 12], ['现金', 1280, 6], ['银行卡', 620, 3], ['储值', 300, 3]] as [string, number, number][],
  promos: [['非高峰 8 折（活动）', 412, 31], ['满 200 减 15（券）', 90, 6], ['抹零', 38, 41], ['人工折扣', 72, 3]] as [string, number, number][],
  pvs: [['美团·点评 团购券', 1240, 14], ['抖音 团购券', 696, 6]] as [string, number, number][],
  voids: [['11:52', '酸梅汤 ×1', '退菜', '上错菜', '李敏', '张伟'], ['12:20', '水煮鱼 ×1', '赠送', '服务补偿', '王芳', '张伟'], ['12:41', '米饭 ×2', '退菜', '客人退菜', '李敏', '—'], ['13:05', '整单 ¥20', '人工折扣', '出品问题', '李敏', '张伟']] as string[][],
  layers: [['新客', 12], ['常客', 21], ['熟客', 9], ['沉睡', 4]] as [string, number][],
};

/* 设置菜单：[键, 中文, English, 项[[键, 中文, English, 行[[中文, English, 值]]]]]；值 on / off 画开关，其余画「值 ›」 */
export type SetRow = [string, string, string];
export type SetItem = [string, string, string, SetRow[]?];
export type SetGroup = [string, string, string, SetItem[]];
export const MENU: SetGroup[] = [
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
    ['platforms', '平台对接', 'Platforms'],
    ['accept', '接单规则', 'Order intake', [['自动接单', 'Auto-accept', 'off'], ['接单后自动送厨', 'Send to kitchen on accept', 'on'], ['新单提示音', 'New order sound', 'on']]],
    ['ready', '出餐通知', 'Ready alerts', [['出餐后通知骑手', 'Notify rider when ready', 'on']]],
    ['vouchers', '团购验券', 'Group-buy vouchers', [['美团·点评', 'Meituan · Dianping', '已授权'], ['抖音来客', 'Douyin', '已授权'], ['快手', 'Kuaishou', '去授权 ›'], ['小红书', 'Xiaohongshu', '未开通'],
      ['与店内活动同享', 'Combine with store campaigns', 'off'], ['与本店券同享', 'Combine with store coupons', 'off'], ['每单最多张数', 'Max per order', '3 张'], ['核销时机', 'Redeem at', '收款完成后'], ['撤销核销', 'Undo redemption within', '24 小时内']]]]],
  ['staff', '员工与权限', 'Staff & roles', [
    ['people', '员工', 'Staff'], ['roles', '角色与权限', 'Roles & permissions'],
    ['codes', '授权码', 'Approval codes', [['主管授权码', 'Manager code', '已设置'], ['授权码有效期', 'Code expiry', '90 天']]],
    ['schedule', '排班', 'Schedules', [['早班', 'Morning', '09:00–15:00'], ['晚班', 'Evening', '15:00–23:00']]]]],
  ['device', '设备', 'Devices', [
    ['devices', '设备列表', 'Devices'], ['printers', '打印机', 'Printers'], ['routing', '档口路由', 'Station routing'],
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
    ['tax', '税率与币种', 'Tax & currency', [['税率', 'Tax rate', '6%'], ['币种', 'Currency', 'CURRENCY']]]]],
  ['display', '界面与显示', 'Display', [['shape', '界面与显示', 'Display']]],
  ['system', '系统', 'System', [
    ['sync', '数据同步', 'Data sync', [['上次同步', 'Last sync', '12:28'], ['立即同步', 'Sync now', '›']]],
    ['updates', '版本更新', 'Updates', [['当前版本', 'Version', '2.6.0'], ['自动更新', 'Auto-update', 'on']]],
    ['audit', '操作日志', 'Audit log', [['今日操作', 'Today', '128 条'], ['敏感操作', 'Sensitive', '6 条']]],
    ['help', '帮助与反馈', 'Help & feedback', [['使用手册', 'Manual', '›'], ['联系客服', 'Support', '400-800-8888']]]]]];
