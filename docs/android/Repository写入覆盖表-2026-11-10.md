# Repository 写入覆盖表

核对日期：2026-11-10；N1-02、N1-03 更新于 2026-11-11；N2 起事件按聚合划分（订单/桌台/预订/目录/库存/交班），信封见[命令协议与恢复实施记录](命令协议与恢复实施记录-2026-11-11.md)。覆盖当前Repositories.kt及仓库外入口。已有事件仅指本地页面命令流，不代表真实同步或设备回执。待补项本轮不实施。

## SessionRepository

| 入口 | 当前分类 | 事件或后续归属 |
| --- | --- | --- |
| `login` | 仅本机 | 本机账号/会话/选择；N4安全审计另做 |
| `register` | 仅本机 | 本机账号/会话/选择；N4安全审计另做 |
| `enterDemo` | 仅本机 | 本机账号/会话/选择；N4安全审计另做 |
| `logout` | 仅本机 | 本机账号/会话/选择；N4安全审计另做 |
| `selectStore` | 仅本机 | 本机账号/会话/选择；N4安全审计另做 |
| `selectTable` | 仅本机 | 本机账号/会话/选择；N4安全审计另做 |

## StoreConfigRepository

| 入口 | 当前分类 | 事件或后续归属 |
| --- | --- | --- |
| `addArea` | 已有事件 | AreaCreated |
| `saveTable` | 已有事件 | TableConfigCreated / TableConfigUpdated |
| `addCategory` | 已有事件 | CategoryCreated |
| `addSpecGroup` | 已有事件 | SpecGroupCreated |
| `saveProduct` | 已有事件 | ProductCreated / ProductUpdated（含餐牌关联变化） |
| `adjustStock` | 已有事件 | StockAdjusted + 库存流水 |
| `saveMenu` | 已有事件 | MenuCreated / MenuUpdated |
| `setPublished` | 已有事件 | MenuPublished / MenuUnpublished |
| `saveSettings` | 已有事件 | StoreSettingsUpdated |

## FloorRepository

| 入口 | 当前分类 | 事件或后续归属 |
| --- | --- | --- |
| `createReservation` | 已有事件 | ReservationCreated（含预订记录） |
| `cancelReservation` | 已有事件 | ReservationCancelled（含预订记录） |
| `checkInReservation` | 已有事件 | ReservationCheckedIn（开台与预订同事务） |
| `openTable` | 已有事件 | TableOpened |
| `settle` | 已有事件 | PaymentRecorded |
| `startCleaning` | 已有事件 | TableCleaningStarted |
| `finishCleaning` | 已有事件 | TableCleaningFinished |
| `setPosition` | 已有事件 | TablePositionChanged |
| `changeTable` | 已有事件 | TableChanged |
| `mergeTable` | 已有事件 | TableMerged |
| `splitLines` | 已有事件 | OrderSplit |

## OrderRepository

| 入口 | 当前分类 | 事件或后续归属 |
| --- | --- | --- |
| `addDish` | 已有事件 | DishAdded |
| `authorizeAdjustment` | 已有事件 | DishVoided / DishGifted / OrderDiscounted |
| `setBenefits` | 已有事件 | OrderBenefitsChanged |
| `setLineNote` | 已有事件 | DishNoteChanged（含变化行） |
| `setOrderNote` | 已有事件 | OrderNoteChanged |
| `hold` | 已有事件 | OrderHeld |
| `retrieve` | 已有事件 | OrderRetrieved |
| `changeQty` | 已有事件 | DishQuantityChanged |
| `clearNew` | 已有事件 | OrderDraftCleared |
| `sendToKitchen` | 已有事件 | KitchenSubmitted |
| `advance` | 已有事件 | DishRushed / DishServed（含变化行） |
| `setMember` | 已有事件 | OrderMemberChanged |

## SyncRepository

| 入口 | 当前分类 | 事件或后续归属 |
| --- | --- | --- |
| `syncNow` | 设备任务日志 | SyncAttempt（simulation）；不进营业事件，N3/N6/N8接真实回执 |

## PrintRepository

