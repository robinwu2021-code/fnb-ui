// 零售收银词条：只放零售差异的 key（方案 §1.1 dict.retail），和主词表同一格式 [中, 英]
import { useStore } from '@/store';
import { STATES, type StDef } from '@/lib/states';

const D: Record<string, [string, string]> = {
  'store': ['虹选便利 · 科技园店', 'Hongxuan Mart · Tech Park'], 'shift': ['收银员 李娜 · 早班', 'Cashier Li Na · AM'],
  'tab.pos': ['收银', 'Checkout'], 'tab.orders': ['订单', 'Orders'], 'tab.goods': ['商品', 'Products'], 'tab.reports': ['报表', 'Reports'], 'tab.settings': ['设置', 'Settings'],
  'entry.scanner': ['扫码枪 · 已连接', 'Scanner · connected'], 'entry.scale': ['电子秤 · 已连接', 'Scale · connected'], 'entry.print': ['打印机', 'Printer'], 'entry.net': ['网络 · 在线', 'Network · online'], 'entry.member': ['会员', 'Members'], 'entry.display': ['显示', 'Display'],
  'cart': ['购物车', 'Cart'], 'cart.no': ['单号 {no}', 'No. {no}'], 'cart.empty': ['扫码开始收银', 'Scan to start'], 'cart.emptyDesc': ['扫码枪对准商品条码即可加入；无码商品点右侧快捷商品', 'Point the scanner at a barcode, or tap a quick item on the right'],
  'q.member': ['会员', 'Member'], 'q.hold': ['挂单', 'Hold'], 'q.pick': ['取单', 'Recall'], 'q.disc': ['整单折扣', 'Discount'], 'q.reprice': ['改价', 'Price'],
  'member.add': ['扫会员码 / 输手机号', 'Scan member code / phone'], 'member.on': ['王女士 · 金卡 · 余额 ¥520.00', 'Ms Wang · Gold · balance ¥520.00'], 'member.help': ['识别会员后自动按会员价计算', 'Member prices apply once identified'],
  'line.each': ['¥{p} / 件', '¥{p} each'], 'line.kg': ['{w} kg × ¥{p}/kg', '{w} kg × ¥{p}/kg'], 'line.half2': ['第二件半价', '2nd at half price'], 'line.mp': ['会员价 ¥{p}', 'Member ¥{p}'], 'line.custom': ['临时商品', 'Custom item'],
  'line.reprice': ['改价', 'Price'], 'line.gift': ['赠送', 'Free'], 'line.del': ['删除', 'Remove'], 'line.note': ['备注', 'Note'],
  'fees': ['优惠', 'Savings'], 'fees.order': ['促销 → 会员价 → 券 → 抹零', 'Promo → member → coupon → rounding'],
  'fee.promo': ['促销 · 第二件半价', 'Promo · 2nd half price'], 'fee.mem': ['会员价', 'Member price'], 'fee.memOff': ['会员价 · 识别会员后可用', 'Member price · identify member first'], 'fee.coupon': ['优惠券 · 在促销后计算', 'Coupon · applied after promos'], 'fee.round': ['抹零 · 抹分', 'Rounding · cents'],
  'due': ['应收', 'Total'], 'due.note': ['{n} 件 · 已优惠 {m}', '{n} items · saved {m}'], 'pay': ['收款', 'Pay'], 'more': ['继续加商品', 'Add more items'],
  'scan.ph': ['扫条码，或输入编码 / 拼音 / 名称', 'Scan barcode, or type code / name'], 'scan.qty': ['下一件 × {n}', 'Next × {n}'],
  'scan.last': ['刚扫', 'Just scanned'], 'scan.undo': ['撤销', 'Undo'], 'scan.lastN': ['车里共 {n} 件', '{n} in cart'],
  'scan.unknown': ['条码 {c} 没有找到商品', 'Barcode {c} not found'], 'scan.custom': ['临时商品', 'Custom item'], 'scan.new': ['新建商品', 'New product'],
  'scan.weighCode': ['称重码已解析', 'Scale label read'],
  'quick': ['快捷商品', 'Quick items'], 'quick.hint': ['无码商品、散称、购物袋', 'No-barcode, by-weight and bags'],
  'card.kg': ['¥{p}/kg', '¥{p}/kg'], 'card.sku': ['多规格', 'Variants'], 'card.out': ['缺货', 'Out of stock'], 'card.low': ['仅剩 {n}', 'Only {n} left'],
  'weigh.title': ['称重 · {n}', 'Weigh · {n}'], 'weigh.unit': ['单价 ¥{p}/kg', '¥{p}/kg'], 'weigh.stable': ['电子秤 · 已稳定', 'Scale · stable'], 'weigh.tare': ['去皮', 'Tare'], 'weigh.zero': ['清零', 'Zero'], 'weigh.manual': ['手输重量', 'Enter weight'],
  'weigh.amt': ['金额', 'Amount'], 'weigh.add': ['加入 {m}', 'Add {m}'], 'weigh.help': ['把商品放上秤，读数稳定后自动变黑；去皮后再放商品', 'Place the item on the scale; the reading turns dark once stable'],
  'sku.title': ['选规格 · {n}', 'Choose variant · {n}'], 'sku.help': ['格子里是库存；缺货的不能选', 'Numbers are stock; out-of-stock cells are disabled'], 'sku.color': ['颜色', 'Colour'], 'sku.add': ['加入 {s}', 'Add {s}'], 'sku.pick': ['先选颜色和尺码', 'Pick colour and size'],
  'held.title': ['取单 · 挂起 {n} 单', 'Recall · {n} on hold'], 'held.note': ['当前购物车还有 {n} 行；取单前会先把它挂起', 'Current cart has {n} lines; it will be put on hold first'], 'held.take': ['取回', 'Recall'], 'held.pcs': ['{n} 件', '{n} items'],
  'step1': ['购物车', 'Cart'], 'step2': ['收款方式', 'Method'], 'step3': ['收款', 'Collect'], 'step4': ['完成', 'Done'],
  'rest': ['待收', 'Remaining'], 'paid': ['已收', 'Paid'], 'method': ['收款方式', 'Payment method'],
  'got': ['实收', 'Received'], 'change': ['找零', 'Change'], 'short': ['还差', 'Short'], 'quickAmt': ['快捷金额', 'Quick amounts'], 'clr': ['清空', 'Clear'], 'confirm': ['确认收现', 'Confirm cash'],
  'qrWait': ['请顾客出示付款码', 'Ask for the payment code'], 'qrHint': ['用扫码枪扫顾客手机上的付款码；也可在购物车里直接扫，会自动跳到这里', 'Scan the code on the customer’s phone; scanning it in the cart jumps here too'], 'qrDev': ['扫码枪 · 已连接', 'Scanner · connected'],
  'cardWait': ['请顾客刷卡或挥卡', 'Ask the customer to tap or insert'], 'cardDev': ['刷卡机 · 已连接', 'Card reader · connected'], 'manualOk': ['手动确认到账', 'Confirm manually'], 'cancelPay': ['换一种方式', 'Change method'],
  'done.title': ['收款成功', 'Paid'], 'done.change': ['找零', 'Change'], 'done.reprint': ['补打小票', 'Reprint'], 'done.next': ['下一位', 'Next customer'], 'done.auto': ['3 秒后自动回到收银；客显屏同步显示找零', 'Returns to checkout in 3 s; the customer display shows the change'],
  'done.got': ['实收', 'Received'], 'done.method': ['收款方式', 'Method'], 'done.save': ['本单优惠', 'Saved'], 'done.points': ['积分', 'Points'],
  'q.more': ['更多', 'More'], 'more.clear': ['清空购物车', 'Clear cart'], 'more.drawer': ['开钱箱', 'Open drawer'], 'more.reprint': ['补打上一单', 'Reprint last'], 'more.note': ['整单备注', 'Order note'], 'more.needAuth': ['需授权', 'Needs approval'],
  'tool.peek': ['查价', 'Price check'], 'tool.custom': ['临时商品', 'Custom item'], 'tool.drawer': ['开钱箱', 'Open drawer'], 'scan.plus1': ['再来一件', 'One more'],
  'peek.idle': ['查价模式：扫码只看价格和库存，不加入购物车', 'Price check: scanning shows price and stock without adding'], 'peek.add': ['加入购物车', 'Add to cart'], 'peek.stock': ['库存 {n}', 'Stock {n}'], 'peek.off': ['退出查价', 'Exit'],
  'fast.cash': ['现金正好', 'Exact cash'], 'fast.qr': ['扫码收款', 'Scan to pay'],
  'clear.title': ['清空购物车？', 'Clear the cart?'], 'clear.desc': ['会移除 {n} 行、共 {p} 件商品；清空后不能恢复，需要的话先挂单', 'Removes {n} lines ({p} items). This can’t be undone — hold the order instead if needed'], 'clear.ok': ['清空', 'Clear'],
  'drawer.toast': ['钱箱已打开 · 已记录到操作日志', 'Drawer opened · logged'],
  'proto': ['原型只画了收银工作台', 'Only the checkout workbench is drawn in this prototype'],
};
function tr(lang: string, key: string, params?: Record<string, string | number>) {
  let s = D[key] ? D[key][lang === 'en' ? 1 : 0] : key;
  if (params) for (const k in params) s = s.split('{' + k + '}').join(String(params[k]));
  return s;
}
export function useRT() { const lang = useStore((s) => s.lang); return (key: string, params?: Record<string, string | number>) => tr(lang, key, params); }
/** 组件外（动作函数里）用 */
export const rt = (key: string, params?: Record<string, string | number>) => tr(useStore.getState().lang, key, params);

/* 零售新增状态（方案 §六）：先本地合并，之后并入 gen-colors.py 再 sync:tokens */
const EXTRA: Record<string, Record<string, StDef>> = {
  item: { weighed: { zh: '称重', en: 'Weighed', group: 'trace', icon: 'scale' }, promo: { zh: '促销', en: 'Promo', group: 'trace', icon: 'badge-percent' }, returned: { zh: '退货', en: 'Returned', group: 'failed', icon: 'package-x' } },
  stock: { out: { zh: '缺货', en: 'Out of stock', group: 'failed', icon: 'circle-slash' }, expiry: { zh: '临期', en: 'Near expiry', group: 'action', icon: 'calendar-clock' } },
};
Object.entries(EXTRA).forEach(([ent, m]) => { STATES.states[ent] = { ...(STATES.states[ent] || {}), ...m }; });
