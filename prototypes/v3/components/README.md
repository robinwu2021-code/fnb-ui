# POS 公共 UI 组件

业务数据和事件处理留在工作台；组件返回 HTML，事件统一通过 `data-action` 接入。

| 文件 | 职责 | 使用位置 |
| --- | --- | --- |
| controls.mjs | button、searchField、segmentedControl、layoutToggle、orderToolbar | 点餐、桌台、显示设置及弹窗 |
| controls.css | 按钮、平面选中态、搜索与筛选工具栏、紧凑文字间距 | 全部页面 |
| chrome.mjs | appHeader、appFooter | 点餐、桌台共享页头页脚 |
| ui.mjs | dishCard、orderLine、names、money、specText；重导出控件 | 菜单、购物车、结账 |

`searchField({id,value,label,placeholder,clearAction,className})` 负责安全输出文本。
`segmentedControl({items,value,action,label})` 使用 `[值,显示文本]` 数组，输出可访问的选中状态。
`layoutToggle(picture,L)` 在同一位置切换上下/左右排列，图标表示当前排列，提示说明下一步操作。
`button` 的 label 可传入可信组件 HTML；用户文本先经过 `esc`。控件不直接修改订单或本地存储。

按钮状态统一：默认浅底、选中主题浅底、主操作主题实底、禁用降低透明度、键盘焦点外轮廓；选中不使用阴影或底部线条。容器与按钮圆角分别由设置控制。


## 独立展示页

打开 `/v3/components.html`。展示按钮状态、输入与筛选、菜品卡有图/无图及上下/左右组合、订单五种状态、桌台工作台。`gallery.mjs` 直接使用 `ui.mjs`、`controls.mjs`、`renderTables`，不复制业务组件。

右上角显示设置与业务页共用同一份 `settings/options.mjs`，三个页面使用同一个 `pos-v3-settings`，并监听其他标签页偏好变化。`gallery.css` 仅定义展示布局和并排排列示例。展示区业务按钮仅提示演示，不变更真实工作台状态。

菜单分类设置 `min-width:0/min-height:0`，文字列可收缩、计数固定，超高在区域内滚动；顶部或底部分类在区域内横向滚动。

显示设置仅保留上下/左右两种图文排列；旧右图/下图偏好加载时归一化。容器圆角与按钮圆角名称明确区分，各字段只出现一次。


## 系统管理与商品编辑

`management.mjs` 提供唯一菜单树 `managementGroups`、多级导航 `managementNavigation` 和六分区表单 `productEditor`。商品管理与系统配置复用同一菜单；业务编辑弹层和组件页“管理表单”直接调用同一编辑器，避免复制表单。

`productEditor` 接收商品、分类、规格组和餐牌，不直接提交业务。业务表单ID为 `admin-form`，展示表单ID为 `gallery-product-form`；展示表单提交被拦截。餐牌复选名为 `menuIds`，保存由业务层统一替换商品关联。

开发规则见[系统管理与商品编辑规范v3.3](../../../docs/餐饮POS系统管理与商品编辑规范-v3.3.md)。


### 多规格与图片

`sku-editor.mjs` 提供组合表格 `skuTable` 与 `readSkuRows`；`product-media.mjs` 提供本地图片读取预览、地址预览及移除。业务和展示页通过相同事件入口调用；SKU 数据算法在 `../sku.mjs`。样例表单展示2×2规格和独立价格/库存，继承公共皮肤、风格、字体与间距。详见[v3.4规范](../../../docs/餐饮POS多规格SKU与商品图片规范-v3.4.md)。


### 导航与显示设置 v3.5

`systemManagementButton(L, active)` 为共享齿轮图标入口，保留中英文无障碍名称、悬停提示、当前页状态和键盘焦点。工作台顶部/底部导航使用同一组件。

显示设置统一为外观、布局、文字三组；皮肤、组件风格、间距保留，容器/按钮形状放入默认折叠的“形状微调”。布局包含分类与账单位置、导航位置、金额操作位置和有图/无图；文字保留六种字体、字号及菜名单双语。语言使用页头入口；图文排列使用点餐工具栏图标；图片比例和网络/菜单加载演示开关从显示面板移除。旧偏好仍可读取，恢复默认可清除历史配置。形状微调展开状态在调节时保留，切换分组回到顶部。


## 独立组件设计规范

[餐饮 POS 组件设计规范 v3.5](../../../docs/餐饮POS组件设计规范-v3.5.md) 汇总当前原型与 B 端 pos-app/pos-ds 的按钮、字体、色系、间距、圆角、状态及复用契约，并区分当前实现与正式开发要求。


## 组件规范落地 v3.6

新增 `recipes.css`，在页面布局样式之后加载，集中提供按钮语义变体（primary/secondary/soft/text/danger）、加载外观、统一主题焦点、数量步进器、状态文字、表格金额对齐和表单说明字号。色彩与桌台状态角色集中在 `tokens/theme.css`。

点餐送厨采用主题浅底，结账保持实底；桌台详情同样以结账为主操作、继续点餐为次操作。状态文字最小12px、次级桌台元信息最小11px；大字号下卡片允许增高。组件页展示六种字体、通用语义角色色、按钮禁用及加载外观。加载按钮目前仅展示状态配方，业务异步行为仍由工作台控制。
