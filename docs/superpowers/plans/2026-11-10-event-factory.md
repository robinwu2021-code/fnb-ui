# 写入覆盖表与事件构造器拆分

> **For agentic workers:** Use superpowers:executing-plans to implement task by task.

**Goal:** 完成N1-01/N1-04，将现有页面事件载荷构造从LocalWorkspace提取为可测试的纯函数，不扩展事件覆盖或改变事务。
**Spec:** docs/architecture/下一阶段实施方案-2026-11-10.md
**Architecture:** 类型化兼容载荷由BusinessEventFactory根据同店before/after生成；LocalWorkspace保留锁、版本、命令ID、数据库保存与发布。当前载荷仍属于legacy页面事件，不假装覆盖无tableId的管理事件。
**Tech Stack:** Kotlin、kotlinx.serialization、JUnit、现有SQLite设备测试。

## Constraints / Review Focus

- 当前混合未提交工作原地保留，不自动提交/推送；不更改schema或信封，不放开经营策略。
- 缺失目标桌/门店不一致必须拒绝；保留多桌、票据、任务、支付/审计字段与省略空optional字段的兼容行为。
- 覆盖表区分真实营业事件、模拟执行和本机信息；间接开台不等于预订事件完整。
- 验证订单仅变化也进入affectedTables；保持原实现选择目标桌台最后匹配支付/审计的语义；更严格新增证据校验留给N2。

### Task 1：覆盖表

- [x] 核对Repositories.kt所有写入口及resetDemo/restore/设备偏好入口，写docs/android/Repository写入覆盖表-2026-11-10.md。
- [x] 脚本对照方法列表，确认无遗漏，状态与源码相符。

### Task 2：构造器

Files: 新建core/data/BusinessEventFactory.kt；修改LocalWorkspace.kt；新增test/core/data/BusinessEventFactoryTest.kt。
Interfaces: build(type,tableId,before,after): BusinessEventPayload；encode(payload): String。载荷类型包括affectedTables及挂单/打印/待发增量。

- [x] 先写测试覆盖跨桌/仅订单变更、票据增删及重复任务过滤、支付/审计optional字段、作用域拒绝和稳定序列化字段；执行观察缺少构造器导致失败。
- [x] 提取类型化载荷与纯构造器，替换LocalWorkspace内联构造，不修改事务与规则。
- [x] 全量单元、Debug编译、现有12项SQLite设备测试通过。
- [x] 独立审阅并更新任务入口。
