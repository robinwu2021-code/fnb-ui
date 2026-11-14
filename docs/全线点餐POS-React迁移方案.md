# 全线点餐 POS · React 迁移方案

> 状态：第 1、2、3 步已完成 · 2026-10-24 · 组件展示页 http://127.0.0.1:5178/#components · 工程在 `prototypes/pos-react/`（独立 `package.json`，`npm run dev` 起在 http://127.0.0.1:5178）
> 依据：《[设计规范（布局·形状·色系）](全线点餐POS-设计规范（布局·形状·色系）.md)》为准；HTML 原型 `prototypes/bapp/pos-ui.html` 保留作对照，不改、不删。
> 对照方法：原型用 `python3 -m http.server 5179` 在 `prototypes/bapp` 下起一个静态服务，和 React 版并排看 1280 × 800。

## 一、技术选型

| 项 | 选择 | 取舍 |
|---|---|---|
| 构建 | Vite 6 + React 18 + TypeScript | 纯前端原型，不需要 SSR；Vite 启动快，HMR 对比原型方便 |
| 原子样式 | UnoCSS（`presetWind` + `presetIcons` + variant group） | 主题只引用 CSS 变量（`--c-*`、`--rc` 等），组件里不出现色值；`presetIcons` 装了 lucide 集，供 class 写法（`i-lucide-x`）备用 |
| 组件库 | shadcn/ui 的做法：Radix 原语 + 组件源码放进 `src/components/ui/`（手写，不用 CLI） | shadcn CLI 依赖 Tailwind 配置与 `components.json`，这里直接按它的结构手写同名组件，样式全走本规范的令牌。Radix 负责可访问性（焦点圈、键盘、`aria-*`、Portal） |
| `unocss-preset-shadcn` | **不用** | 它的主题要求 `--primary: h s l` 这类 HSL 三元组并带一套自己的 `--radius`、动画；本项目已有完整的角色令牌（`pos-colors.css`）和两套圆角，接它反而要再写一层映射。需要的少量动画（遮罩淡入、侧滑进入）写在 `base.css` |
| 图标 | `lucide-react`（按 kebab-case 名动态取，`<Ico name="chef-hat">`） | 原型和 `pos-states.js` 里的图标名可以原样沿用；线宽统一 1.75 |
| 状态 | zustand（一个 store，对应原型的 `S` + `LINES / TABLES / ORDERS`） | 原型是全局 `S` + `ACT[名]` 分发；store 保留同一套动作名（`act('add:宫保鸡丁')`），快捷操作、菜单项的配置字符串不用改 |
| Toast | Sonner（`unstyled`，用令牌写成墨底白字） | 挂在设备容器里，`position: fixed` 相对被 `transform` 的设备定位，正好只在设备内 |
| 不选 Ant Design / Arco | — | 自带视觉体系和 CSS-in-JS，和 UnoCSS 及本规范的色块形态冲突 |

## 二、工程结构

```
prototypes/pos-react/
  index.html                 <html data-skin data-tone data-frame data-btn data-density>（和原型同一套属性）
  uno.config.ts              主题：颜色 = --c-* 角色；圆角 rc/rt/rb/rk；字号 7 档；shortcut h-ctl 等
  scripts/sync-tokens.mjs    从 bapp 同步 pos-colors.css → src/styles/pos-colors.css、pos-states.js → src/data/pos-states.json
  src/styles/base.css        形状 / 密度 / 字号变量、全局基线、焦点环、热区 .hit、骨架、浮层动画
  src/styles/pos-colors.css  （生成物，不手改）
  src/data/sample.ts         示例数据（DISHES / LINES / TABLES / COUPONS / PLATS / PVOUCHERS / MEMBER / QUICK）
  src/data/pos-states.json   19 实体 112 状态（生成物）
  src/lib/                   cn（clsx + tailwind-merge）· icon（Lucide 动态）· states（stv / stName）· pricing（totals）· money
  src/i18n/dict.ts           词表 key 制：{ 'tab.tables': ['桌台', 'Tables'] }，带 {n} 占位
  src/store/                 index（状态 + set/act）· actions（ACT 注册表）
  src/components/ui/         button · toggle-group(Seg) · toggle(Filter) · radio-cards(Option) · tabs · switch · checkbox · input · dialog · sheet · dropdown-menu · panel · badge · description-list · stepper · status-tile · keypad · feedback
  src/layout/                modules（MODULES 布局表 + 网格）· device（1280×800 舞台 + Portal 容器）· sysbar · rail · workbench
  src/modules/tables/        TablesWB（桌台）
  src/modules/order/         OrderWB（点餐 + 结单区 Dock）· DishCard · OrderLine · AdjRows
  src/modules/stubs/         未迁移模块的占位
  src/overlays/              Overlays（规格 x1、授权 x2、选桌 x3、清台提示、挂单、拼桌、调价、备注）· MoreMenu · SheetPanel
  src/dev/                   DevBar（页首工作台切换）· StyleControls（显示开关，侧滑「设置」页签也用它）
```

