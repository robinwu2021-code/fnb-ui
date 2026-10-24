# SegmentedControl

2–4 个互斥模式的切换，例如堂食 / 外带 / 外卖。轨道 `sh-faint`，选中项浮为 `sh-surface`。

- 胶囊形，项高 `pos-ctl-s` 减去轨道内边距。
- **调用方提供：** `role="group"` 与 `aria-label`，每项 `aria-pressed`。
- 切换会改变整单规则（服务费、打包费）的用它；筛选列表用 FilterChip。
