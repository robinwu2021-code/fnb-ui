// 零售收银 · 示例数据（关键画面用，本地模拟，不接后端）。方案见 docs/全线点餐POS-零售POS设计方案.md §三
import { round2 } from '@/domain/money';
import { promoOff, type CartLine } from '@/domain/cart';
import { rulesOf, totals, type PricingRules } from '@/domain/pricing';

export type RCat = 'common' | 'fresh' | 'bakery' | 'drinks' | 'snacks' | 'apparel';
export const RCATS: { v: RCat; zh: string; en: string }[] = [
  { v: 'common', zh: '常用', en: 'Frequent' }, { v: 'fresh', zh: '散称', en: 'By weight' }, { v: 'bakery', zh: '烘焙', en: 'Bakery' },
  { v: 'drinks', zh: '饮品', en: 'Drinks' }, { v: 'snacks', zh: '零食', en: 'Snacks' }, { v: 'apparel', zh: '服饰', en: 'Apparel' },
];

/** 商品：weigh = 'kg' 按重量（单价是元 / kg）；sku = 有尺码颜色；mp = 会员价；code = 条码 */
export interface Item { id: string; n: string; en: string; p: number; code: string; c: RCat[]; weigh?: 'kg'; sku?: 1; mp?: number; promo?: 'half2'; s?: number }
export const ITEMS: Item[] = [
  { id: 'bag', n: '购物袋（中）', en: 'Shopping bag (M)', p: 0.3, code: '2000001', c: ['common'] },
  { id: 'water', n: '农夫山泉 550ml', en: 'Spring water 550ml', p: 2, code: '6921168509256', c: ['common', 'drinks'], promo: 'half2' },
  { id: 'cola', n: '可口可乐 330ml', en: 'Coca-Cola 330ml', p: 3.5, code: '6954767423579', c: ['common', 'drinks'] },
  { id: 'milk', n: '纯牛奶 250ml × 12', en: 'Whole milk 250ml × 12', p: 39.9, mp: 36.9, code: '6907992500133', c: ['common', 'drinks'], s: 4 },
  { id: 'chips', n: '薯片 原味 70g', en: 'Potato chips 70g', p: 7.9, code: '6924743915763', c: ['common', 'snacks'] },
  { id: 'apple', n: '富士苹果', en: 'Fuji apple', p: 12.8, weigh: 'kg', code: 'PLU 1001', c: ['common', 'fresh'] },
  { id: 'banana', n: '香蕉', en: 'Banana', p: 6.98, weigh: 'kg', code: 'PLU 1002', c: ['fresh'] },
  { id: 'cherry', n: '车厘子 JJ', en: 'Cherries JJ', p: 98, weigh: 'kg', code: 'PLU 1010', c: ['fresh'] },
  { id: 'grape', n: '阳光玫瑰葡萄', en: 'Shine Muscat grapes', p: 29.8, weigh: 'kg', code: 'PLU 1015', c: ['fresh'] },
  { id: 'tomato', n: '番茄', en: 'Tomato', p: 7.6, weigh: 'kg', code: 'PLU 2003', c: ['fresh'] },
  { id: 'croissant', n: '牛角包', en: 'Croissant', p: 6, code: '2100012', c: ['common', 'bakery'] },
  { id: 'toast', n: '全麦吐司', en: 'Whole-wheat toast', p: 14.8, code: '2100020', c: ['bakery'], s: 2 },
  { id: 'eggtart', n: '葡式蛋挞', en: 'Egg tart', p: 5.5, code: '2100031', c: ['bakery'] },
  { id: 'baguette', n: '法棍', en: 'Baguette', p: 12, code: '2100045', c: ['bakery'], s: 0 },
  { id: 'coffee', n: '冷萃咖啡 300ml', en: 'Cold brew 300ml', p: 15, code: '6971284390011', c: ['drinks'] },
  { id: 'tea', n: '无糖乌龙茶 500ml', en: 'Oolong tea 500ml', p: 5, code: '6901939621271', c: ['drinks'], promo: 'half2' },
  { id: 'yogurt', n: '原味酸奶 200g', en: 'Plain yogurt 200g', p: 6.5, code: '6907992512853', c: ['drinks'] },
  { id: 'nuts', n: '每日坚果 25g', en: 'Daily nuts 25g', p: 4.9, code: '6970399920415', c: ['snacks'] },
  { id: 'choc', n: '黑巧克力 100g', en: 'Dark chocolate 100g', p: 18.9, code: '6923450605882', c: ['snacks'] },
  { id: 'gum', n: '口香糖', en: 'Chewing gum', p: 9.9, code: '6923450656181', c: ['snacks'] },
  { id: 'tee', n: '基础圆领 T 恤', en: 'Basic crew tee', p: 59, sku: 1, code: '2300101', c: ['apparel'] },
  { id: 'socks', n: '棉袜 3 双装', en: 'Cotton socks ×3', p: 29, sku: 1, code: '2300210', c: ['apparel'] },
];
export const item = (id: string) => ITEMS.find((x) => x.id === id)!;