改颜色、改状态：先改 `prototypes/bapp/tools/gen-colors.py`，生成后在本工程跑 `npm run sync:tokens`。

## 三、令牌映射

| 规范 | 原型变量 | React / Uno 写法 |
|---|---|---|
| 画布 / 面板 | `--sh-bg` / `--sh-card` | `bg-canvas` / `bg-surface`（← `--c-canvas` / `--c-surface`） |
| 浅块 / 深一档 | `--sh-wash` / `--sh-wash-strong` | `bg-wash` / `bg-wash-strong` |
| 已点 | `--sh-pick` | `bg-pick` |
| 选中 | `--sh-sel-bg` / `--sh-sel-fg` | shortcut `sel` = `bg-sel-bg text-sel-fg font-600` |
| 字 | `--sh-ink` / `--sh-sub` / `--sh-disabled` | `text-ink` / `text-sub` / `text-placeholder` |
| 主色 | `--sh-primary` 系列 | `bg-primary` `hover:bg-primary-hover` `bg-primary-tint` `text-primary-text` `text-primary-on` |
| 状态色 | `--sh-danger-*` 等 | `bg-danger-tint text-danger-text` · `bg-warning-tint` · `text-success-text` |
| 业务状态组 | `stv(g)` 写 `--d --t --b --bk --bks` | `stv(group)` 返回 style；类 `text-st-t` `bg-st-b` `bg-st-bk` `bg-st-bks` |
| 优惠金额 | `--sh-saving` | `text-saving` |
| 反色面 | `--sh-ink` 底 | `bg-inverse text-on-inverse` |
| 圆角 | `--rc --rt --rb --rk` | `rounded-rc` `rounded-rt` `rounded-rb` `rounded-rk`（内嵌 `rounded-rt-in`、`rounded-rk-in`、`rounded-chip`） |
| 控件高 | `--h-sm --h --h-lg` | `h-ctl-sm` `h-ctl` `h-ctl-lg`（紧凑 36/44/52 · 标准 40/48/56，由 `data-density` 切） |
| 间距 | `--gap --pad --px` | `gap-gap` `p-pad` `px-pad` `px-px` |
| 字号 | `--fs-xs … --fs-2xl` | `text-xs` `text-sm` `text-base` `text-md` `text-lg` `text-xl` `text-2xl` |
| 热区 | `.sh-hit::before` | 类 `hit`（`base.css`） |
| 焦点环 | `:focus-visible` 2px 主色外扩 2px | `base.css` 全局，所有可点元素都是 `button`，不再补 `tabindex` |
| 皮肤 / 色系 / 形状 / 密度 | `<html data-*>` | 同一套属性，`App.tsx` 从 store 同步到 `<html>` |

## 四、组件映射

