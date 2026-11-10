# POS 本地持久化第一阶段实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 业务操作在持久保存后才报告完成，业务快照与现有待同步、打印任务在 SQLite 中原子提交，并保留旧 DataStore 快照迁移。

**Architecture:** 保留 Repository 和当前 Vault v3 数据契约。通过 SnapshotStore 适配器分离 SQLite 存储，事务内保存门店快照与独立队列表；先完善单机提交保证，再推进领域事件、Inbox 和真实同步。

**Tech Stack:** Kotlin、协程、Android SQLiteOpenHelper、JUnit；不新增生产依赖。

**Spec:** docs/architecture/多POS离线局域网与云端协同方案.md；docs/architecture/同步节点与事件契约.md

## Global Constraints

- 离线授权默认短期7天、长期90天；此阶段不签发凭证或改变已有登录流程。
- 保留当前未提交功能与 v2/v3 迁移数据；不清库、不自动提交或推送。
- 本阶段只完成本机持久化基础，不声称实现主副 POS、真实云同步或设备回执。
- 当前分支 android-ui-v3 有前序未提交功能，原地继续，避免新 worktree 遗漏这些依赖。

## Review Focus

- 保存挂起或失败时，界面不能发布未保存状态。
- 并发加菜或开台不能使用过时状态覆盖另一操作。
- 取消协程不能留下磁盘已保存、内存未发布的分叉。
- 旧快照无效或版本过高时不能静默回退演示并覆盖数据。
- SQLite 队列与快照必须一起提交或回滚，迁移旧数据只发生一次。

### Task 1: 提交边界与旧数据校验

**Files:** LocalWorkspace.kt、WorkspaceDurabilityTest.kt（均在 bak/pos-android/app/src 对应 main/test 的 core/data 下）。
**Interfaces:** SnapshotStore.load/save 保持不变；LocalWorkspace.restore 对无效数据报错，commit 持久完成后发布；storeOp/mutate 在同一锁内校验及修改。

- [x] 编写延迟写、失败写、立即重启、并发加菜/开台、取消与无效恢复测试。
- [x] 运行指定测试，确认旧代码失败。
- [x] 实现先保存后发布、事务锁内校验、失败不更新内存及恢复失败保护。
- [x] 运行指定测试确认通过。

### Task 2: SQLite 存储与迁移

**Files:** 新建 SqliteSnapshotStore.kt、MigratingSnapshotStore.kt 及其测试；修改 AppContainer.kt。
**Interfaces:** SqliteSnapshotStore 实现 SnapshotStore，SQLite 事务保存 vault 元数据、门店数据、outbox 和打印任务；MigratingSnapshotStore 优先新库，无新库时校验并导入旧库，旧库保留不删除。

- [x] 先编写迁移优先级、失败重试、坏数据及 SQLite 原子恢复测试并确认失败。
- [x] 实现适配器并接入 AppContainer，IO 放到 Dispatchers.IO。
- [x] 验证旧快照迁移、重启恢复、事务回滚及重复迁移。

### Task 3: 验证与交接

- [x] 执行完整单元测试、debug 编译及 SQLite 设备集成测试。
- [x] 独立审阅本轮改动，处理影响数据正确性的问题。
- [x] 更新架构现状与执行记录，明确领域事件、Inbox 和真实同步仍待实现。

本轮不提交 Git：前序大量未提交工作需保留，任务完成后提供可审阅结果。

验证结果：70项单元测试通过；Debug及设备测试APK编译通过；emulator-5556上3项SQLite集成测试通过。独立审阅指出的写入异常逃逸问题已修复，增加失败返回及成功提示拦截回归测试。
