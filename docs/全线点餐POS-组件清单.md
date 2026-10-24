# 全线点餐 POS · 组件清单（组件库第一步）

> 状态：已被《[布局与组件清单](全线点餐POS-布局与组件清单.md)》取代（按现有原型代码采集）；本文按框架草图写，仅作组件库命名参考 · 2026-10-23
> 来源：从原型框架草图 `bak/prototypes/pos-frame.html`（27 个功能区，见[功能区清单](../bak/docs/全线点餐POS-功能区清单.md)）逐区拆出；名称沿用 `pos-ds` 已有组件（`Dining*` 和通用名），新增的按同一规则命名。
> 这一步只列清单：有哪些组件、各有哪些变体和状态、用在哪个功能区。API 和视觉配方下一步再写。

**现状列：**
- **ds**：`pos-ds/project/components` 已有（pos-ds 已移到 `bak/pos-ds`）。
- **app**：`bak/pos-app/src/components` 已有 Vue 实现。
- **框架**：框架草图里已经画出，但还没有成为组件。
- **新增**：框架里只有占位，或者还没有出现。

**优先级：** P0 首版必须有；P1 首版应有；P2 可后置。

## 总览

| 类 | 数量 | 组件 |
|---|---|---|
| 一 布局 | 4 | DiningPosShell · Panel · DetailPanel · SectionHeader |
| 二 导航 | 6 | DiningSysBar · WorkbenchTabs · DiningEntryButton · DiningResourceRail · SegmentedControl · ChipTabs |
| 三 菜单 | 7 | DiningMenuItem · DiningMenuGrid · SearchInput · FilterChip · OptionPicker · ActionMenu · DishDetailPopover |
| 四 按钮 | 6 | Button · IconButton · DiningQuantityStepper · DiningPayMethod · ColorSwatch · LayoutPicker |
| 五 数据展示 | 10 | DiningStatusLabel · StatusDot · CountBadge · ColorMark · KeyValue · AmountDisplay · StatTile · DiningTableTile · DiningOrderLine · DiningOrderRow |
| 六 提示与反馈 | 8 | Toast · Banner · InlineNotice · ConfirmDialog · DiningAuthPad · ResultView · EmptyState · Skeleton |
| 七 浮层 | 4 | Dialog · Sheet · Popover · Scrim |
| 八 表单与输入 | 6 | TextInput · NumberInput · Select · Checkbox · DiningCashPad · Keypad |
| 合计 | 51 | |

## 一、布局

| 组件 | 用途 | 变体 / 状态 | 用在 | 现状 | 优先级 |
|---|---|---|---|---|---|
| DiningPosShell | 应用外壳：系统栏 + 三栏网格；响应布局开关和断点 | 布局 L1–L6；有无导航区（结账、班次没有）；宽度 ≥1280 / 1000–1279 / <1000 | 全部 | ds · app（PosShell） | P0 |
| Panel | 面板容器，扁平色块，无阴影 | 普通 / 带内边距 / 可滚动 | Z3 Z4 Z5 | 框架（`.panel`） | P0 |
| DetailPanel | 详情区三段式：头、可滚动的主体、固定的底部 | 底部有无金额区；底部操作排 | M1+M5–M7、T3、O3、P1、S2 | 框架（`.detail`）；点餐页这一份即 ds 的 DiningOrderDock | P0 |
| SectionHeader | 区内小标题，可带计数和右侧操作 | 有无右侧操作（「批量选择」） | M5、T3、O3 | 框架（`.lines h4`） | P1 |

## 二、导航

| 组件 | 用途 | 变体 / 状态 | 用在 | 现状 | 优先级 |
|---|---|---|---|---|---|
| DiningSysBar | 系统栏：身份与班次、工作台切换、入口、时钟 | — | G1–G5 | app（SysBar）；ds 并在 DiningPosShell 里 | P0 |
| WorkbenchTabs | 工作台切换（桌台 / 点餐 / 结账 / 订单 / 班次） | 当前项墨色实心 | G2 | 框架（`.g2`） | P0 |
| DiningEntryButton | 系统栏入口按钮，带状态点或计数 | 正常 / 警告点 / 危险点 / 计数徽标；只有图标时必须有 aria-label | G3 G4 G5 | ds（框架 `.ent`） | P0 |
| DiningResourceRail | 导航栏：分类、桌区、订单状态 | 竖排 96px / 横排 48px（随布局）；项可带色块小方块、计数、已点圆点；可分组（「桌区」「状态」） | M2 T1 O1 | ds · app（NavRail） | P0 |
| SegmentedControl | 分段切换 | 2–4 段；小号（工具栏内）/ 常规 | M3（有图 / 无图、卡片 / 列表）、X3（换桌 / 并台 / 转菜 / 合单）、设置面板 | ds · app（PosSeg） | P0 |
| ChipTabs | 胶囊页签，用于面板内分页 | 当前项墨色实心 | X4（外卖 / 会员 / 打印 / 网络 / 待办 / 设置） | 框架（`.sheet .tabs`） | P1 |