| 原型类 | 组件 | 文件 | 说明 |
|---|---|---|---|
| `sh-btn` | `Button` | `ui/button.tsx` | cva：variant `default`（墨实心）/ `soft` / `muted` / `danger`（浅红底红字）/ `danger-solid` / `link`；size `sm` / `md` / `lg` / `icon` / `icon-sm`；`block`。`disabled` 即原型 `--off` |
| `sh-seg` | `Seg` | `ui/toggle-group.tsx` | Radix ToggleGroup single；浅灰轨道 + 当前段 gray-200 |
| `sh-filter` | `Filter` + `FilterCount` | `ui/toggle.tsx` | Radix Toggle |
| `sh-option` / `dn-pay` / `dn-plan` / `dn-coupon` / `dn-layout` | `OptionGroup` + `Option`（多选 `OptionMulti`） | `ui/radio-cards.tsx` | Radix RadioGroup；一条规则：未选 wash · 当前 gray-200 + 墨字加粗 · 禁用置灰写原因 |
| `sh-tabs` | `Tabs` 系列 | `ui/tabs.tsx` | 2px 底线 |
| `sh-switch` / `sh-check` | `Switch` / `Checkbox`（`CheckMark` 只做样子） | `ui/switch.tsx` `ui/checkbox.tsx` | 关闭态 gray-500 轨道 / gray-150 实心块 |
| `sh-input` / `sh-search` | `Input` / `Textarea` / `Search` | `ui/input.tsx` | |
| `sh-dialog` | `Dialog` + `DialogTitle / DialogDesc / DialogActs` | `ui/dialog.tsx` | Radix Dialog，Portal 到设备容器；≤560，`wide` 760 |
| `sh-sheet` | `Sheet` | `ui/sheet.tsx` | 右侧 480 |
| `sh-menu` | `Menu` 系列 | `ui/dropdown-menu.tsx` | Radix DropdownMenu，反色面 |
| `sh-toast` | `Toaster` + `notify()` | `ui/feedback.tsx` · `store/actions.ts` | Sonner |
| `sh-cells` | `Cells` / `Cell` | `ui/description-list.tsx` | |
| `st-tag` / `st-ic` | `StatusTag` / `StatusIcon` | `ui/badge.tsx` | 形式按状态组：chip 组浅底、dot 组不铺底 |
| `sh-chip` / `sh-badge` / `dn-qty` / `dn-mark` | `Chip` / `Badge` / `Qty` / `CatMark` | `ui/badge.tsx` | |
| `dn-table` / `dn-ocard` | `StatusTile` | `ui/status-tile.tsx` | 整块状态底 `--bk / --bks`；当前 / 选中 gray-200 |
| `sh-panel` + `panel / ledger / side` | `Panel`（`ledger` 变体）+ `Block` + `DueFoot` + `Acts` + `Stat` | `ui/panel.tsx` | hd / bd / ft 三段，只有 bd 滚动 |
| `sh-stepper` | `Stepper` | `ui/stepper.tsx` | |
| `dn-keys` / `dn-pins` | `Keypad` / `Pins` | `ui/keypad.tsx` | |
| `sh-empty` / `sh-note` / `dn-banner` / `sh-skel` | `Empty` / `Note` / `Banner` / `Skeleton` | `ui/feedback.tsx` | |
| `dn-rail` | `Rail` | `layout/rail.tsx` | 导航在上时横排 |
| `dn-sysbar` | `SysBar` | `layout/sysbar.tsx` | 6 页签 + 图标入口 |
| `dn-steps` | `Steps` | `ui/steps.tsx` | 已过打勾、当前 gray-200、步间 8px 短线 |
| `dn-pay--row` | `PayGroup` + `PayItem` | `ui/pay-row.tsx` | Radix RadioGroup；品牌色图标（§3.4 例外）；离线 / 储值不可用置灰写原因 |
| `dn-promo`（结账左栏） | `PromoRow` | `ui/promo-row.tsx` | ✓ 已用 · ○ 可选 · ⊘ 置灰写原因 |
| `dn-coupon` / `dn-plat` | `CouponCard` / `PlatMark` | `ui/coupon.tsx` | 本店券、平台券、核销记录共用 |
| `dn-result` / `sh-spin` / `dn-amount` | `Result` / `Spinner` / `Amount` | `ui/result.tsx` | |
| `dn-orow` / `dn-ocard` | 订单行（`button`）/ `StatusTile` | `modules/orders/OrdersWB.tsx` | 列表行当前 gray-100；卡片整块状态底 |
| `sh-table` | `Table` + `Tr` / `Td` / `Th` / `Nm2` | `ui/table.tsx` | grid 模板列宽；不画行线；当前行 gray-100 |
| `dn-kpi` / `dn-vbars` / `dn-hbars` | `Kpi` / `Kpis` / `VBars` / `HBars` | `ui/chart.tsx` | 单一系列只用主色，不画网格 |
| `dn-plan` / `dn-swatch` / `dn-mdpic` | 内联（Stored / Cats / Dishes） | `modules/menu` `modules/members` | 方案卡 = 浅灰块 + 开关；色样 = 圆点 + 墨色外圈 |

