# pos-android · 餐饮收银 Android 演示工程

Jetpack Compose + Material 3 原生实现。界面对齐 v3 原型（组件规范 v3.5、落地 v3.6），扁平风格：无阴影、无渐变，分区靠色块与留白。演示数据与 `prototypes/v3/menu.mjs`、`workbenches/tables.mjs` 同源。

开发约定见 [docs/android](../../docs/android/README.md)；评审结论与改造记录见 [现有工程评审与v3对齐记录](../../docs/android/现有工程评审与v3对齐记录.md)。

## 运行与测试

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

需要 JDK 17+ 和 Android SDK 37，首次构建需联网下载依赖。

## 分层

```
app/src/main/java/ai/neargo/fnb/pos/
├── core/model/          纯 Kotlin：实体、显示偏好枚举、OrderRules（加菜合并、库存上限、状态不回退、金额）
├── core/data/           Repository 接口 + 演示实现 DemoWorkspace（DataStore 快照持久化）+ AppContainer（手动 DI）
├── core/designsystem/
│   ├── theme/           颜色角色、八套皮肤、桌台五态、角色字号、密度与圆角 → 映射到 MaterialTheme
│   └── component/       基于 Material 3 的扁平组件：PosButton、PosIconButton、PosChip、RailItem、QuantityStepper、
│                        SearchField、PosSwitch、Panel、CountBadge、Tag、GroupTitle、KeyValue、DetailBlock、
│                        PosDialog、PosSheet、ProductImage、Workbench / PosLayout
├── feature/order/       OrderViewModel → OrderUiState + OrderAction；DishCard、OrderLineRow、OrderDock、规格 / 详情弹窗
├── feature/tables/      TablesViewModel（含一次性导航 Effect）；TableCard、TableDetail、开台弹窗
├── feature/checkout/    CheckoutViewModel（读取路由参数 tableId）；结账与收款结果
├── feature/display/     DisplayViewModel；显示设置 Sheet
├── feature/shift/       交班（演示，无状态）
├── navigation/          类型安全路由：TablesRoute、OrderRoute、CheckoutRoute(tableId)、ShiftRoute
└── app/                 PosApplication、ShellViewModel、PosApp（页头、页脚、导航宿主、提示）
```

约定：

- Screen 只接收 `UiState` 和 `onAction`，不碰 Repository；`XxxRoute` 负责取 ViewModel、订阅状态、处理导航 Effect。
- 一次性提示走 `MessageBus`，导航走各 ViewModel 的 Effect 通道，都不放进持久 UiState。
- 界面不 import 演示数据；`DemoCatalog` 只在 data 层使用。
- 订单按桌台隔离；草稿、送厨状态、当前桌台、显示偏好在进程重启后恢复。

## 组件与 Material 3 对应

| 组件 | Material 3 基础 | 扁平化处理 |
|---|---|---|
| PosButton | Button / TextButton | 零阴影；选中只换浅底与主色字 |
| PosIconButton | FilledIconButton / IconButton | 控件底色块 |
| PosChip | FilterChip | 去描边、零阴影 |
| RailItem | 可选中 Surface | 左侧 3dp 指示条；不用 NavigationDrawerItem（88dp 栏宽下会截断） |
| QuantityStepper | IconButton | 轻底一体 |
| CountBadge | Badge | 主色 |
| DishCard、TableCard | Card | 零阴影；桌台选中用状态色 2dp 外环，网格铺轻底让白色空闲卡可辨 |
| Panel、PosDialog、PosSheet | Surface、Dialog、ModalBottomSheet | 零色调提升、零阴影 |
| PosDivider | HorizontalDivider | 1dp 极淡分隔，替代旧的渐变阴影 |
| PosSwitch | Switch | 无描边轨道 |

小于 48dp 的视觉控件依赖 Compose 的触控目标自动扩展满足命中下限。

## 布局

点餐布局 L1–L6（显示 → 布局）为菜单分类位置 × 账单位置：L1 左/右、L2 上/右、L3 上/左、L4 右/左、L5 下/右、L6 下/左。偏好键为 `order_layout_v3`，不读取旧工程的布局值。桌台详情跟随账单所在侧。

断点按应用实际宽度：≥ 960dp 双栏（≥ 1190dp 账单坞 350dp，否则 340dp）；< 960dp 分类横排在上，账单为底部摘要 + 全屏 Sheet，导航移到底部。

## 用到的库

| 库 | 用途 |
|---|---|
| Compose BOM 2026.09.00、Material 3 | UI 与组件基础 |
| navigation-compose 2.10 + kotlinx-serialization 1.11 | 类型安全带参路由、快照序列化 |
| DataStore Preferences 1.2.1 | 显示偏好与演示工作台持久化 |
| Coil 3 + OkHttp、compose-shimmer、Telephoto | 菜品图加载、骨架、大图缩放；带应用 User-Agent（Wikimedia 拒绝默认 UA） |
| Lottie、Konfetti | 收款成功动画 |
| JUnit 4、kotlinx-coroutines-test | 单元测试 |

演示菜品图来自 Wikimedia Commons，上线前换成门店自有图片。所有送厨、收款、打印、会员与优惠都是本地演示，不代表真实服务已接通。

## 截图

`docs/screenshots/v3/`：平板点餐、桌台、开台、结账、蓝色皮肤 + L4（2560×1600，320dpi）；手机点餐、空账单（1080×2400，420dpi）。根目录下为旧版界面，仅作历史参考。
