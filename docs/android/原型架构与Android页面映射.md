# 原型架构与 Android 页面映射

基线：2026-11-03，HTML 原型 v3.6。目标：基于已评审原型开发原生 Android 页面，保留紧凑工作台、完整业务信息与统一组件，不把 DOM 或 CSS 覆盖链直接搬入 Compose。

## 1 原型架构

```text
pos-ui.html / components.html
  └─ workbenches/order.mjs         入口、路由、状态、事件调度、弹层
      ├─ model.mjs                订单数量、金额、送厨状态、偏好校验
      ├─ workbenches/tables.mjs    桌台数据、筛选、网格与平面展示
      ├─ workbenches/admin.mjs     登录/选店、商品/桌台配置、门店快照
      ├─ sku.mjs                  规格解析、组合生成、SKU 校验
      ├─ components/
      │   ├─ controls.mjs/.css     按钮、搜索、筛选、排列图标
      │   ├─ chrome.mjs           页头、页脚
      │   ├─ ui.mjs               菜品卡、订单行、金额和双语格式
      │   ├─ management.mjs       多级菜单、商品分区编辑器
      │   ├─ sku-editor.mjs       SKU 表格、编辑值读取
      │   ├─ product-media.mjs    本地图片读取、预览和移除
      │   ├─ gallery.mjs/.css     共享组件展示
      │   ├─ admin.css            管理页布局
      │   └─ recipes.css          v3.6 公共组件配方
      ├─ tokens/theme.css         主题、字号、形状、语义状态角色
      ├─ settings/options.mjs     三组显示设置定义
      ├─ i18n/index.mjs           界面语言选择
      └─ menu.mjs                 演示商品数据
```

样式加载顺序为 theme → ui → controls → gallery → admin → recipes。`recipes.css` 是最近收敛的组件规则；`theme.css` 的早期值可能被 `ui.css` 覆盖。开发取最终配方及组件规范，不只读取主题文件开头。

当前入口 `order.mjs` 同时持有多个页面状态，通过 `data-action` 分派事件并重绘 HTML。这是原型装配方式；Android 应拆成独立页面状态与组件事件。组件展示页调用同一渲染组件，不维护另一套视觉实现。

## 2 页面与导航

| 原型入口 | Android 页面/容器建议 | 进入与退出 |
| --- | --- | --- |
| `#login` / `#register` | LoginScreen / RegisterScreen | 登录/注册成功后选店；失败保留非敏感输入 |
| `#stores` | StorePickerScreen | 确认门店后加载该门店配置和工作台 |
| `#tables` | TablesScreen | 选桌查看详情；开台/继续点餐进入指定桌台订单 |
| `#order` | OrderScreen | 保留当前桌订单；桌台与点餐切换不清空草稿 |
| 结账弹层 | CheckoutSheet 或 CheckoutScreen | 携带 orderId，返回同一订单；窄屏全屏承载 |
| `#manage` | ManagementHost | 多级菜单中的桌台与商品资料配置 |
| `#system` | SystemSettingsHost | 门店、设备、费用、员工信息配置 |
| 显示侧栏 | DisplaySettingsSheet | 即时生效，关闭返回原页面，订单不丢失 |
| `#components` / `components.html` | ComponentGalleryScreen | 开发/验收入口，可在正式终端按构建配置隐藏 |

系统管理为共享齿轮图标，保留名称、提示和当前页状态。顶层导航与系统管理子页面使用分层导航；子页面需携带稳定 section ID，而不是依赖中文菜单文案。原型 `#manage` 不包含具体子页，刷新默认回桌台管理；Android 建议显式保存 section。

正式登录守卫：未认证 → 登录；已认证无门店 → 选店；有门店 → 上次工作台或桌台。原型没有真实认证守卫，不能把直接进入 `#order` 当成正式登录流程。

返回键顺序：关闭临时提示/弹层 → 处理有改动的编辑草稿 → 返回上级子页 → 顶层页面。商品编辑有未保存改动时提示继续编辑或放弃；这是 Android 要补齐的防丢失契约，当前 HTML 取消直接放弃草稿。

### 系统管理树

```text
系统管理
├─ 门店运营：桌台管理 / 桌台区域 / 门店与营业
├─ 商品管理：商品分类 / 商品资料 / 规格组 / 库存盘点 / 餐牌管理
├─ 设备与系统：终端与打印 / 收款与费用 / 员工与权限 / 界面显示
└─ 账号与门店：切换门店 / 账号登录
```