## 三、菜单

「菜单」包括两类：菜品菜单（菜品卡、宫格、筛选）和操作菜单（「更多」下拉、行操作菜单）。

| 组件 | 用途 | 变体 / 状态 | 用在 | 现状 | 优先级 |
|---|---|---|---|---|---|
| DiningMenuItem | 菜品卡 | **无图**：色块填色（色块方案）/ 中性底（主题色方案）；**有图**：上图 4:3、下文字；**有图模式下没图**：分类色块加菜名前两字。状态：未点 / 已点（份数角标、选中圈）/ 多规格 / 限量（剩 N）/ 已沽清（灰、图去色）。列表行变体（编号、名称、价格一行） | M4 | ds · app（DishCard）；有图、列表行为新增 | P0 |
| DiningMenuGrid | 菜品宫格 | auto-fill，最小列宽 140（无图）/ 150（有图）；跟随密度 | M4 | 框架（`.grid`） | P0 |
| SearchInput | 搜索：菜名、拼音首字母、编号；单号、桌号 | 有无清除按钮；有无扫码按钮 | M3 O2 | ds | P0 |
| FilterChip | 可选中的胶囊筛选 | 选中墨色实心；可带计数 | M3（全部 / 辣 / 素食 / 特价）、P2 快捷金额、X1 选项 | ds（框架 `.chip`） | P0 |
| OptionPicker | 规格、做法、加料、原因的单选或多选 | 单选 / 多选；必选标记；加价文字；不可选 | X1 X2 | ds | P0 |
| ActionMenu | 「更多」下拉菜单、订单行的操作菜单 | 普通项 / 危险项 / 需授权项（带锁标记）/ 不可用项（写原因） | M1「更多」、M5 行操作（催、赠、退、转）、T3 | 新增（框架里只有占位按钮） | P0 |
| DishDetailPopover | 长按菜品看详情：大图、描述、过敏原、库存 | 有图 / 无图 | M4 | 新增 | P2 |

## 四、按钮

| 组件 | 用途 | 变体 / 状态 | 用在 | 现状 | 优先级 |
|---|---|---|---|---|---|
| Button | 所有文字按钮 | **主**（实心，一屏只有一个：结账、确认收款、开班）· **soft**（送厨、开台）· **muted**（挂单、预结、取消）· **危险 soft**（退菜、反结账）· **危险实心**（只在确认弹层最后一步）。尺寸 36 / 44 / 52 / 56；全宽；带数量（「送厨 · 2」）；按下下沉 1px；用不了时隐藏，不放灰按钮 | 全部 | ds · app（PosBtn） | P0 |
| IconButton | 只有图标的按钮 | 常规 / 小号（补热区到 44）；必须有 aria-label | 挂单图标、关闭、删除行 | ds | P0 |
| DiningQuantityStepper | 数量步进器 | 44 / 36；减到 0 时询问删除；只读（已下单行显示 ×N） | M5 X1、改人数 | ds · app（PosStepper） | P0 |
| DiningPayMethod | 支付方式块 | 色块（色块方案）/ 中性；选中圈；离线不可用（写原因） | P2 | 框架（`.pay`） | P0 |
| ColorSwatch | 主题色 / 色块选择 | 选中外圈 | 设置面板（G5） | 框架（`.sw`） | P1 |
| LayoutPicker | 布局 L1–L6 缩略图选择 | 选中描边 | 设置面板（G5） | 框架（`.layg`） | P1 |

## 五、数据展示

