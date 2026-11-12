package ai.neargo.fnb.pos.feature.order

import ai.neargo.fnb.pos.core.cloud.CloudIssue
import ai.neargo.fnb.pos.core.cloud.CloudPriceDiff
import ai.neargo.fnb.pos.core.cloud.CloudState
import ai.neargo.fnb.pos.core.model.LineStatus
import ai.neargo.fnb.pos.core.model.OrderLine
import ai.neargo.fnb.pos.core.model.TableOrder
import org.junit.Assert.*
import org.junit.Test

/** 账单上的云端提示：只算本单的需处理与本单批次的价差 */
class CloudNoteTest {
    private fun line(uid: Long, batch: String?) = OrderLine(uid, "d", "菜", "", 1000, 1, LineStatus.Sent, batchId = batch)
    private val order = TableOrder(listOf(line(1, "K-1"), line(2, "K-2"), line(3, null)), orderId = "O-1")

    @Test fun nothingWhenClean() {
        assertNull(cloudNoteOf(order, CloudState(priceDiffs = listOf(CloudPriceDiff("K-9", 1, 2)))))
        assertNull(cloudNoteOf(TableOrder(), CloudState()))
    }

    @Test fun sumsOwnBatchesAndIssues() {
        val cs = CloudState(
            issues = listOf(CloudIssue("e1", "KitchenSubmitted", "order:O-1", 10400, "x", 0), CloudIssue("e2", "TableOpened", "order:O-2", 10400, "y", 0)),
            priceDiffs = listOf(CloudPriceDiff("K-1", 1000, 1100), CloudPriceDiff("K-2", 2000, 1900), CloudPriceDiff("K-9", 5, 6)),
        )
        val n = cloudNoteOf(order, cs)!!
        assertEquals(1, n.issues); assertEquals(3000, n.localMinor); assertEquals(3000, n.remoteMinor); assertEquals(0, n.diffMinor)
    }
}