菜单定义来源：[management.mjs](../../prototypes/v3/components/management.mjs)。部分设置页面只有展示/配置演示，不代表设备及权限管理服务完整。

## 3 页面骨架与组件分层

```text
PosApp
├─ SessionGate / StoreScope
└─ PosShell
   ├─ PosHeader：门店、员工/终端、导航、消息、打印、语言、显示
   ├─ WorkbenchContent
   │  ├─ Tables：区域栏 + 桌台内容 + 桌台详情
   │  ├─ Order：分类与菜单大面板 + OrderDock
   │  └─ Management：分组菜单 + 内容面板
   ├─ PosFooter：在线/同步、钱箱、交班、帮助
   └─ OverlayHost：规格、备注、开台、商品编辑、结账、显示设置
```

- 分类与菜单共用一个大面板，内部以浅渐变/模糊阴影区分逻辑；桌台区域栏沿用分类组件。
- 点餐账单按新加、待发送、已送厨等状态分组，菜品清单优先分配空间；顶部身份和底部金额区域保持紧凑。
- 桌台卡默认白底淡阴影，五种状态以整卡背景表达，禁止顶部色条；选中保留状态背景。
- 商品表单依次包含基本资料/分类、图片、基础售价、多规格、库存、SKU 表、餐牌关联；固定保存区，内容单独滚动。
- 页头页脚、显示设置和公共控件不因切换业务页而换样式。

## 4 Android 代码组织建议

沿用现有 Kotlin + Compose 方向；以下为建议职责，可先在单 app 模块以 package 分层，避免为初期页面开发过度拆分 Gradle 模块。

```text
app/
  core/designsystem/     PosTheme、颜色/字体/间距、基础控件
  core/model/            业务实体、金额、状态枚举
  core/data/             Repository 接口、演示及真实数据实现
  core/device/           打印、钱箱、终端连接适配接口
  feature/auth/          登录、注册、选择门店
  feature/tables/        筛选、网格/平面、开台和详情
  feature/order/         菜单、规格、购物车、送厨
  feature/checkout/      账单核对、优惠、支付状态
  feature/management/    桌台、商品、SKU、库存、餐牌
  feature/settings/      门店配置与显示偏好
  feature/gallery/       组件验收页
  navigation/            目的地、路由参数、权限守卫
```

每个功能采用 `Screen(UiState, onAction)`：Screen 不直接修改 Repository；ViewModel 接收 Action，调用用例/Repository，输出状态。Toast、导航、打开图片选择器等一次性效果与持久 UiState 分开，避免重组重复执行。Repository 对外区分加载中、成功、可重试失败及冲突。

订单、门店配置和设备状态分别管理。不要把现有全局 `PosStore` 扩大成所有页面的唯一可变对象；可逐步迁移，共享会话/门店作用域，页面保持独立状态。

基础组件优先包括 PosButton、PosIconButton、PosTextField、PosFilterChip、PosSegmentedControl、PosOptionPicker、PosQuantityStepper、PosDialog/Sheet、PosDataTable；业务组件组合这些基础件。

## 5 主题、语言与自适应

默认参考：松绿、Quiet、标准字号、紧凑密度、L1、Manrope + Noto Sans SC；若本机偏好选择 Inter，不能误认为默认值已改。颜色、字体、尺寸定义以[组件规范](../餐饮POS组件设计规范-v3.5.md)为准。

- px 不等于 Android 物理像素；布局转 dp、字号转 sp，并在目标屏幕和系统缩放下校准。
- 现 HTML 主要在950px附近转窄屏、700px附近调整桌台/结账、540px附近精简控件；这些是视觉参照，不作为 Android 固定设备分类。
- Android 按应用实际可用宽度、剩余菜单宽度及触控空间决定双栏/单栏，不能只判断横竖屏或设备型号。
- 宽屏保留菜单+账单、桌台+详情；窄屏使用底部导航、购物车摘要和全屏账单/Sheet。网格列数按最小可用卡宽计算，不固定写死列数。
- 卡片可紧凑，触控热区至少48dp且不能相互重叠；适配系统状态栏、导航栏及键盘，保存/支付按钮必须可达。
- 字体随包提供，中文回退固定；金额等宽数字、两位小数。英文和大字模式先重排，不截掉关键操作与金额。

