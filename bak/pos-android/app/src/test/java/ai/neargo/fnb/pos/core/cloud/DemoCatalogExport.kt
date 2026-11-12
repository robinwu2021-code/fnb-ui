package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.data.LocalWorkspace
import ai.neargo.fnb.pos.core.data.SnapshotStore
import ai.neargo.fnb.pos.core.data.JournalCommand
import ai.neargo.fnb.pos.core.data.testReceipt
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

/**
 * 给本地模拟服务端生成演示门店（海岸店）的可售目录：菜名、规格名与本机一致。
 * 只在设置环境变量时写文件：EXPORT_DEMO_CATALOG=tools/mock-cloud/demo-catalog.json ./gradlew :app:testDebugUnitTest --tests '*DemoCatalogExport*'
 */
class DemoCatalogExport {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun export() = runTest(kotlinx.coroutines.test.UnconfinedTestDispatcher()) {
        val path = System.getenv("EXPORT_DEMO_CATALOG") ?: return@runTest
        val w = LocalWorkspace(object : SnapshotStore {
            var d: String? = null
            override suspend fun load() = d
            override suspend fun save(json: String) { d = json }
            override suspend fun saveCommand(source: String, command: JournalCommand) = testReceipt(command).also { d = source }
        }, backgroundScope) { 1_800_000_000_000L }
        w.enterDemo(); w.selectStore("s-coast")
        val items = w.current.value!!.products.map { p ->
            CloudCatalogItem("M1", "MI-${p.id}", "G-${p.id}", p.name,
                if (p.skus.isEmpty()) listOf(CloudCatalogSku("S-${p.id}", "", p.price, "CNY")) else p.skus.map { CloudCatalogSku("S-${p.id}-${it.key}", it.label, it.price, "CNY") })
        }
        val cat = CloudCatalog("NS002", listOf(CloudCatalogMenu("M1", "R1", "全天菜单")), items)
        File(path).let { if (it.isAbsolute) it else File(System.getProperty("user.dir")).parentFile.resolve(path) }
            .writeText(Json { prettyPrint = true }.encodeToString(CloudCatalog.serializer(), cat))
    }
}
