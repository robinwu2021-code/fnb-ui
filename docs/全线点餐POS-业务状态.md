# 全线点餐 POS · 业务状态（辅助色与图标）

> 由 `prototypes/bapp/tools/gen-colors.py` 生成，不要手改；改状态请改脚本里的 `STATES` 和 `GROUPS`。
> 依据：《详细功能清单》A–K（已归档 `bak/docs/全线点餐POS-详细功能清单.md`）与[功能菜单](全线点餐POS-功能菜单.md)。颜色体系见[色彩体系](全线点餐POS-色彩体系.md)第七节。
> 预览：`prototypes/bapp/pos-colors.html` 第 05 节。图标用 [Lucide](https://lucide.dev)（ISC 许可），线宽 1.75。

## 一、规则

1. **状态一律「图标 + 文字」**，颜色是辅助；同一组的状态用同一种颜色，同一个意思在不同实体上用同一个图标（例如「制作中」都是 chef-hat）。
2. **按意思分组，不按实体分组。** 9 组覆盖全部状态；新增状态先判断属于哪一组，再挑图标。
3. **两种形式。** chip：浅底 + 图标 + 文字，用于要人注意或要提前看到的组（待处理、预约、暂存、异常）；dot：图标 + 彩色文字、不铺底，用于进行中、等待、完成、变更标记、关闭。chip 组的浅底两两 ΔE ≥ 4；「待处理」（橙）和「异常」（红）最接近，靠图标和文字区分。
4. **浅底统一 200 档**，比主题浅底深一档；文字在浅底上 ≥ 4.5:1，图标和圆点在浅底和白底上 ≥ 3:1。
5. **桌台「用餐中」不是业务状态色**，它用主题浅底表示「这张桌在用」，见色彩体系的位置清单。
6. **撞色避让。** 微信绿主题下「已完成」组、经典蓝主题下「进行中」组改为灰色；其余组靠图标、文字和浅底深浅区分。

## 二、分组与颜色

| 分组 | English | 颜色 | 形式 | 圆点 | 文字 | 浅底 | 文字/浅底 | 圆点/浅底 | 含义 |
|---|---|---|---|---|---|---|---|---|---|
| 待处理 `action` | Needs action | 警告（状态色） | chip | #AD6200 | #8F5000 | #FFD1A9 | 4.51 | 3.30 | 要人处理或留意：待接单、待结账、待审批、催菜、低库存、缺纸、离线（还能收现金和刷卡） |
| 进行中 `progress` | In progress | 信息蓝（状态色） | dot | #0076DE | #004A93 | #C2DDFF | 6.28 | 3.25 | 正在进行：制作中、已接单、营业中 |
| 等待中 `waiting` | Waiting | 辅助色 · 青 | dot | #00888E | #00575B | #B4E5E8 | 6.09 | 3.12 | 在等别人或外部系统：待取餐、配送中、支付中、排队打印、待同步 |
| 已完成 `done` | Done | 成功（状态色） | dot | #008F50 | #005C31 | #B1EBC4 | 6.04 | 3.08 | 完成，不需要再看：已上菜、已结账、已送达、已打印 |
| 预约 `booked` | Booked | 辅助色 · 紫罗兰 | chip | #9E49CA | #8436AC | #EBCDFF | 4.74 | 3.45 | 未来的安排：已预订、待到店 |
| 暂存 `held` | On hold | 辅助色 · 橄榄 | chip | #688400 | #415400 | #D1E3AE | 6.14 | 3.13 | 暂时放着、还会回来：挂起、待下单 |
| 变更标记 `trace` | Change mark | 辅助色 · 品红 | dot | #C63288 | #A81E70 | #FFC9E1 | 4.76 | 3.49 | 追溯用的标记：补打、反结账、合自、拆自、转自、改价、临时菜、加菜 |
| 已关闭 `closed` | Closed | 中性灰 | dot | #848689 | #67696B | #EDEFF2 | 4.79 | 3.17 | 结束且不需要处理：已取消、已拒单、已撤单、空闲、未连接 |
| 异常 `failed` | Problem | 危险（状态色） | chip | #D82821 | #B70F0F | #FECDC6 | 4.78 | 3.48 | 失败或异常：打印失败、支付失败、同步失败、超时、盘差、已沽清、已退 |

辅助色（新增 4 个色系，色阶规则与主题色相同）：

| 辅助色 | 键 | 色相 | 50 | 100 | 200 | 300 | 400 | 500 | 600 | 700 | 800 | 900 | 950 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 青 | `cyan` | 200° | #ECFAFB | #D7F3F4 | #B4E5E8 | #89D3D7 | #58BBC0 | #1DA2A8 | #00888E | #027074 | #00575B | #004346 | #002F31 |
| 紫罗兰 | `purple` | 312° | #FAF4FF | #F5E6FF | #EBCDFF | #E0ACFF | #CE85F8 | #B762E5 | #9E49CA | #8436AC | #69258A | #501A6A | #39114D |
| 橄榄 | `olive` | 123° | #F4F9EA | #E7F2D3 | #D1E3AE | #B7D081 | #99B750 | #7E9D18 | #688400 | #556C00 | #415400 | #304000 | #212D00 |
| 品红 | `magenta` | 350° | #FFF3F8 | #FFE5F0 | #FFC9E1 | #FFA3CF | #F875B9 | #E24EA0 | #C63288 | #A81E70 | #860D58 | #680843 | #4A042F |

## 三、各实体的状态

### 订单 · Order

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 进行中 | Open | 进行中 | `utensils` | E-01 |
| 待结账 | Ready to pay | 待处理 | `receipt` | E-01 G-13 |
| 部分支付 | Partly paid | 待处理 | `wallet` | G-11 |
| 已结账 | Paid | 已完成 | `badge-check` | E-01 G-13 |
| 挂起 | On hold | 暂存 | `circle-pause` | E-03 E-04 |
| 已取消 | Cancelled | 已关闭 | `ban` | — |
| 已反结账 | Reopened | 变更标记 | `undo-2` | E-07 |
| 合自 | Merged from | 变更标记 | `git-merge` | E-05 |
| 拆自 | Split from | 变更标记 | `split` | D-07 |

### 订单行（菜品） · Order item

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 待下单 | Not sent | 暂存 | `pencil-line` | D-01 F-01 |
| 已下单 | Sent | 等待中 | `send` | F-01 F-07 |
| 制作中 | Cooking | 进行中 | `chef-hat` | F-07 |
| 催菜中 | Rushed | 待处理 | `zap` | D-03 |
| 待上菜 | Ready to serve | 等待中 | `concierge-bell` | F-06 |
| 已上菜 | Served | 已完成 | `check-check` | F-06 F-07 |
| 已退 | Voided | 异常 | `circle-minus` | D-05 |
| 赠送 | Comp | 变更标记 | `gift` | D-04 |
| 转自 | Moved from | 变更标记 | `arrow-right-left` | D-06 |
| 已改价 | Repriced | 变更标记 | `pencil` | D-08 |
| 临时菜 | Custom item | 变更标记 | `file-plus` | C-09 |

### 桌台 · Table

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 空闲 | Free | 已关闭 | `circle` | B-01 |
| 已预订 | Booked | 预约 | `calendar-clock` | B-05 |
| 待点餐 | Seated | 等待中 | `hourglass` | B-03 |
| 用餐中 | Dining | 进行中 | `utensils` | B-02 |
| 超时未上菜 | Dishes late | 待处理 | `timer` | F-05 |
| 待结账 | Ready to pay | 待处理 | `receipt` | B-02 |
| 部分支付 | Partly paid | 待处理 | `wallet` | G-11 |
| 待清台 | To clear | 待处理 | `sparkles` | B-09 |
| 并入 | Merged into | 变更标记 | `git-merge` | B-08 |
| 拼桌 | Shared | 变更标记 | `users` | B-07 |
| 停用 | Disabled | 已关闭 | `ban` | — |

### 外卖与自提 · Delivery & pickup

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 待接单 | New | 待处理 | `bell-ring` | H-01 H-02 |
| 已接单 | Accepted | 进行中 | `chef-hat` | H-02 |
| 待取餐 | Ready for pickup | 等待中 | `package` | H-04 |
| 配送中 | Out for delivery | 等待中 | `bike` | H-04 |
| 已送达 | Delivered | 已完成 | `circle-check` | — |
| 已拒单 | Rejected | 已关闭 | `circle-x` | H-03 |
| 已取消 | Cancelled | 已关闭 | `ban` | — |
| 退款中 | Refunding | 等待中 | `rotate-ccw` | J-04 |
| 接单超时 | Accept timeout | 异常 | `timer-off` | H-02 |

### 支付 · Payment

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 待支付 | Unpaid | 待处理 | `wallet` | G-07 |
| 支付中 | Paying | 等待中 | `loader-circle` | G-07 |
| 结果未知 | Result unknown | 待处理 | `circle-help` | G-07 J-04 |
| 已支付 | Paid | 已完成 | `circle-check` | G-13 |
| 支付失败 | Failed | 异常 | `circle-alert` | G-07 G-10 |
| 退款中 | Refunding | 等待中 | `rotate-ccw` | J-04 |
| 已退款 | Refunded | 已关闭 | `undo-2` | G-11 |

### 厨房工单 · Kitchen ticket

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 新单 | New | 待处理 | `bell-ring` | F-04 |
| 制作中 | Cooking | 进行中 | `chef-hat` | F-04 |
| 加菜 | Added | 变更标记 | `plus` | F-05 |
| 催菜 | Rushed | 待处理 | `zap` | F-05 |
| 超时 | Late | 异常 | `timer-off` | F-05 |
| 已出餐 | Done | 已完成 | `check-check` | F-06 |
| 已撤单 | Withdrawn | 已关闭 | `ban` | D-05 |

### 打印任务 · Print job

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 排队中 | Queued | 等待中 | `printer` | F-02 |
| 已打印 | Printed | 已完成 | `circle-check` | F-02 |
| 打印失败 | Print failed | 异常 | `file-x` | F-03 |
| 补打 | Reprint | 变更标记 | `repeat` | A-04 E-06 |

### 设备与网络 · Devices & network

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 在线 | Online | 已完成 | `wifi` | A-05 |
| 离线 | Offline | 待处理 | `wifi-off` | A-05 A-06 |
| 缺纸 | Out of paper | 待处理 | `triangle-alert` | A-03 |
| 未连接 | Not connected | 已关闭 | `unplug` | A-02 |
| 待同步 | Waiting to sync | 等待中 | `cloud-upload` | A-06 |
| 同步失败 | Sync failed | 异常 | `circle-alert` | A-06 |

### 预订 · Reservation

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 待到店 | Upcoming | 预约 | `calendar-clock` | B-05 |
| 已到店 | Arrived | 已完成 | `calendar-check` | B-05 |
| 未到店 | No-show | 已关闭 | `calendar-x` | B-05 |
| 已取消 | Cancelled | 已关闭 | `ban` | — |

### 库存与沽清 · Stock

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 库存充足 | In stock | 已完成 | `package-check` | C-10 |
| 仅剩 N | Only N left | 待处理 | `triangle-alert` | C-10 |
| 已沽清 | Sold out | 异常 | `circle-slash` | C-10 |
| 限时供应 | Limited hours | 预约 | `clock` | C-02 |
| 已下架 | Off menu | 已关闭 | `archive` | — |

### 渠道同步 · Channel sync

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 已同步 | Synced | 已完成 | `cloud` | C-12 |
| 待同步 | Not synced | 等待中 | `cloud-upload` | C-12 |
| 同步中 | Syncing | 等待中 | `refresh-cw` | C-12 |
| 同步失败 | Sync failed | 异常 | `cloud-alert` | C-12 |
| 未上架 | Not listed | 已关闭 | `cloud-off` | C-12 |

### 班次 · Shift

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 未开班 | Not open | 已关闭 | `lock` | A-01 |
| 营业中 | Open | 进行中 | `store` | A-01 |
| 交班中 | Handing over | 等待中 | `hourglass` | J-05 |
| 已交班 | Handed over | 已完成 | `circle-check` | J-05 |
| 盘差 | Cash variance | 异常 | `scale` | J-03 |

### 审批 · Approval

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 待审批 | Pending | 待处理 | `stamp` | J-08 |
| 已通过 | Approved | 已完成 | `circle-check` | J-08 |
| 已驳回 | Rejected | 异常 | `circle-x` | J-08 |

### 发票 · Invoice

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 待开票 | Requested | 待处理 | `file-text` | G-15 |
| 已开票 | Issued | 已完成 | `file-check` | G-15 |
| 开票失败 | Failed | 异常 | `file-x` | G-15 |

### 会员 · Member

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 已绑定 | Linked | 已完成 | `user-check` | I-01 |
| 余额不足 | Low balance | 待处理 | `wallet` | G-10 |
| 已解绑 | Unlinked | 已关闭 | `user-x` | I-03 |
| 线索会员 | Lead | 暂存 | `user-round-pen` | I-01 |

### 储值单 · Top-up

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 待收款 | Awaiting payment | 待处理 | `wallet` | I-04 |
| 待确认收款 | Payment to confirm | 等待中 | `hourglass` | I-04 |
| 已收款待到账 | Received | 等待中 | `loader-circle` | I-04 |
| 已到账 | Credited | 已完成 | `circle-check` | I-04 |
| 已取消 | Cancelled | 已关闭 | `ban` | I-04 |
| 已拒绝 | Rejected | 异常 | `circle-x` | I-04 |
| 已过期 | Expired | 已关闭 | `timer-off` | I-04 |

### 优惠券 · Coupon

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 可用 | Available | 进行中 | `ticket` | I-06 |
| 已用 | Used | 已关闭 | `ticket-check` | I-06 |
| 已过期 | Expired | 已关闭 | `timer-off` | I-06 |
| 已收回 | Revoked | 异常 | `undo-2` | I-06 |

### 营销活动 · Campaign

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 草稿 | Draft | 暂存 | `pencil-line` | I-05 |
| 进行中 | Running | 进行中 | `megaphone` | I-05 |
| 已暂停 | Paused | 暂存 | `circle-pause` | I-05 |
| 已结束 | Ended | 已关闭 | `flag` | I-05 |

### 积分 · Points

| 状态 | English | 分组 | 图标（Lucide） | 依据 |
|---|---|---|---|---|
| 待生效 | Pending | 等待中 | `hourglass` | I-07 |
| 可用 | Available | 已完成 | `coins` | I-07 |
| 占用 | Held | 暂存 | `lock` | I-07 |
| 退款预留 | Refund reserved | 等待中 | `rotate-ccw` | I-07 |

合计 19 个实体、112 个状态。

## 四、令牌

每组四个令牌：`--c-st-{组}-dot`（图标、圆点）、`--c-st-{组}-text`（文字）、`--c-st-{组}-tint`（chip 浅底）、`--c-st-{组}-border`（装饰描边）。组件按状态所属的组取令牌，不直接用色阶。
