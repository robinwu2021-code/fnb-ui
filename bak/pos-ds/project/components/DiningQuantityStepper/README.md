# DiningQuantityStepper

数量加减：订单行内 36px，规格弹窗和人数 44px（`dn-step--l`）；对应 ai-shop `sh-stepper` 的 POS 版。

- `sh-faint` 胶囊底，无描边。到达下限时减号禁用；订单行里数量为 1 再减，弹出确认后才删除该行。
- **调用方提供：** 值、最小 / 最大值、两个按钮的 `aria-label`。
