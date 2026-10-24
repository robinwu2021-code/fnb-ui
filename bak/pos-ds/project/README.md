Dining POS 是餐饮收银平板端的界面规范，定稿为 **L1 布局 + B 端色块方案**：沿用 ai-shop B 端组件库的设计语言（扁平色块、tint 分层、胶囊按钮、五档圆角），在它上面加一层平板 POS 配方。`sh-*` 类名与 ai-shop `packages/ui/src/styles/base.css` 同名同义，`dn-*` 是餐饮专用件，对应 ai-shop《餐饮系统-UI组件与主题方案》里的 `Dining*` 候选组件。

## 设计语言

- **扁平色块。** 分层靠「面色 + tint 色块」，不用描边和阴影：页面 `sh-bg`，面板和菜品卡 `sh-surface`，中性块 `sh-faint`，选中和强调用 `sh-primary-tint`。
- **强调色只用于「可点」与「状态」。** 主色出现在主按钮、数量角标、选中态上，不做装饰。
- **一屏一个实心主操作。** 结单区的「结账」是唯一的 `sh-btn` 实心；并排的「送厨」降为 `sh-btn--soft`，「挂单」用 `sh-btn--muted`。
- **危险操作描边加墨字**（`sh-btn--danger`）；红色实心（`sh-btn--danger-solid`）只留给二次确认弹层里的最后一步。
- **用不了就不摆灰按钮。** 能隐藏就隐藏，不能隐藏就就地写一句原因（例如支付方式「离线不可用」）。
- **状态一律圆点加文字**（`dn-dot` + `sh-chip--*`），不只靠颜色。

## 布局：L1

```
┌──────────────── 系统栏 pos-sys-h 56 ────────────────────────┐
│ 门店 · 收银员      桌台 订单 会员 打印 网络 设置        时钟     │
├────────┬──────────────────────────────────┬────────────────┤
│ 分类区  │ 搜索 · 筛选 · 视图切换                │ 桌台订单条       │
│ rail-w │──────────────────────────────────│────────────────│
│ 108    │ 菜品宫格 auto-fill minmax(136,1fr)  │ 订单明细（滚动）  │
│        │                                  │────────────────│
│        │                                  │ 金额与操作区     │
└────────┴──────────────────────────────────┴────────────────┘
  间距 pos-gap 8 · 结单区 pos-order-w 360（应用宽 1000–1279 时 320）
```

- 分类在左、结单在右：右手拇指区落在「结账」上，与 Square、Clover 一致。
- 三栏各自滚动；系统栏、桌台订单条、金额与操作区固定。
- 应用宽度小于 1000px（竖屏、手机）时，分类改为顶部横排（`dn-rail--h`），结单区收成底部条，点开是抽屉。
- 门店可以在设置里把结单区换到左侧、分类区换到上方或右侧（L2–L6），组件不需要改。

## 功能区

| 区 | 内容 | 组件 |
|---|---|---|
| Z1 系统栏 | 门店、收银员；桌台、订单、会员、打印、网络、设置入口；时钟 | `DiningPosShell`、`DiningEntryButton` |
| Z2 桌台订单条 | 桌号、人数、时长、单号；堂食 / 外带 / 外卖；桌台操作菜单 | `DiningOrderDock` 顶部、`SegmentedControl` |
| Z3 分类区 | 分类与已点数量 | `DiningResourceRail` |
| Z4 菜单区 | 搜索、标签筛选、卡片 / 列表、菜品宫格 | `SearchInput`、`FilterChip`、`DiningMenuItem` |
| Z5 结单区 | 待下单、已下单、行操作、批量选择 | `DiningOrderDock`、`DiningQuantityStepper` |
| Z6 金额与操作区 | 会员、优惠券、抹零；金额明细；挂单、送厨、结账 | `DiningOrderDock` 底部、`Button` |
| Z7 浮层 | 规格、桌台、订单、会员、打印、网络、设置、支付、授权、确认 | `Dialog`、`Sheet`、`DiningCashPad`、`DiningAuthPad` |

## 颜色

