package ai.neargo.fnb.pos.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 迁移与恢复共用校验；不把未知版本或坏数据当成首次启动。 */
internal object VaultCodec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun decode(source: String): Vault {
        val root = json.parseToJsonElement(source).jsonObject
        val version = root["version"]?.let {
            requireNotNull(it.jsonPrimitive.intOrNull) { "本地数据版本无效" }
        } ?: 2
        require(version in 2..3) { "不支持的本地数据版本：$version" }
        val vault = json.decodeFromString(Vault.serializer(), source).copy(version = version)
        require(vault.stores.all { (id, store) -> id == store.id }) { "门店数据标识不一致" }
        require(vault.storeId == null || vault.storeId in vault.stores) { "当前门店数据缺失" }
        return vault
    }
}
