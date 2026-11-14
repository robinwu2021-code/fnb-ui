package ai.neargo.fnb.pos.core.data

/** 测试适配器显式声明命令能力：内存中视为协调方已提交（版本 = 期望 + 1）。 */
internal fun testReceipt(c: JournalCommand) =
    JournalReceipt(ReceiptStatus.Committed, "evt-${c.commandId}", c.aggregates.associate { it.id to it.expected + 1 }, "committed", c.occurredAt)
