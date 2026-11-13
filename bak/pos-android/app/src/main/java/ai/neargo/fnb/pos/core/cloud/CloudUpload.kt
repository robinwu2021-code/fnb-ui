package ai.neargo.fnb.pos.core.cloud

import ai.neargo.fnb.pos.core.data.JournalEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.ZoneId

/** 上传一条事件所需的环境：每轮回放新建一份，同一轮里各上传器共享接口与映射表 */
internal class UploadContext(
    val api: CloudApi, val refs: CloudRefs, val storeNo: String, val zone: ZoneId, val currency: String,
    /** 员工下单的买家号（真实后端要求买家号或会话号之一；散客方案待后端定） */
    val buyerNo: String,
    /** 当前门店是零售（进销存单据上传）；餐饮门店的库存调整只在本机 */
    val inventory: Boolean,
)

/**
 * 事件上传器：一个业态（或公共）负责一组事件类型。同步流水线（顺序、阻塞、需处理、确认）只写一份在 [CloudSync]，
 * 新增业态或新接口只加一个上传器，不改流水线。
 * - [types]：会上传的事件；[localOnly]：只在本机（直接标已处理，不挡同单后续）；[config]：配置类，不走营业回放。
 * - 都不属于的事件 = 等服务端接口，留在本机。
 */
internal interface EventUploader {
    val types: Set<String>
    val localOnly: Set<String> get() = emptySet()
    val config: Set<String> get() = emptySet()
    /** 这条事件是否只在本机处理（直接标已处理） */
    fun handledLocally(e: JournalEvent): Boolean = e.type in localOnly || e.type in config
    /** 需处理 / 等后端时挡住哪些后续事件的键；null = 交给下一个上传器，最后回退按事件主聚合 */
    fun blockKey(e: JournalEvent, p: JsonObject?): String? = null
    /** 需处理里显示的对象（桌号、单号…）；null = 不认识这类事件 */
    fun label(e: JournalEvent, p: JsonObject): String? = null
    /** 本轮有这个上传器的事件时先做的准备（如餐饮拉桌台映射）；网络 / 登录问题抛出让整轮停下 */
    suspend fun prepare() {}
    /** 上传一条事件：[key] 是幂等键（事件 ID，或人工重试的「事件ID#n」）；业务拒绝抛 [CloudException]，暂时不能上传抛 [WaitForBackend] */
    suspend fun upload(e: JournalEvent, p: JsonObject, key: String)
}

/** 上传器基类：把环境里常用的东西摆出来 */
internal abstract class Uploader(protected val ctx: UploadContext) : EventUploader {
    protected val api get() = ctx.api
    protected val refs get() = ctx.refs
    protected val storeNo get() = ctx.storeNo
    protected val zone get() = ctx.zone
    protected val currency get() = ctx.currency
    protected val buyerNo get() = ctx.buyerNo
    protected val json = Json { ignoreUnknownKeys = true }
}

internal fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content.orEmpty()
internal fun JsonObject.obj(k: String) = this[k]!!.jsonObject
internal fun JsonObject.num(k: String) = this[k]?.jsonPrimitive?.long ?: 0L