/** SKU 矩阵：行 = 颜色、列 = 尺码，格子 = 库存 */
export const SKU = { colors: [['白', 'White'], ['黑', 'Black'], ['燕麦', 'Oat']] as [string, string][], sizes: ['S', 'M', 'L', 'XL'], stock: [[6, 2, 8, 0], [3, 9, 1, 4], [0, 5, 7, 2]] };

/** 购物车行：类型和加减规则在 domain/cart.ts；这里只补「按商品资料取名称、单价」的部分 */
export type RLine = CartLine;
export const lineName = (l: RLine, en: boolean) => (l.custom ? l.custom.n : en ? item(l.id).en : item(l.id).n);
export const lineUnit = (l: RLine) => (l.custom ? l.custom.p : item(l.id).p);
export const lineAmt = (l: RLine) => (l.gift ? 0 : l.w != null ? round2(lineUnit(l) * l.w) : round2(lineUnit(l) * l.q));
/** 行级促销（规则在 domain/cart.promoOff）：第二件半价 */
export const linePromo = (l: RLine) => (l.custom || l.gift ? 0 : promoOff(item(l.id).promo, l.q, item(l.id).p));
const memberSaveRaw = (l: RLine) => { const it = l.custom ? null : item(l.id); return it?.mp && !l.gift ? round2(Math.max(0, it.p - it.mp) * l.q) : 0; }; /* 会员价只在低于售价时生效 */
export const lineMember = (l: RLine, member: boolean) => (member ? memberSaveRaw(l) : 0);

/** 零售计价适配器：购物车行 → 价格行，用 RETAIL_RULES 算（方案 §八：行级促销 → 会员价 → 券在促销后 → 抹零到角） */
export function rTotals(lines: RLine[], member: boolean, rules: PricingRules = rulesOf('retail')) {
  const t = totals({ lines: lines.map((l) => ({ gross: lineAmt(l), promo: linePromo(l), memberSave: memberSaveRaw(l), pcs: l.w != null ? 1 : l.q })), member }, rules);
  return { ...t, mem: t.memberSave };
}

let K = 100;
export const nextKey = () => ++K;
/** 关键画面里的「扫了十几件」：14 件 8 行，最后扫的是可乐 */
export const CART: RLine[] = [
  { key: 8, id: 'cola', q: 2 }, { key: 7, id: 'tee', q: 1, sku: ['白 / M', 'White / M'] }, { key: 6, id: 'bag', q: 1 }, { key: 5, id: 'milk', q: 1 },
  { key: 4, id: 'croissant', q: 3 }, { key: 3, id: 'apple', q: 1, w: 1.236 }, { key: 2, id: 'chips', q: 1 }, { key: 1, id: 'water', q: 4 },
];
/** 挂起的单 */
export const HELD = [
  { no: '#R0156', time: '10:12', pcs: 5, amt: 48.6, why: ['顾客回去拿钱包', 'Went back for wallet'], member: '' },
  { no: '#R0153', time: '09:47', pcs: 2, amt: 118.0, why: ['等顾客试穿', 'Trying on'], member: '王女士' },
];
/** 未识别的条码 */
export const UNKNOWN_CODE = '6923 4567 0012 8';