## 五、分步完成情况

### 第 1 步 · 脚手架 + 令牌 + 壳 + 桌台 + 点餐 —— 已完成

- 工程可跑：`npm run dev`、`npm run build`（tsc 无错）。
- 令牌：`pos-colors.css` 原样同步；皮肤 7 种、色系黑白灰 / 多色、框架圆角 5 档、按钮形状 5 档、密度 2 档全部可切。
- 壳：系统栏（6 页签 + 5 图标入口 + 时间）、离线横幅、`MODULES` 布局表、L1–L6 可切（导航左 / 上 / 右 × 详情右 / 左）、`Panel` 三段。
- 桌台：桌区 / 状态导航（带计数与状态图标）、统计行、桌台宫格（11 态状态块）、桌台详情（P2：分组列表 · 已点清单 · 白色窄底 · 换桌 / 并台 / 预结 / 清台 / 加菜 / 去结账 / 开台 / 启用）、选桌浮层、清台提示。
- 点餐：分类导航（双语 + 已点圆点）、工具条（搜索 · 筛选 · 有图 / 无图 · 上下 / 左右）、菜品卡三种（图文上下、图文左右、纯文字；沽清 / 限时 / 低库存）、结单区 v4（头 · 快捷操作 5 个 · 待下单白卡可改数量 · 已下单可点开行内操作 · 批量 / 拆单勾选 · 费用与优惠 · 白色窄底 应收 + 送厨 + 结账）；浮层：规格、挂单、拼桌、调价（三种方式 + 授权门槛）、整单备注、更多菜单、主管授权、侧滑面板（外卖 / 会员 / 打印 / 网络 / 设置）。
- 中英文：词表 key 制，`useT()`；菜名、分类、状态名按语言取数据字段。
- 对照：桌台、点餐在 1280 × 800 下与原型并排比过，布局、颜色、字号一致。

**对照时发现的原型问题（原型未改，React 版已按规范处理）**
1. 黑白灰色系下分类小方块 `dn-mark` 是 `display:none`，但结单区订单行的网格第一列仍固定 10px，菜名落进第一列被截成一个字（原型 `pos-ui.html` 结单区可见「宫」「米」「酸」）。React 版在黑白灰下不渲染小方块、也不留列。
2. 原型可点元素多为 `span` + `tabindex`，React 版一律 `button`（Radix 组件自带 `role`、`aria-*`）。

**本步未做 / 简化**
- 搜索框只有外观，不过滤；筛选「辣 / 素食 / 特价」只有选中态。
- 字体切换（Inter / Roboto / Noto …）没搬，只用 Inter + Noto Sans SC。
- 「加载中（骨架）」开关没接上页首（`DishSkeleton` 组件已有）。
- 页首「模拟扫码」只接了会员码（侧滑会员页「识别为王女士」）。

### 第 2 步 · 结账 + 订单 —— 已完成

