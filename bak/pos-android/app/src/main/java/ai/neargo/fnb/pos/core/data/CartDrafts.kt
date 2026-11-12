package ai.neargo.fnb.pos.core.data

import ai.neargo.fnb.pos.core.model.CartLine
import kotlinx.serialization.Serializable

/**
 * 收银台正在扫的购物车（本机草稿）：每台设备、每家门店一份，存在本机，不进门店账本。
 * App 被杀、重启、断电后能接着收；收款成功或清空后删掉。取单取回来的挂单也靠它保住。
 */
@Serializable
data class CartDraft(
    val lines: List<CartLine>,
    val member: Boolean = false,
    val memberId: String = "",
    val discountPct: Int = 100,
    val promoIds: List<String> = emptyList(),
    val approver: String = "",
)

interface CartDraftStore {
    suspend fun load(storeId: String): CartDraft?
    /** null 或空购物车 = 删除 */
    suspend fun save(storeId: String, draft: CartDraft?)
}

/** 内存版（单测、预览用） */
class MemoryCartDrafts : CartDraftStore {
    val saved = mutableMapOf<String, CartDraft>()
    override suspend fun load(storeId: String) = saved[storeId]
    override suspend fun save(storeId: String, draft: CartDraft?) {
        if (draft == null || draft.lines.isEmpty()) saved.remove(storeId) else saved[storeId] = draft
    }
}
