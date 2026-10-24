# SearchInput

菜品搜索框和普通输入框：胶囊形、`sh-surface` 底、无描边，聚焦时 2px 主色光圈。

- 搜索同时匹配菜名、拼音首字母（`gbjd` → 宫保鸡丁）和编号；与当前分类、筛选求交。
- 错误：2px `sh-danger` 光圈，下方 `dn-err` 说明原因和改法。
- 金额、授权码、手机号在 POS 上用 DiningCashPad / DiningAuthPad 的数字键盘输入，不调起系统键盘。
