# OptionPicker

分组选项：规格、辣度、加料、套餐换菜、支付方式、优惠券；对应 ai-shop `sh-option` 的 POS 版。

- 选项是 `sh-faint` 色块，选中变 `sh-primary-tint` 底、`sh-primary-text` 字，不用描边。
- 必选组标 `sh-chip--primary`「必选」，多选组标中性 `sh-chip`「可多选」；加价写在选项下方。
- 不可选的选项保留并写原因（「已沽清」「离线不可用」），不只是变灰。
- `dn-opt--row` 是整行版本，用于支付方式、优惠券、桌台操作菜单。