- 中性面与 ai-shop 的 brand 皮肤同值：`sh-bg` #ECEDEF、`sh-surface` 白、`sh-ink` #17181A、`sh-sub` #63676E、`sh-faint` #E4E5E8。深色模式同样沿用 ai-shop 的深色面。
- 主色默认松绿 `sh-primary` #216857。另有靛蓝、暖橙、绛红、石墨、茶青、黛蓝六套皮肤（`skin-*` 参考值，含各自的 `primary-text` 与深色取值；业态推荐见 `docs/POS颜色规范.md`）。换皮肤只换 `sh-primary`、`sh-primary-tint`、`sh-primary-text`、`sh-on-primary` 四个值，不换中性面和状态色。
- ai-shop B 端默认的红色皮肤（`skin-brand`）在 POS 里会和危险色撞色，不建议作为收银默认。
- 状态色沿用 ai-shop 的 `sh-success`、`sh-warning`、`sh-danger` 作圆点和底色。它们作文字对比度不够，POS 新增了 `pos-success-text`、`pos-warning-text`、`pos-danger-text` 作标签文字，另加 `pos-info-tint`、`pos-info-text` 表示「就餐中、外带、转入」。
- 桌台状态：空闲 `sh-faint`、就餐中 `pos-info-tint`、待结账 `sh-warning-tint`、已预订 `sh-primary-tint`，并且每张卡都写状态文字。

## 字体与字号

- **Inter + Noto Sans SC**，都从 Google Fonts 加载；ai-shop 现有回退为苹方、鸿蒙黑体。
- 字号用 ai-shop 的八档（按 375pt 折算的 px）：`txt-caption` 12 → `txt-sub` 13 → `txt-body` 14 → `txt-strong` 15 → `txt-title` 17 → `txt-display` 20 → `txt-hero` 24 → `txt-mega` 30。最小 12px。
- 只用三种字重：700 只给价格和金额，600 给标题、菜名、按钮，其余 400。标签（`sh-chip`）从不加粗。
- 所有金额、数量、桌号加 `sh-num`（等宽数字）。

## 间距、圆角与尺寸

- 间距五档：`space-xs` 4、`space-sm` 8、`space-md` 14、`space-lg` 20、`space-xl` 32。三栏和宫格统一 `pos-gap` 8，属于紧凑档。
- 圆角五档：`radius-sm` 8、`radius-md` 12（选项、桌台卡、按键、分类项）、`radius-lg` 16（面板、菜品卡、弹层）、`radius-xl` 22（POS 首版不用）、`radius-full`（按钮、标签、筛选、步进器）。
- 触控：`pos-ctl` 44 是默认控件高；`pos-ctl-s` 36 的行内小按钮和筛选，在 b-app 里挂 `.sh-hit` 把点按区补到 44；结账、送厨、支付确认用 `pos-ctl-l` 52。

## 交互

- 无规格的菜单击加一份；有规格、套餐的打开规格弹窗；长按任意菜品看详情。
- 已下单的行只能催菜、赠送、退菜；赠送、退菜、整单折扣要主管授权（`DiningAuthPad`），退菜要选原因。
- 桌台操作：开台、改人数、换桌、并台、转菜、拆单、合单、打印预结单、清台。并台从桌台出发（被并桌显示「并入 A08」，结账后一起释放），合单从订单出发，不改桌台。
- 离线时：仍可点菜、送厨（进入待同步队列）、收现金和刷卡；微信、支付宝在支付方式里标「离线不可用」。
- 打印失败（例如缺纸）不阻塞送厨，提示条说明哪台失败，系统栏打印入口变成警告色，到打印面板重打。
- 动效：按下下沉 1px；弹窗 180ms 淡入放大，侧滑面板 220ms 从右滑入；尊重减少动效设置。

## 文案

- 中文优先，动词开头的 2–4 字按钮：`送厨`、`结账`、`开台`、`并台`、`确认收款`。不用「请」，不用感叹号。
- 状态用名词：`已下单`、`催菜中`、`已退`、`已沽清`、`并入 A08`。
- 提示条写结果：「已送厨 3 道菜」「A05 已并入 A08，一起结账」。错误写原因和下一步：「还有未结账的菜品，不能清台。先结账或退菜」。
- 金额统一两位小数（`¥203.50`）；菜单里整数价可以省略小数（`¥68`）。

## 图标

- 线性图标，线宽 1.75，18px（行内 15px）。原型用 Lucide；迁入 b-app 时改用 `sh-icon`（内联 SVG 转 CSS mask，颜色跟随主题），缺的图标补进 `packages/shared/src/design/icons.ts`。
- 不用 emoji。只有图标的按钮必须有 `aria-label`；挂单按钮在结单区只显示图标，必须带 `aria-label="挂单"`。

## 迁入 b-app

- `sh-*` 类直接使用 ai-shop 现有定义；POS 的平板尺寸差异（按钮改为行内 44px 高）放在 `pos-v2` 作用域里，不覆盖全局 `sh` 主题。
- `dn-*` 类与 `pos-*` token 进入餐饮设计令牌模块，经组件 API 评审后落为 `Dining*` 组件。迁移计划见 ai-shop `docs/technical/TDD-餐饮POS点餐收银界面.md`。
- 可交互原型：POS 点餐工作台（`prototypes/pos-workbench.html`），方案 1 + L1 即本规范。