显示设置仅保留外观/布局/文字。外观含皮肤、风格、密度和折叠的形状微调；布局含分类/账单位置、导航位置、操作区位置、有图/无图；文字含字体、字号、菜名单双语。语言入口在页头，图文排列入口在点餐工具栏，避免重复。

当前布局含义必须显式转换：

| ID | 菜单分类 | 账单 |
| --- | --- | --- |
| L1 | 左 | 右 |
| L2 | 上 | 右 |
| L3 | 上 | 左 |
| L4 | 右 | 左 |
| L5 | 下 | 右 |
| L6 | 下 | 左 |

上述是当前点餐布局语义。桌台区域栏当前宽屏在左、窄屏在上，并未完整跟随六布局切换；Android 如需统一扩展，作为后续实现项。旧 Android L5/L6 为左/左、右/右，禁止按相同枚举名直接迁移。

## 6 现有 Android 骨架复用与替换

工程位置：[bak/pos-android](../../bak/pos-android/README.md)，包名 `ai.neargo.fnb.pos`。实际构建配置为 compileSdk37、minSdk26、targetSdk36、Java17；这是现有工程记录，不是本次升级建议。本次没有运行 Gradle，不保证当前机器构建环境就绪。

| 文件/区域 | 可复用结构 | 必须按最新原型调整 |
| --- | --- | --- |
| `ui/shell/PosApp.kt` | Compose 导航、共享壳、窄屏导航 | 增加账号/选店/管理路由、公共页脚和管理图标 |
| `ui/shell/Frame.kt` | 根据可用宽度计算布局 | L5/L6映射、最终尺寸、双栏降级策略 |
| `theme/PosTheme.kt`、`PosColors.kt` | 主题注入方式 | 八套皮肤、语义色、字体与独立圆角；旧深色仅作参考 |
| `ui/components/Controls.kt` | 基础组件入口 | 新按钮层级、无灰框、选中无阴影、焦点与触控区 |
| `ui/order/*` | 菜单/账单/规格结构 | 左右或上下、有图/无图、SKU选择、紧凑清单 |
| `ui/tables/TablesScreen.kt` | 区域、状态、详情 | 五种整卡背景、平面位置、卡片信息层级 |
| `ui/checkout/CheckoutScreen.kt` | 结账容器和支付展示 | 新加/已送厨/历史收款、优惠资产；真实支付另接服务 |
| `state/PosStore.kt` | 示例状态与 ViewModel 起点 | 按订单/门店/设置拆作用域，持久化与冲突处理 |
| 新增管理功能 | 原型表单结构可参照 | 登录、门店、商品、SKU、图片、库存、餐牌页面需补齐 |

旧骨架存在订单和交班页面，最新 HTML 中对应入口主要为后续功能提示；不能以“旧工程有页面”直接认定新方案已验收这些流程。优先实现已评审的桌台、点餐、结账和管理页面。

## 7 服务与设备边界

页面先使用可替换的演示 Repository 开发，明确显示演示状态；接入服务时保持 UI 契约一致。服务以业务能力定义，本文不虚构已存在的 HTTP 地址、鉴权协议或支付 SDK。

- 会话：登录、注册、门店授权、员工权限、令牌失效。
- 门店目录：区域、桌台、分类、商品、规格、SKU、库存、餐牌与配置。
- 订单：开台、加载/保存草稿、送厨、批次状态、催菜/上菜。
- 结账：服务端报价、优惠资格、资产校验、支付单及最终结果查询。
- 图片：授权上传、内容校验、缩略图、商品引用与删除生命周期。
- 设备：打印任务、结果回执、重试、缺纸/断连、钱箱指令。

调用中阻止重复提交；失败保留输入；冲突提示重新核对；门店切换隔离数据。详细字段与状态见[数据契约](数据契约与交互状态.md)。

## 8 开发顺序

1. 建立主题、基础组件和组件验收页，确认平板/窄屏视觉。
2. 建立导航壳、会话/门店作用域与演示 Repository。
3. 完成桌台→开台→点餐→规格→购物车→送厨闭环。
4. 完成结账核对及支付状态界面；真实支付与设备按独立接口接入。
5. 补齐账号、门店、系统管理、商品/SKU/图片与库存餐牌。
6. 覆盖错误、离线、重启恢复、大字/英文、设备回执及权限验收。

页面开发与真实服务接入分开验收，不能用模拟成功提示替代真实业务完成。逐项任务见[实施清单](页面实施与验收清单.md)。
