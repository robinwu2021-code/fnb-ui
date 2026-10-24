# DiningStatusLabel

只读状态与标签，基于 ai-shop `.sh-chip`：tint 色块、胶囊、12px、从不加粗。

- 状态一律 `dn-dot` + 文字：已下单（success）、催菜中 / 待结账 / 缺纸（warning）、已退 / 已沽清（danger）、就餐中 / 外带 / 转入合入（info）。
- 主色 tint 只给与钱有关的标签：特价、赠、会员价、预订。菜品属性（辣、素、新品）用中性 `sh-chip`。
- `dn-count` 是主色实心的数量角标，用在菜品卡、分类、入口上。
- 标签不可点。可点的筛选用 FilterChip。
