# 全线点餐 POS · 原型 → App 交接清单

> 状态：v1 · 2026-11-11
> 决定：原型 `prototypes/pos-react` 已冻结。餐饮和零售是同一个 App（现有餐饮 Android App `bak/pos-android`，分支 `android-ui-v3`），按登录门店的业态分流；零售在分支 `android-retail` 上接入（方案：该分支 `docs/android/零售业态接入方案-2026-11-11.md`）。本清单第一、二节的技术映射与模块拆分按「新建工程」写，接入现有 App 时以现有工程的分层和约定为准，第四、五节的规则和画面清单照用。之后如需原型只做静态 UI。
> 原型代码：分支 `pos-react-retail`，合并请求 robinwu2021-code/fnb-ui#1。任务排期见《[任务清单](全线点餐POS-任务清单.md)》。
> 规格文档：《[设计规范（布局·形状·色系）](全线点餐POS-设计规范（布局·形状·色系）.md)》《[业务状态](全线点餐POS-业务状态.md)》《[功能菜单](全线点餐POS-功能菜单.md)》《[零售 POS 设计方案](全线点餐POS-零售POS设计方案.md)》《[端架构](全线点餐POS-端架构.md)》《[设备发现·外送平台·可拖布局·桌台2D3D方案](全线点餐POS-设备发现·外送平台·可拖布局·桌台2D3D方案.md)》

## 〇、怎么用这份清单

- **规格以原型为准**：界面长什么样、点了会怎样，打开原型对应画面看（页首「业态 / 工作台 / 各画面预设」能直达）；业务规则以 `src/domain/*.ts` 和 `npm run test:domain` 的断言为准。
- **原型冻结**：PR #1 合进 main 后原型只修 bug，不加功能；新需求直接在 App 里做。
- **每一项都有出处**：画面写原型文件名，规则写领域函数名，移植时逐项对照。

---

## 一、技术映射（React 原型 → Compose App）

| 原型里 | App 里 | 说明 |
|---|---|---|
| Vite + React 18 + TypeScript | Android 应用 · Kotlin · Jetpack Compose | 不用 Material 的视觉，组件全部按设计规范自绘 |
| UnoCSS 主题 + `styles/pos-colors.css`（35 个角色令牌）+ `styles/base.css`（形状 / 密度变量） | `core:ui` 设计系统：`PosColors`（按皮肤 × 色系生成）· `PosShapes`（rc / rt / rb / rk 两套圆角，5 档可切）· `PosDensity`（紧凑 36/44/52 · 标准 40/48/56）· `PosType`（7 档字号） | 给 `prototypes/bapp/tools/gen-colors.py` 加 Kotlin 输出，和网页同源生成，不手抄色值 |
| `data/pos-states.json`（20 实体 · 115 状态 · 9 组）+ `retail/dict.ts` 里的零售扩展（7 个实体） | 生成的 `PosStates.kt`（实体 → 状态 → 组 / 图标 / 中英名） | 零售扩展先并入 `gen-colors.py` |
| `i18n/dict.ts`（约 1,390 条）+ `retail/dict.ts`（约 330 条）+ `biz/dict.ts`（业态覆盖） | `strings.xml`（`values` / `values-en`）+ 业态覆盖层 | 写脚本转换；`{n}` 占位转 `%1$s`；取字符串时先查业态覆盖 |
| `components/ui/*`（21 个，Radix 原语）+ `components/pos/*`（6 组业务组件） | `core:ui` 里同名 Composable | 清单见第三节；焦点环、热区 ≥ 44、`contentDescription` 按规范补 |
| zustand：主 store + 零售 `useR` `useSales` `useStock` `useGoods` + 业态 `useBiz` | 每个工作台一个 ViewModel，`StateFlow<UiState>` | `act('名:参数')` 分发表 → ViewModel 的意图函数，名字可照搬 |
| `store/actions.ts`（112 个动作） | 各 feature 的 ViewModel | 动作只剩「取状态 → 调领域函数 → 写回 + 提示」 |
| `src/domain/*`（13 个文件，纯 TS） | `core:domain`（纯 Kotlin，不依赖 Android） | 逐函数移植，见第四节 |
| `domain/events.ts`（`on` / `emit`） | `DomainEvents`：`MutableSharedFlow<DomainEvent>` | 订阅放在仓储 / UseCase，不在界面层 |
| localStorage（显示开关、布局、平面图） | DataStore（偏好）· Room（业务数据） | 示例数据改成首次启动的种子数据 |
| Dialog / Sheet（Portal 到设备容器） | 自绘 Dialog（560 / 760）· 右侧 480 侧滑 | 遮罩 45% 墨 |
| Lucide 图标（按名字动态取） | 矢量图（只导入用到的） | 脚本从源码扫图标名生成清单，线宽 1.75 |
| 1280 × 800 设备容器 | 横屏平板，最小 1024 × 768 dp | 栏宽 104 / 128 / 160、详情 360、账单 440 不变 |

## 二、工程拆分（Gradle 模块）

