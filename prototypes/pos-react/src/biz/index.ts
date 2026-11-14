// 业态配置（方案 §1.1）：所有「餐饮 / 零售」差异集中在这里，组件里不散落 if (biz === 'retail')。
// 只放数据和跳转；各业态工作台的渲染表在 layout/workbench.tsx（餐饮）和 retail/RetailWorkbench.tsx（零售），避免和模块互相引用。
import { useStore, type ModuleKey } from '@/store';
import { MODULES } from '@/layout/modules';
import { MENU, type SetGroup, type SetItem } from '@/data/sample';
import { useBiz, type BizKey, type RetailTab } from './store';

export interface BizTab { v: string; label: string; /** 布局借用哪个餐饮模块的声明（W1–W4 + 栏宽） */ layout: ModuleKey; /** 不上页签，从设置「管理」进 */ home?: boolean }
export interface BizDef {
  key: BizKey; label: [string, string];
  tabs: BizTab[]; menu: SetGroup[];
  /** 结单区 / 购物车头的快捷操作（默认值；餐饮可拖动改，存主 store.quick） */ quick: string[];
  /** 计价规则（方案 §八）：餐饮 lib/pricing.totals · 零售 retail/data.rTotals */ pricing: 'fnb' | 'retail';
  /** 费用行 */ fees: [string, string][];
}

/* ---------- 零售设置菜单（方案 §七）：从餐饮菜单派生，改动的组整组替换 ---------- */
const g = (k: string) => MENU.find((x) => x[0] === k)!;
const pick = (k: string, keys: string[]): SetItem[] => keys.map((i) => g(k)[3].find((x) => x[0] === i)).filter(Boolean) as SetItem[];
const RETAIL_MENU: SetGroup[] = [
  g('ops'),
  ['goods', '商品', 'Products', [
    ['barcode', '条码规则', 'Barcode rules', [['店内码前缀', 'In-store prefix', '20–29'], ['称重码格式', 'Scale label format', '18 位 · 金额 + 重量'], ['一码多品时', 'Shared barcode', '弹出选择']]],
    ['labels', '价签模板', 'Shelf labels', [['价签尺寸', 'Label size', '60 × 40 mm'], ['改价后自动进待打列表', 'Queue labels after price change', 'on'], ['标签打印机', 'Label printer', '已连接']]],
    ['expiry', '临期与预警', 'Expiry & alerts', [['临期提醒提前', 'Warn before expiry', '3 天'], ['临期自动折扣', 'Auto discount near expiry', '8 折'], ['安全库存提醒', 'Low-stock alert', 'on']]],
    ['limits', '限购与禁售', 'Limits & restrictions', [['烟酒禁售时段', 'Tobacco & alcohol hours', '无'], ['促销品每人限购', 'Promo limit per customer', '2 件'], ['禁售需主管放行', 'Override needs approval', 'on']]]]],
  ['pay', '订单与支付', 'Orders & payment', [
    ...pick('pay', ['methods']),
    ['bags', '购物袋与押金', 'Bags & deposits', [['购物袋（中）', 'Bag (M)', '¥0.30'], ['购物袋（大）', 'Bag (L)', '¥0.50'], ['瓶押金', 'Bottle deposit', 'off']]],
    ...pick('pay', ['rounding', 'discount']),
    ['returns', '退换货', 'Returns & exchanges', [['退货期限', 'Return window', '7 天'], ['无单退货需授权', 'No-receipt return needs approval', 'on'], ['无单退货退款方式', 'No-receipt refund to', '现金 / 储值'], ['退款原路返回', 'Refund to original method', 'on']]],
    ...pick('pay', ['invoice'])]],
  ['channel', '线上渠道', 'Online channels', [
    ...pick('channel', ['platforms']),
    ['mall', '小程序商城', 'Mini-program store', [['线上商城', 'Online store', 'on'], ['自提', 'Pick-up', 'on'], ['同城配送', 'Local delivery', 'off']]],
    ['picking', '拣货规则', 'Picking', [['自动接单', 'Auto-accept', 'on'], ['接单后打印拣货单', 'Print pick list on accept', 'on'], ['缺货时', 'When out of stock', '联系顾客']]]]],
  g('staff'),
  ['device', '设备', 'Devices', [
    ...pick('device', ['scanner', 'scale', 'devices', 'printers']),
    ['labelp', '标签打印机', 'Label printer', [['价签打印机', 'Shelf label printer', '已连接'], ['默认份数', 'Copies', '1']]],
    ...pick('device', ['drawer', 'display', 'network'])]],
  ['store', '门店', 'Store', g('store')[3].map((x) => (x[0] === 'info' ? ['info', '门店信息', 'Store info', [['门店名称', 'Store name', '虹选便利 · 科技园店'], ['业态', 'Business type', '零售'], ['联系电话', 'Phone', '0755-8888 7777']]] as SetItem : x[0] === 'receipts' ? ['receipts', '小票模板', 'Receipt templates', [['销售小票', 'Sales receipt', '›'], ['退货小票', 'Return receipt', '›'], ['小票打印语言', 'Receipt language', '中英双语']]] as SetItem : x))],
  g('display'), g('system'),
];
const FNB_MENU = MENU.map((grp) => (grp[0] === 'store' ? [grp[0], grp[1], grp[2], grp[3].map((x) => (x[0] === 'info' && x[3] ? [x[0], x[1], x[2], [...x[3].slice(0, 1), ['业态', 'Business type', '餐饮'], ...x[3].slice(1)]] as SetItem : x))] as SetGroup : grp));

