package ai.neargo.fnb.pos.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 仅包装写入失败，不把校验或编程错误隐藏成存储错误。 */
class WorkspaceWriteException(cause: Exception) : Exception("本机保存失败，操作未完成，请检查存储空间后重试", cause)

/** 无返回值入口的业务拒绝（未落盘、无事件），由页面显示原因，不当作崩溃。 */
class WorkspaceRejectedException(reason: String) : Exception(reason)

/** Unit 入口：写入失败原样抛出，业务拒绝转为 WorkspaceRejectedException。 */
internal fun Result<Unit>.orReject() = getOrElse { throw if (it is WorkspaceWriteException) it else WorkspaceRejectedException(it.message ?: "操作未完成") }

internal suspend fun <T> persistenceResult(block: suspend () -> Result<T>): Result<T> =
    try { block() } catch (e: WorkspaceWriteException) { Result.failure(e) }

/** Unit/计数接口无法返回 Result 时，停止后续成功提示并显示保存失败；取消保持传播。 */
fun CoroutineScope.launchStorage(messages: MessageBus, block: suspend CoroutineScope.() -> Unit) = launch {
    try { block() }
    catch (e: WorkspaceWriteException) { messages.post(requireNotNull(e.message)) }
    catch (e: WorkspaceRejectedException) { messages.post(requireNotNull(e.message)) }
}

internal suspend fun saveSnapshot(store: SnapshotStore, source: String) {
    try { store.save(source) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { throw WorkspaceWriteException(e) }
}