| 入口 | 当前分类 | 事件或后续归属 |
| --- | --- | --- |
| `preBill` | 设备任务日志 | PrintRequested（local）；不进营业事件，N3/N6/N8接真实回执 |
| `reprint` | 设备任务日志 | PrintReprinted（local，记原任务）；不进营业事件，N3/N6/N8接真实回执 |
| `process` | 设备任务日志 | PrintAttempt（simulation）；不进营业事件，N3/N6/N8接真实回执 |
| `billPreview` | 只读 | 仅文本预览，不入队 |

## ShiftRepository

| 入口 | 当前分类 | 事件或后续归属 |
| --- | --- | --- |
| `closeShift` | 已有事件 | ShiftClosed（固化记录与账本范围） |

## PreferencesRepository

| 入口 | 当前分类 | 事件或后续归属 |
| --- | --- | --- |
| `update` | 仅本机 | 偏好、观察/演示开关或瞬时提示，不进营业日志 |

## DeviceRepository

| 入口 | 当前分类 | 事件或后续归属 |
| --- | --- | --- |
| `setForceOffline` | 仅本机 | 偏好、观察/演示开关或瞬时提示，不进营业日志 |
| `setPrinterOk` | 仅本机 | 偏好、观察/演示开关或瞬时提示，不进营业日志 |

## MessageBus

| 入口 | 当前分类 | 事件或后续归属 |
| --- | --- | --- |
| `post` | 仅本机 | 偏好、观察/演示开关或瞬时提示，不进营业日志 |

## 部分覆盖及业务差距

- `checkInReservation`已改为单事务产生独立的ReservationCheckedIn，只标记该条预订到店；直接开台仍用TableOpened，并把该桌待到店预订标为到店。
- `setPosition`是门店桌台布局，后续应同步配置，不等同于个人字体/布局偏好。
- `adjustStock`已写库存流水（前后数量、差量、原因、人员）；`saveProduct`事件含商品与餐牌关联的前后内容。
- `closeShift`产生ShiftClosed：固化交班记录、本班收款/调整ID、交班单任务、未同步项。
- `preBill/reprint`只写本机任务日志（补打记原任务ID）；创建任务不代表出纸。
- `process/syncNow`为模拟执行，每次状态变化写入来源为simulation的任务日志；不能把其成功标志升级为真实回执，也不确认command_events。
- `advance`是本地催菜/上菜事件，不是KDS接单；`setMember/setBenefits`已有事件，但仍为本机演示，不代表资产已核验。
- 当前账号/密码哈希、设备密钥及个人显示偏好不得混入营业事件。

## Repository外的写入口

| 入口 | 分类及限制 |
| --- | --- |
| `LocalWorkspace.resetDemo` | 仅演示管理；保留账号及命令版本，不是生产清库/恢复工具 |
| `LocalWorkspace.restore` | 恢复/迁移，不能制造历史营业事件；坏库不得回退演示 |
| `LocalMedia.import` | 本机文件写入，不代表商品保存或云上传成功 |
| `SnapshotStore.save/saveCommand` | 持久化边界；当前默认命令保存兼容实现仅存快照，N2需明确能力、避免生产降级 |
| `MigratingSnapshotStore.load` | 新库为空才导入旧库，保留原件，导入不得重执历史副作用 |
| `CommandJournal.execute` | 业务、事件、Inbox同事务保存；仅已接入命令适用 |
| `CommandJournal.ack` | 远端持久确认后才调用，不得由online开关触发 |
| `DataStorePreferences.update` | PreferencesRepository的本机偏好实现 |
| `NetworkDevice`回调 | 本机网络观察，不是云/厨房的业务确认 |

## 完成口径

- 已覆盖接口中的47个写/状态操作入口及1个只读方法billPreview；Flow属性均为观察入口。
- 待营业事件、仅本机及模拟设备日志明确区分；尚不能以事件重建完整门店。
- 管理、库存、交班、设备事件需专属载荷，不能伪造tableId套入现有页面构造器。
- 后续新增接口必须同步更新此表；每项完成需有事务及失败回滚验证。

依据：[实施方案](../architecture/下一阶段实施方案-2026-11-10.md)、[接口源码](../../bak/pos-android/app/src/main/java/ai/neargo/fnb/pos/core/data/Repositories.kt)。