```
app/                     入口、导航、业态分流（餐饮 / 零售工作台）、依赖注入
core/ui/                 设计系统：令牌、形状、密度、字号；基础组件 + 业务组件
core/domain/             领域服务，纯 Kotlin + JUnit
core/data/               Room、DataStore、仓储、领域事件总线
core/biz/                业态配置 BIZ[fnb | retail]
feature/  tables · order · checkout · orders · menu · members · reports · shift · settings      餐饮与共用
feature/  retail-pos · retail-orders · goods · stock                                            零售
device/                  硬件适配：打印、钱箱、扫码枪、电子秤、客显、支付终端、设备发现
sync/                    Outbox、局域网总线、云同步（后期）
```

依赖方向：`feature → core:*`；`core:domain` 不依赖任何模块。和《端架构》图 ① 的分层一一对应。

## 三、组件清单（`core:ui`）

- **基础组件**（`components/ui/`）：Button（default / soft / muted / danger / danger-solid / link × sm / md / lg / icon）· Seg · Filter · Tabs · Switch · Checkbox · Input / Search / Textarea · Dialog · Sheet · Menu · Panel（hd / bd / ft、ledger 变体）+ Block + DueFoot + Acts + Stat · StatusTag / StatusIcon / Chip / Badge / Qty / CatMark · StatusTile · Table · Cells · Keypad / Pins · Stepper · Steps · PayGroup / PayItem · Option / OptionMulti · PromoRow · CouponCard / PlatMark · Result / Amount / Spinner · Empty / Note / Banner / Skeleton / Toast · Kpi / VBars / HBars
- **业务组件**（`components/pos/`）：SysBarShell + SysEntry · LineRow · FeeRow · QuickBar / QuickView · ItemTile / TileFoot · PayHead / CashPad / WaitBlock
- **布局**：`layout/modules.ts` 的 `layoutOf()`（W1–W4 × L1–L6）→ `WorkbenchScaffold(nav, content, detail)`；Rail
- **验收**：原型「组件」页（`#components`）每个组件、每个变体在 App 里有对应 `@Preview`，并排比对

## 四、领域规则移植（`core:domain`）

| 原型文件 | 函数 / 类型 |
|---|---|
| `money.ts` | Currency · setCurrency · round2 · money |
| `pricing.ts` | PricingRules · FNB_RULES · RETAIL_RULES · rulesOf · totals · couponAmt · couponBlock · voucherMissing |
| `order.ts` | LINE_FLOW · canTransit · addCheck · addItem / addWithSpec · incAt / decAt · clearNew · sendNew · rush · removeAt · pickable · voidAt · repriced · repriceNeedsAuth · applyReprice · qtyOf · newCount · dishCount · stockLeft |
| `cart.ts` | scanAdd · bump · undoLast · toggleGift · removeLine · pieces · promoOff |
| `payment.ts` | keyInput · quickAmounts · defaultCash · cashResult · takeAmount · paidSum · methodBlock |
| `catalog.ts` | classifyCode · parseWeighCode · findByCode · stockState · ean13Check · toEan13 · ean13Bars · labelUnit |
| `table.ts` | TABLE_FLOW · canTableTransit · isBusy · setTable |
| `member.ts` | matchLayer · toggleTag · activePlans · pickPlan · recharge · togglePlan · CAMP_FLOW · canCamp |
| `aftersale.ts` | voucherUndo · refundableQty · allocate · refundAmount · restockByDefault · NO_RECEIPT · applyReturn · saleStatus · channelBlock · pickedPieces · exchangeSettle |
| `shift.ts` | DENOMS · drawerTotal · expectedCash · variance |
| `stock.ts` | receive · deduct · countDiff · nearExpiry · lowStock · applyCount · writeOff · applyDelta · costTotal · qtyTotal |
| `events.ts` | order.paid · order.held · stock.changed（delta / reason / ref）· exchange.credit |

**测试怎么移**：`scripts/domain-selftest.ts` 现有 60,067 项断言。在原型加一个导出脚本，把随机计价输入和 TS 结果存成 JSON fixtures，App 的 JUnit 读 fixtures 逐字段比对；单元断言逐条翻成 JUnit。**领域层移植完成 = fixtures 全部一致。**

**事件订阅关系**（App 照此接线）：

| 事件 | 谁发 | 谁订阅 → 做什么 |
|---|---|---|
| `order.paid` | 餐饮结账完成、零售收款完成 | 桌台服务：餐饮且有桌号 → 该桌「待清台」 |
| `stock.changed` sale | 零售收款完成（称重按 kg，临时商品不算） | 库存服务：扣账面、记流水 |
| `stock.changed` return | 退货 / 换货且回库 | 库存服务：加账面、记流水 |
| `stock.changed` loss | 退货 / 换货且不回库 | 库存服务：生成已过账的报损单（来自退货单号） |
| `exchange.credit` | 换货第 3 步确认 | 零售收银：开新购物车，带「换货抵扣」行 |

## 五、画面清单

### 5.1 共用壳

