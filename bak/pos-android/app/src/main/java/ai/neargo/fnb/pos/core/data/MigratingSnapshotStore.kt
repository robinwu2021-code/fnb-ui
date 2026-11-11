package ai.neargo.fnb.pos.core.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 新库优先；旧库只在新库为空时校验导入，保留原件供恢复核对。 */
internal class MigratingSnapshotStore(
    private val primary: SnapshotStore,
    private val legacy: SnapshotStore,
) : SnapshotStore {
    private val lock = Mutex()
    override suspend fun load(): String? = lock.withLock {
        primary.load()?.let { return@withLock it }
        val source = legacy.load() ?: return@withLock null
        VaultCodec.decode(source)
        primary.save(source)
        source
    }
    override suspend fun saveCommand(source: String, command: JournalCommand): JournalReceipt = lock.withLock { primary.saveCommand(source, command) }
    override suspend fun save(json: String) = lock.withLock { primary.save(json) }
}
