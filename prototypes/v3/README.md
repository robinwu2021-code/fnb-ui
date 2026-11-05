# 餐饮 POS v3 原型

开发交接以 [原型与 UI 开发基线 v3.1](../../docs/餐饮POS原型与UI开发基线-v3.1.md) 为入口。该文档统一记录当前视觉、组件、页面交互、状态、设置、开发边界和验收标准；历史迭代记录不再作为最新规则。

## 启动与入口

在仓库根目录运行，无需 npm 安装或构建：

```sh
python3 -m http.server 8765 --bind 127.0.0.1 --directory prototypes
```

- [桌台](http://127.0.0.1:8765/v3/pos-ui.html#tables)
- [点餐](http://127.0.0.1:8765/v3/pos-ui.html#order)
- [组件展示](http://127.0.0.1:8765/v3/components.html)
- [公共组件接口](components/README.md)

## 当前能力

- 点餐分类、搜索、筛选、规格/备注、数量、库存校验、每桌草稿、挂单及模拟送厨。
- 16张演示桌台，大厅/窗边/包间/二层分类，五种状态背景，紧凑卡片与可编辑坐标的平面位置。
- 结账核对：新加、已送厨未结账、历史已结账分区，优惠资产、金额明细、收款方式与小票预览。
- 组件展示按基础、按钮、输入筛选、菜品卡、订单行、桌台切换；字体可对照。
- 共用页头、页脚和显示设置：8皮肤、6风格、6字体、3字号、3密度、独立容器/按钮圆角、中英文。点餐支持L1–L6布局、有图/无图与上下/左右卡片。

## 代码职责

| 路径 | 职责 |
| --- | --- |
| model.mjs | 订单、库存、金额示例、设置校验 |
| model.test.mjs | 11项模型回归 |
| menu.mjs | 演示菜单与第三方图片URL |
| components/controls.mjs、controls.css | 公共控件与最终交互样式 |
| components/chrome.mjs | 页头、页脚 |
| components/ui.mjs | 菜品卡、订单行、格式与安全文本 |
| components/gallery.mjs、gallery.css | 组件展示及样例布局 |
| settings/options.mjs | 共享设置与双语名称 |
| tokens/theme.css、ui.css | 基础令牌与工作台布局；包含历史覆盖，正式实现需收敛 |
| workbenches/order.mjs、tables.mjs | 点餐、桌台与交互装配 |

## 验证

```sh
node --test prototypes/v3/model.test.mjs
node --check prototypes/v3/workbenches/order.mjs
node --check prototypes/v3/workbenches/tables.mjs
node --check prototypes/v3/components/gallery.mjs
```

## 运行边界

订单为当前页面会话数据，刷新恢复示例；显示偏好和桌台坐标保存本机。联网、消息、打印、预订时长、会员、促销、储值与历史收款均为演示；不执行真实收款、核销或储值扣款。订单管理、班次、退款、移桌并桌、真实设备及Android真机适配仍需开发。

平面画布900×460，坐标保存到 `pos-v3-table-positions`；当前坐标边界X 0–744、Y 0–332。入口与通道为示意；未实现拖拽、旋转或墙体编辑。

演示服务费5%、会员优惠5%、满200减10、活动满100减5允许叠加；正式业务规则需另行确认。图片和Google字体依赖网络。


## 账号与配置扩展

新增[账号与门店管理原型说明](../../docs/餐饮POS账号与门店管理原型补充-v3.2.md)：登录/注册、选店、桌台与区域创建、商品分类/商品/规格/库存/餐牌配置、系统设置。入口为 `#login`、`#register`、`#stores`、`#manage`、`#system`。配置与账单在两家演示门店间按会话隔离；刷新恢复演示数据。密码不保存或发送。

管理表单验证：`node --test prototypes/v3/model.test.mjs prototypes/v3/admin.test.mjs`，共14项。新增代码为 `workbenches/admin.mjs`、`components/admin.css`。


系统管理入口统一为“系统管理”，使用一级分组/二级页面菜单。商品编辑增加六个分区及餐牌直接关联；组件页增加“管理表单”。见[开发规范v3.3](../../docs/餐饮POS系统管理与商品编辑规范-v3.3.md)。回归测试更新为15项。


## 多规格与图片 v3.4

商品创建/编辑支持多规格自动组合、SKU 独立价格与库存、SKU 盘点及点餐选型。本地 JPG/PNG/WebP（≤5MB）支持预览、替换和移除，仅保存在本次页面会话。管理表单与组件展示页共用 `sku-editor.mjs` 和 `product-media.mjs`。见[开发规范v3.4](../../docs/餐饮POS多规格SKU与商品图片规范-v3.4.md)。执行 `node --test prototypes/v3/*.test.mjs`，共18项。


## 导航与显示设置 v3.5

系统管理改为共享齿轮图标，支持中英文提示及选中态。显示设置精简成外观/布局/文字，圆角微调折叠；去除重复语言、图文排列入口及图片比例、演示状态。点餐、桌台、管理及独立组件页共用。


## Android 页面开发交接

开发入口：[Android 交接索引](../../docs/android/README.md)，包含原型架构/页面映射、数据契约/状态和实施验收清单；基于当前v3.6及已有Compose骨架，区分演示与真实服务。