export const BIZ: Record<BizKey, BizDef> = {
  fnb: {
    key: 'fnb', label: ['餐饮', 'Dining'],
    tabs: (Object.keys(MODULES) as ModuleKey[]).map((k) => ({ v: k, label: MODULES[k].name, layout: k, home: !!MODULES[k].home })),
    menu: FNB_MENU, quick: ['member', 'hold', 'split', 'share', 'reprice'], pricing: 'fnb',
    fees: [['服务费', 'Service charge'], ['茶位费', 'Tea charge'], ['打包费', 'Packaging']],
  },
  retail: {
    key: 'retail', label: ['零售', 'Retail'],
    tabs: [
      { v: 'pos', label: 'tab.pos', layout: 'checkout' }, { v: 'orders', label: 'tab.orders', layout: 'orders' }, { v: 'goods', label: 'tab.goods', layout: 'menu' },
      { v: 'reports', label: 'tab.reports', layout: 'reports' }, { v: 'settings', label: 'tab.settings', layout: 'settings' },
      { v: 'members', label: 'tab.members', layout: 'members', home: true }, { v: 'shift', label: 'tab.shift', layout: 'shift', home: true }, { v: 'stock', label: 'tab.stock', layout: 'orders', home: true },
    ],
    menu: RETAIL_MENU, quick: ['member', 'hold', 'pick', 'disc', 'reprice'], pricing: 'retail',
    fees: [['购物袋', 'Bags'], ['押金', 'Deposit']],
  },
};
export const useBizDef = () => BIZ[useBiz((s) => s.biz)];
/** 当前业态的当前工作台（页签选中用；不上页签的模块归到「设置」） */
export function useCurTab(): { tab: BizTab; on: string } {
  const def = useBizDef(), wb = useStore((s) => s.wb), rwb = useBiz((s) => s.rwb);
  const v = def.key === 'retail' ? rwb : wb, tab = def.tabs.find((x) => x.v === v) ?? def.tabs[0];
  return { tab, on: tab.home ? 'settings' : tab.v };
}
/** 进某个工作台：餐饮走原来的 wb / snav 动作，零售切 rwb */
export function goModule(k: string) {
  if (useBiz.getState().biz === 'retail') useBiz.getState().set({ rwb: k as RetailTab });
  else useStore.getState().act('snav:wb.' + k);
}
export { useBiz };