- **结账**（W2，账单 440 在左）：左栏 P2 块色清单（菜品可展开 · 会员识别 / 会员卡 · 费用与优惠 9 行，置灰原因与原型一致 · 白色窄底「应付」）；右栏步骤条 `Steps`（账单 → 收款方式 → 收款 → 完成，当前步由状态推出）→ 待收 / 应付 / 已收 → 收款方式 6 项（微信、支付宝、现金、银行卡、储值、组合；离线时扫码置灰，储值按「先识别会员 / 余额不足 / 不能组合」置灰）→ 操作区：现金（实收大字 · 找零 / 还差 · 快捷金额 · 4 列键盘，确认键跨两行）· 扫码 / 刷卡（等待 + 设备状态 + 手动确认）· 储值（扣款明细 → 推送等待 → 模拟确认）· 组合（已付笔数可撤销 · 这一笔的方式 · 现金块收这一笔）。完成 → 收款成功弹窗（找零、各笔、优惠、储值余额 → 下一桌：桌台变待清台，平台券写入核销记录）；支付失败弹窗 `p4`。
- **优惠浮层**：选券（侧滑，满减 / 兑换 / 折扣券按 `cpWhy` 置灰）· 整单折扣 / 免单（金额 + 原因，超 ¥20 或免单走授权）· 平台券验券（侧滑：平台授权状态一排 · 模拟扫码 4 种 · 离线警告 / 已用 / 未授权 / 套餐券缺菜 / 面额超出 · 本次张数 ≤3 · 本单已用列表可移除）。`totals()` 的互斥规则原样搬来：平台券 → 活动、本店券、人工折扣、抹零全部让位。
- **订单**（W1）：时段导航 · 搜索 + 渠道 Seg + 列表 / 卡片 Seg · 状态筛选（带状态图标，要处理的计数红字）· 列表行（状态 dot · 单号 · 桌 / 渠道 · 件数时间 · 金额）/ 卡片（`StatusTile` 整块状态底，要处理 60%、已关闭浅灰）· 详情（菜品 · 支付记录 · 订单金额 · 合单 / 补打 / 反结账 / 取单）。
- **平台券撤销核销**（按平台券方案 §七）：支付记录逐张列出平台券（平台标识 · 核销时间 · 操作人 · 张数 · 金额 · 已核销 / 已退回）；本人 24 小时内 →「撤销核销」选原因直接撤；他人核销 →「撤销核销 · 需授权」加主管授权码键盘；超 24 小时 → 不可撤，提示去平台商家后台退款。撤销后券退回、订单变「部分支付」、支付记录出「待补收 ¥X」。示例数据：#1020 本人、#1021 王芳（需授权）、#1019 超 24 小时。
- 对照：结账（未选方式 / 现金 / 组合 / 已收齐）、订单（列表 / 卡片 / 详情 / 撤销授权弹窗 / 撤销后）与原型并排比过，布局、颜色、字号一致。

**本步未做 / 简化**
- 扫码收款的「模拟扫码 › 付款码」没接页首（可用「手动确认到账」走通）；「反结账」「合单」复用授权 / 选桌浮层，不改状态。
- 新建会员 `mnew` 只出 Toast（第 3 步会员模块再做）。
- 订单搜索只有外观；时段导航只切选中态，不过滤。

### 第 3 步 · 其余模块 + 中英文 + 组件展示页 —— 已完成

- **菜品管理**（W1，`modules/menu/MenuWB.tsx`）：菜品（搜索 · 新建 · 五个筛选 · 分类 Seg · `Table` 列表：缩略图 / 双语名 + 标记图标 / 分类 / 价格 / 售卖状态 / 四渠道同步图标 / 上架开关；详情：图片位 · 字段 · 规格组 · 标记可切 · 今日库存 / 限时 / 沽清开关 · 四渠道同步状态与失败原因 · 下架 / 保存）· 分类（拖把手 · 中英名 · 菜品数 · 平台类目映射 · 显示开关；详情含打印档口、类目、分类色色样）· 规格（规格组表 · 选项加价 · 必选 / 多选 / 同步开关）· 套餐（`StatusTile` 卡 · 分组与换菜开关）· 渠道同步（三渠道状态卡 · 待处理表「去修改 / 同步」· 全部同步 1.2 秒后待同步变已同步 · 同步记录）。改标记 / 保存 → 美团、饿了么变待同步。
- **会员与营销**（W1，`modules/members/MembersWB.tsx`）：会员（手机号查找 · 分层筛选含线索 · 表格 · 详情：线索提示 / 三数卡 / 标签编辑 / 收支记录 Seg）· 标签（自定义标签列表 + 系统分层只读；详情列成员）· 储值（方案卡开关 · 今日储值单表 · 办理储值入口）· 活动（状态筛选 · `StatusTile` 卡 · 详情按状态给 暂停 / 结束 / 恢复 / 发布）· 优惠券（券表 · 四种券状态 · 右栏核销券：模拟扫到 → 预览 → 红实心「确认核销」→ 已核销）· 积分（只读说明）。浮层：新建会员（仅保存 / 保存并储值）· 办理储值（选方案 → 实收方式 → 处理中 → 已到账，余额同步到结账页储值）· 编辑 / 新建标签 · 新建充值方案。
- **报表**（W4，`modules/reports/ReportsWB.tsx`）：7 个分区（概况 KPI 六块 + 四张图 · 时段竖条 + 13 列表 · 菜品横条按份数 / 金额 + 表 · 支付 · 会员与储值 · 优惠含平台券抵扣 · 退赠明细表）；时段 Seg、打印 / 导出。
- **班次**（W3，`modules/shift/ShiftWB.tsx`）：当班数据（统计行 · 按支付方式 · 异常操作 chip）· 钱箱实盘（P2 块色清单：六种面额张数 · 白色窄底差异红字 · 打印报表 / 确认交班）；浮层：确认交班（红实心最终确认）· 开班 `s1`（收银台 · 备用金 · 设备自检 · 缺纸提示）。
- **设置**（W4 两列，`modules/settings/SettingsWB.tsx`）：左列 `Rail`：「管理」组（菜品 / 会员 / 班次 → 各自工作台，页签「设置」保持选中）+ 9 组 36 项（双语两行）；右设置页：标题 + 面包屑 + 保存；页内容：营业概况、打印机表、档口路由、支付方式开关、员工表、角色权限矩阵、菜品 / 库存表、通用「标签 + 值 › / 开关」行；「界面与显示」= 快捷操作（最多 5 个）+ `StyleControls`（语言、布局 L1–L6、密度、形状方案、框架圆角、按钮形状、皮肤、色系、菜品图、卡片排布、菜名、网络）。
- **中英文**：词表约 600 条 key；导航、表头、按钮、说明、Toast 全部双语；菜名 / 分类 / 状态名按语言取数据字段；设置菜单与行用数据里的中英对。示例数据里的中文内容（活动名、会员称呼、原因文案）保留中文，和原型一致。
- **组件展示页**（`#components`，`dev/Components.tsx`）：令牌（角色色样、两套圆角、三档控件高、七档字号）· Button 全部变体 · Seg / Filter / Tabs · 选项块一族（Option / OptionMulti / PayItem / CouponCard 三种 / PlatMark / PromoRow）· 表单控件 · Steps / Stat / Kpi / Amount · 全部 112 个业务状态按 9 组列出 + Chip / Badge / Qty · 状态磁贴（桌台 10 态 + 订单卡 4 态）· Panel 三种骨架实例 · Table / Cells · Keypad / Pins · 反馈类 · 图表 · Dialog / Sheet / Menu（在一台小设备里打开）· Rail / QuickBar。页首开关对本页同样生效。
- 对照：菜品、会员、报表概况、班次、设置「界面与显示」与原型并排比过，布局、颜色、字号一致。

