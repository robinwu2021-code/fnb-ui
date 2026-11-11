// 词表按业态覆盖（方案 §1.1 nouns）：只放零售与餐饮不同的 key，查不到回落到主词表。
export const BIZ_DICT: Record<string, Record<string, [string, string]>> = {
  fnb: {},
  retail: {
    'store.name': ['虹选便利 · 科技园店', 'Hongxuan Mart · Tech Park'], 'store.shift': ['李娜 · 早班 08:00 起', 'Li Na · Morning from 08:00'],
    'tab.pos': ['收银', 'Checkout'], 'tab.goods': ['商品', 'Products'], 'tab.menu': ['商品', 'Products'], 'tab.stock': ['库存作业', 'Stock tasks'],
    'co.step1': ['购物车', 'Cart'], 'p3.next': ['下一位', 'Next customer'],
    'biz.reuse': ['本页复用餐饮模块，零售字段（条码、SKU、进价、退换货）在第 4–6 期补齐', 'Reuses the dining module; retail fields (barcode, SKU, cost, returns) arrive in phases 4–6'],
    'biz.stockTodo': ['库存作业（入库、盘点、调拨、报损）在第 5 期做', 'Stock tasks (receive, count, transfer, write-off) come in phase 5'],
  },
};