| 画面 | 原型 | 要点 |
|---|---|---|
| 系统栏 | `layout/sysbar.tsx` · `retail/RetailWorkbench.tsx` | 页签按业态；入口图标带状态；时间 |
| 离线横幅 | `layout/workbench.tsx` | 断网时出现，扫码支付等置灰写原因 |
| 长按编辑态 | `layout/edit/*` | 四个停靠槽、快捷方式库、栏位换位、点按模式 |
| 界面与显示 | `dev/StyleControls.tsx` | 语言、布局 L1–L6、密度、形状、圆角、按钮形状、皮肤、色系、菜品图、卡片排布 |

### 5.2 餐饮

| 工作台 | 原型 | 画面与浮层 |
|---|---|---|
| 桌台 | `modules/tables/*` | 宫格（11 态）· 平面 2D · 2.5D · 平面编辑器 · 桌台详情 · 选桌 · 清台 |
| 点餐 | `modules/order/*` | 分类导航 · 菜品卡三种 · 结单区（待下单 / 已下单 / 批量 / 拆单）· 规格 · 挂单 · 拼桌 · 调价（三种方式 + 授权）· 整单备注 · 更多 · 主管授权 |
| 结账 | `modules/checkout/*` · `overlays/checkout.tsx` | 账单 440 · 6 种收款方式 · 选券 · 平台券验券 · 整单折扣 · 收款成功 · 支付失败 |
| 订单 | `modules/orders/OrdersWB.tsx` | 列表 / 卡片 · 详情 · 平台券撤销核销 · 外卖单号映射平台 |
| 菜品 | `modules/menu/MenuWB.tsx` | 菜品 · 分类 · 规格 · 套餐 · 渠道同步 |
| 会员与营销 | `modules/members/*` · `overlays/members.tsx` | 会员 · 标签 · 储值 · 活动 · 优惠券核销 · 积分 |
| 报表 | `modules/reports/ReportsWB.tsx` | 7 个分区 |
| 班次 | `modules/shift/ShiftWB.tsx` | 当班数据 · 钱箱实盘与盘差 · 交班 · 开班 |
| 设置 | `modules/settings/*` · `overlays/DeviceAdd.tsx` `PlatformAdd.tsx` | 菜单按业态 · 设备列表与添加（三路）· 平台对接 · 打印机 · 档口路由 · 支付方式 · 员工 · 角色权限 · 通用设置行 |

### 5.3 零售

| 工作台 | 原型 | 画面与浮层 |
|---|---|---|
| 收银 | `retail/RetailPos.tsx` | 空车 · 扫了 14 件 · 未知条码 · 查价 · 称重 · 选规格 · 取单 · 清空确认 · 收款 · 收款完成 · 换货补差价 / 退差价 |
| 订单 · 退货 · 换货 | `retail/RetailOrdersWB.tsx` · `ReturnDialog.tsx` | 列表与状态筛选 · 详情 · 退货四步 · 无单退货 · 换货三步 → 收银 |
| 商品 | `retail/GoodsWB.tsx` · `PriceLabel.tsx` | 商品资料（改价）· 库存（筛选、流水）· 价签（队列、三种模板、EAN-13 预览、打印） |
| 库存作业 | `retail/StockWB.tsx` | 入库 · 盘点（提交冻结快照 → 审批过账）· 报损 · 调拨（未实现）· 预警 |

## 六、App 要新做的（原型只模拟或没做）

| 项 | 原型现状 | App 里 |
|---|---|---|
| 持久化 | 内存 + localStorage，刷新复原 | Room：商品、单据、库存流水、会员、班次 |
| 打印 | 提示模拟 | ESC/POS（网口 9100 / 蓝牙 / USB）：小票、厨房单、退货小票；标签机（TSPL / ZPL 二选一） |
| 扫码枪 | 页首「模拟扫码」 | HID 键盘模式：两键间隔 < 30ms 判为扫码，回车结束；扫码框常驻焦点 |
| 电子秤 | 固定读数 | 串口 / 蓝牙协议，读数稳定判断 |
| 客显 | 只在设置里 | Android Presentation（第二屏） |
| 收款 | 手动确认到账 | 聚合支付 SDK / 刷卡终端；按地区的收款方式 |
| 设备发现 | 示例数据 + 延时 | NSD（mDNS）、BLE 扫描、扫码配对 |
| 多设备集群 | 无 | 主 POS 起本地服务（Ktor）+ WebSocket 总线；按设备角色加载工作台 |
| 离线同步 | 横幅 + 能力置灰 | 领域事件写 Outbox；单号 = 门店 + 设备 + 序号；按《端架构》能力矩阵降级 |
| 账号与权限 | 固定当班人、授权码任意 4 位 | 员工登录、角色权限、主管授权码校验与审计 |
| 零售分类 / 规格 / 渠道同步 | 复用餐饮菜品模块 | 按零售字段改造 |
| 税与价目 | 只换币种符号 | 等决定（任务清单 D2、D3） |

## 七、原型专用、不移植

页首 DevBar（业态 / 工作台切换、显示开关）· 零售页首各「画面预设」与「模拟扫码」· 组件展示页（改为 Compose `@Preview`）· 示例数据（改为种子数据）· 设备外框与舞台缩放。