**本步未做 / 简化**
- 设置「界面与显示」用 Seg 行而不是原型的选项块（内容相同，和侧滑「设置」页签共用一个组件）；字体切换没搬。
- 菜品 / 会员 / 活动的搜索框只有外观；报表时段只切 Seg 不换数；页首「模拟扫码」只接了会员码。
- 新建会员 / 新建标签 / 新建方案的输入框是示例值，不校验。

## 六、检查清单对照（规范 §四）

- [x] 布局用 `MODULES` 声明；栏宽只用 104 / 128 / 160、360、440
- [x] 面板用 `Panel`；只有 bd 滚动；一屏实心主按钮 ≤ 1
- [x] 圆角只用 `rounded-rc/rt/rb/rk`；没有描边和阴影（设备外框的 10px 黑边是原型外壳，不是产品）
- [x] 墨只出现在文字和主按钮上；当前项 gray-200 + 墨字；已点、当前行 gray-100
- [x] 控件高 36 / 44 / 52，热区 ≥ 44（`hit`）；字号不小于 12
- [x] 只用角色令牌；颜色只给待处理（琥珀）和异常（红）；平台标识、支付图标的品牌色在数据里（§3.4 例外）
- [x] 状态都是「图标 + 文字」；图标按钮有 `aria-label`；可点元素是 `button`，禁用项 `disabled`
- [x] 中英文都看过（English + L2 截过图）
- [x] 没有写死的色值（`grep -n "#[0-9A-Fa-f]\{3,6\}" src --include=*.tsx` 只命中皮肤色样与平台品牌色数据）

## 七、已知事项

- 构建产物单个 chunk 约 1 MB：`lucide-react` 的 `icons` 全量表为了按名字动态取图标被整包打进来。正式实现时改成只登记 `pos-states.js` 和界面用到的图标（约 80 个）的静态映射即可；原型阶段不处理。
- 开发服务器 `http://127.0.0.1:5178`，`.claude/launch.json` 的 `pos-react` 配置用 `npm run dev --prefix prototypes/pos-react` 启动。