| 组件 | 用途 | 变体 / 状态 | 用在 | 现状 | 优先级 |
|---|---|---|---|---|---|
| DiningStatusLabel | 状态标签，一律带文字 | 中性 / 主题 / 辅助 / 警告 / 危险 / 成功 / 色块（k-*）；放在色块上时白底 | 全部 | ds（框架 `.tag`） | P0 |
| StatusDot | 状态圆点，旁边没字时改用文字色 | 正常 / 警告 / 危险 | G3、自检列表 | 框架（`.dot`） | P0 |
| CountBadge | 计数徽标、份数角标 | 危险计数（待办、外卖）/ 墨色份数 | G4、M4 | 框架（`.cnt`、`.q`） | P0 |
| ColorMark | 10px 色块小方块，标出分类、状态、支付方式 | 11 个色块 | M2 M5 T1 O1 P1 S2 | 框架（`.ksq`） | P1 |
| KeyValue | 键值列表：金额明细、桌台信息、会员信息 | 普通 / 等宽数字 / 负数用次要色 | M6 T3 O3 P1 S2、会员面板 | 框架（`.kv`） | P0 |
| AmountDisplay | 应收、实收的大号金额 | 30px / 20px；还差多少时用危险色加文字 | M6 P1 P2 | 框架（`.big`） | P0 |
| StatTile | 统计数字块 | 带色块小方块 / 不带 | T2 顶部、S2、店长端 | 框架（`.stat`） | P1 |
| DiningTableTile | 桌台卡 | 空闲 / 用餐中 / 待结账 / 已预订 / 并入他桌；当前选中；人数、时长、金额 | T2 X3 | ds · app（TableCard） | P0 |
| DiningOrderLine | 订单行 | 待下单（步进器）/ 已下单 / 制作中 / 已上 / 催菜中 / 已退（删除线）/ 赠送；可勾选（批量）；做法、备注副行 | M5 T3 O3 P1 | 框架（`.ln`）；ds 并在 DiningOrderDock 里，需单独拆出 | P0 |
| DiningOrderRow | 订单列表行 | 当前选中；渠道标签；状态标签 | O2 | 框架（`.orow`） | P0 |

## 六、提示与反馈

| 组件 | 用途 | 变体 / 状态 | 用在 | 现状 | 优先级 |
|---|---|---|---|---|---|
| Toast | 操作结果的短提示，写结果：「已送厨 3 道菜」 | 普通 / 成功 / 失败（带「重试」）；2–4 秒自动消失；失败的不自动消失 | 全部 | ds（框架未用） | P0 |
| Banner | 全局横幅，挂在系统栏下面，直到问题解决 | 离线（写待同步数）/ 打印异常（写哪台）/ 未开班 | 外壳 | 新增 | P0 |
| InlineNotice | 区内提示，写原因和下一步 | 信息 / 警告 / 危险 | P2「还差 ¥23.50」、S1 自检异常、清台被拒 | 新增（框架里是纯文字） | P0 |
| ConfirmDialog | 二次确认 | 普通 / 危险（最后一步危险实心）；写清后果 | 清空、清台、退菜、反结账 | ds（Dialog） | P0 |
| DiningAuthPad | 主管授权：原因 + 授权码 | 输入中 / 错误（抖动并清空）/ 通过 | X2 | ds | P0 |
| ResultView | 结果页：收款成功或失败 | 成功（金额、找零、打印小票）/ 失败（原因、换一种方式） | P3 | 框架（P3 弹窗） | P0 |
| EmptyState | 空状态：图标 + 一句话 + 可选操作 | 空订单 / 无搜索结果 / 无挂起单 / 无待办 | M5 M4 O2 X4 | ds 有 sh-empty 规范，框架未用 | P1 |
| Skeleton | 加载中的占位、支付等待 | 宫格骨架 / 列表骨架 / 支付中（转圈 + 「等待顾客付款」） | M4 O2 P2 | 新增 | P1 |

## 七、浮层

| 组件 | 用途 | 变体 / 状态 | 用在 | 现状 | 优先级 |
|---|---|---|---|---|---|
| Dialog | 居中弹窗 | 560 / 760 宽；有无底部操作排；全屏（开班） | X1 X2 X3 P3 S1 | ds | P0 |
| Sheet | 侧滑面板 | 480–640 宽；从右滑入；内部带 ChipTabs | X4（外卖、会员、打印、网络、待办、设置） | ds · app（PosSheet、SettingsSheet） | P0 |
| Popover | 锚定在元素旁边的小浮层 | 下拉 / 上拉；承载 ActionMenu、DishDetailPopover | M1 M5 M4 | 新增 | P0 |
| Scrim | 遮罩 | 点击关闭 / 不可关闭（授权、支付中） | 所有浮层 | 框架（`.scrim`） | P0 |

## 八、表单与输入

| 组件 | 用途 | 变体 / 状态 | 用在 | 现状 | 优先级 |
|---|---|---|---|---|---|
| TextInput | 文字输入：备注、临时菜名 | 有无快捷短语；错误态写原因 | X1 整单备注、临时菜 | 框架（`.input`） | P0 |
| NumberInput | 数字输入：备用金、实盘张数、临时菜价格 | 金额 / 整数；接 Keypad | S1 S2 | 框架（`.input`） | P0 |
| Select | 下拉选择：收银台、服务员 | 常规；用 Popover 展开 | S1 开台 | 新增（框架只有「▾」占位） | P1 |
| Checkbox | 勾选：批量选择订单行、合单选订单 | 未选 / 已选 / 半选 | M5 X3 | 新增 | P1 |
| DiningCashPad | 收款键盘：金额、快捷金额、找零 | 现金 / 组合支付分摊 | P2 | ds | P0 |
| Keypad | 通用数字键盘 | 3 列（授权码）/ 4 列（金额） | X2 P2 S2 | 框架（`.keys`）；ds 的 CashPad、AuthPad 共用 | P0 |

## 八b、v7 补充（2026-10-23，按原型合规评审）

原型先做、组件方案后补的组件，配方见 [UI 组件方案](全线点餐POS-UI组件方案.md) 第三节。

| 组件 | 用途 | 变体 / 状态 | 类名 | 优先级 |
|---|---|---|---|---|
| Panel | 工作台三栏里的面板：头 / 滚动主体 / 底 | 默认 · `--page`（设置页）· `--fill`（主体垂直居中） | `sh-panel` | P0 |
| Card | 独立内容块（从 Panel 拆出） | — | `sh-card` | P0 |
| FieldLabel | 表单字段标签（区块标题改用 SectionHeader `dn-sec`） | — | `sh-gt` | P0 |
| Toolbar | 面板头里的搜索 + 筛选 + 分段 | 换行 | `sh-toolbar` | P0 |
| SubNav | 设置工作台二级菜单 | 选中 · 小组标题 | `sh-subnav` | P1 |
| BilingualName | 中英两行名 | 双语 / 单语 | `sh-biname` | P0 |
| DataTable | 设置页表格 | 列定义 `--cols` · 右对齐列 · 缩略图 | `sh-table` | P1 |
| TextLink | 文字操作（清空、批量选择） | — | `sh-link` + `sh-hit` | P0 |
| CountRow | 钱箱实盘：面额 + 张数 | — | `dn-count` | P1 |
| StatusTag | 业务状态：图标 + 字（取代 DiningStatusLabel 的状态用法） | chip / dot × 9 组 | `st-tag` | P0 |
| StatusIcon | 只放图标的状态（取代 StatusDot） | 9 组 | `st-ic` | P0 |
| OrderMemo（v8） | 整单备注条，点开编辑 | 有 / 无备注 | `dn-memo` | P1 |
| DiningOrderLine · 批量（v8） | 批量选择订单行 | 未选 / 已选；已退不可选 | `dn-oline--ck` | P1 |
| Skeleton · 菜品卡（v8） | 菜单加载占位 | 有图 / 无图 | `dn-dish--skel` + `sh-skel--*` | P1 |

## 九、不进组件库的

这些只为评审原型服务：原型控制条、功能区图例、功能区标注（橙色虚线）、设备外框。

## 十、下一步

1. 按 P0 先做组件预览页（类似 `bak/pos-ds/project/components/*/preview.html`），每个组件列出全部变体和状态，色块方案、主题色方案和浅色、深色都能切换。
2. 给每个组件写 API：属性、事件、插槽，对齐 `bak/pos-app` 现有的 Vue 组件。
3. 框架草图里的 `.ln`、`.orow`、`.pay` 等改成引用组件，保证框架和组件库是同一套代码。
