package ai.neargo.fnb.pos.feature.manage

import ai.neargo.fnb.pos.core.data.launchStorage
import ai.neargo.fnb.pos.core.data.DeviceRepository
import ai.neargo.fnb.pos.core.data.LocalMedia
import ai.neargo.fnb.pos.core.data.MessageBus
import ai.neargo.fnb.pos.core.data.SessionRepository
import ai.neargo.fnb.pos.core.data.StoreConfigRepository
import ai.neargo.fnb.pos.core.model.DeviceStatus
import ai.neargo.fnb.pos.core.model.ManageRules
import ai.neargo.fnb.pos.core.model.Product
import ai.neargo.fnb.pos.core.model.Sku
import ai.neargo.fnb.pos.core.model.StoreData
import ai.neargo.fnb.pos.core.model.StoreSettings
import ai.neargo.fnb.pos.core.model.fenToInput
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** 系统管理菜单树（v3.3）：一级分组 → 二级页面。ID 稳定，不依赖文案。 */
enum class Section(val group: String, val label: String) {
    Tables("门店运营", "桌台管理"), Areas("门店运营", "桌台区域"), Store("门店运营", "门店与营业"),
    Categories("商品管理", "商品分类"), Products("商品管理", "商品资料"), Specs("商品管理", "规格组"), Stock("商品管理", "库存盘点"), Menus("商品管理", "餐牌管理"),
    Devices("设备与系统", "终端与打印"), Payments("设备与系统", "收款与费用"), Staff("设备与系统", "员工与权限"), Display("设备与系统", "界面显示"), Lan("设备与系统", "主副 POS"), Auth("设备与系统", "授权中心"), Data("设备与系统", "数据与恢复"), Gallery("设备与系统", "组件验收"),
    SwitchStore("账号与门店", "切换门店"), Account("账号与门店", "账号登录"),
}

/** 图片状态：empty → reading → ready / error（v3.4）。 */
enum class ImageState { Empty, Reading, Ready, Error }

/**
 * 商品编辑草稿：保存成功才写入门店数据；取消不落盘。SKU 随基础价 / 规格组重算，保留手动值。
 * 输入以文本保存，校验错误按字段显示，不清空已填内容。
 */
data class ProductDraft(
    val id: String,
    val isNew: Boolean,
    val name: String = "",
    val en: String = "",
    val searchAliases: String = "",
    val categoryId: String = "",
    val image: String? = null,
    val imageState: ImageState = ImageState.Empty,
    val imageError: String? = null,
    val imageToken: Int = 0,
    val price: String = "",
    /** 新商品默认可售 99 份，避免一建好就沽清。 */
    val stock: String = "99",
    val unlimited: Boolean = false,
    val groupIds: Set<String> = emptySet(),
    val skus: List<Sku> = emptyList(),
    val skuPrice: Map<String, String> = emptyMap(),
    val skuStock: Map<String, String> = emptyMap(),
    val menuIds: Set<String> = emptySet(),
    val hot: Boolean = false,
    val tags: List<String> = emptyList(),
    val mealGroups: List<ai.neargo.fnb.pos.core.model.MealGroup> = emptyList(),
    val mealPrices: Map<String, String> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    /** 有未保存改动：关闭前需确认放弃。 */
    val dirty: Boolean = false,
    val confirmDiscard: Boolean = false,
)

data class ManageUiState(
    val store: StoreData? = null,
    val section: Section = Section.Tables,
    val device: DeviceStatus = DeviceStatus(),
    val user: String? = null,
    val draft: ProductDraft? = null,
)

class ManageViewModel(
    private val config: StoreConfigRepository,
    private val session: SessionRepository,
    device: DeviceRepository,
    private val media: LocalMedia,
    private val messages: MessageBus,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val draft = MutableStateFlow<ProductDraft?>(null)

    val state: StateFlow<ManageUiState> = combine(config.current, saved.getStateFlow("section", Section.Tables.name), device.status, session.session, draft) { s, sec, d, ss, dr ->
        ManageUiState(s, Section.valueOf(sec), d, ss.user, dr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUiState())

    fun select(section: Section) { saved["section"] = section.name }

    /** 通用保存：成功提示并回调 null；失败把原因交回表单就地显示。 */
    fun save(ok: String, op: suspend StoreConfigRepository.() -> Result<Unit>, done: (String?) -> Unit) = viewModelScope.launchStorage(messages) {
        val r = config.op()
        r.onSuccess { messages.post(ok) }
        done(r.exceptionOrNull()?.message)
    }

    fun saveSettings(s: StoreSettings, done: (String?) -> Unit) = save("门店信息已保存", { saveSettings(s) }, done)
    fun logout() = viewModelScope.launchStorage(messages) { session.logout() }
    fun notice(msg: String) = messages.post(msg)

    // —— 商品编辑 ——
    fun newProduct() {
        val st = state.value.store ?: return
        draft.value = ProductDraft(id = "p-" + UUID.randomUUID().toString().take(8), isNew = true, categoryId = st.categories.firstOrNull()?.id.orEmpty())
    }

    fun editProduct(p: Product) {
        val st = state.value.store ?: return
        draft.value = ProductDraft(
            id = p.id, isNew = false, name = p.name, en = p.en, searchAliases = p.searchAliases, categoryId = p.categoryId, image = p.image,
            imageState = if (p.image != null) ImageState.Ready else ImageState.Empty,
            price = fenToInput(p.price), stock = (p.stock ?: 0).toString(), unlimited = !p.hasSpec && p.stock == null,
            groupIds = p.specGroupIds.toSet(), skus = p.skus,
            skuPrice = p.skus.associate { it.key to fenToInput(it.price) }, skuStock = p.skus.associate { it.key to it.stock.toString() },
            menuIds = st.menus.filter { p.id in it.productIds }.map { it.id }.toSet(), hot = p.hot, tags = p.tags, mealGroups = p.mealGroups,
        )
    }

    fun closeEditor() { draft.value = null }

    /** 请求关闭：有改动先确认，避免返回键 / 点遮罩丢失草稿。 */
    fun requestClose() = draft.update { d -> if (d?.dirty == true) d.copy(confirmDiscard = true) else null }
    fun keepEditing() = draft.update { it?.copy(confirmDiscard = false) }

    fun edit(f: (ProductDraft) -> ProductDraft) = draft.update { d -> d?.let(f)?.copy(errors = emptyMap(), dirty = true) }

    /** 基础价或规格组变化后重算 SKU（保留仍存在组合的手动值）。 */
    fun regenerate() {
        val st = state.value.store ?: return
        draft.update { d ->
            d ?: return@update null
            val base = ManageRules.price(d.price).getOrNull() ?: return@update d
            val groups = st.specGroups.filter { it.id in d.groupIds }
            val defaultStock = d.stock.toIntOrNull() ?: 0
            ManageRules.skus(groups, base, defaultStock, d.skus).fold(
                onSuccess = { list ->
                    d.copy(
                        skus = list,
                        skuPrice = list.associate { it.key to (if (it.manualPrice) d.skuPrice[it.key] ?: fenToInput(it.price) else fenToInput(it.price)) },
                        skuStock = list.associate { it.key to (d.skuStock[it.key] ?: it.stock.toString()) },
                        errors = emptyMap(),
                    )
                },
                onFailure = { d.copy(errors = mapOf("skus" to it.message!!)) },
            )
        }
    }

    fun editSkuPrice(key: String, v: String) = edit { d -> d.copy(skuPrice = d.skuPrice + (key to v), skus = d.skus.map { if (it.key == key) it.copy(manualPrice = true) else it }) }
    fun editSkuStock(key: String, v: String) = edit { d -> d.copy(skuStock = d.skuStock + (key to v), skus = d.skus.map { if (it.key == key) it.copy(manualStock = true) else it }) }

    /** 本地图片：读取中阻止保存；替换 / 移除 / 关闭使旧读取结果失效（token）。 */
    fun pickImage(uri: Uri) {
        val token = (draft.value?.imageToken ?: 0) + 1
        edit { it.copy(imageState = ImageState.Reading, imageError = null, imageToken = token) }
        viewModelScope.launchStorage(messages) {
            val r = media.import(uri)
            draft.update { d ->
                if (d == null || d.imageToken != token) return@update d
                r.fold({ d.copy(image = it, imageState = ImageState.Ready) }, { d.copy(imageState = ImageState.Error, imageError = it.message) })
            }
        }
    }

    fun imageUrl(url: String) = edit { it.copy(image = url.ifBlank { null }, imageState = if (url.isBlank()) ImageState.Empty else ImageState.Ready, imageToken = it.imageToken + 1) }
    fun removeImage() = edit { it.copy(image = null, imageState = ImageState.Empty, imageError = null, imageToken = it.imageToken + 1) }

    fun saveProduct() {
        val d = draft.value ?: return
        if (d.saving) return
        val errors = mutableMapOf<String, String>()
        val mealGroups = d.mealGroups.map { g -> g.copy(choices = g.choices.map { c ->
            val input = d.mealPrices["${g.id}:${c.id}"] ?: fenToInput(c.delta)
            val delta = ManageRules.price(input, "换菜加价").getOrElse { errors["mealGroups"] = it.message!!; c.delta }
            c.copy(name = c.name.trim(), delta = delta)
        }, name = g.name.trim()) }
        ai.neargo.fnb.pos.core.model.MealRules.validate(mealGroups)?.let { errors["mealGroups"] = it }
        ManageRules.required(d.name, "商品名称")?.let { errors["name"] = it }
        ManageRules.required(d.en, "英文名称")?.let { errors["en"] = it }
        if (d.categoryId.isBlank()) errors["category"] = "请选择分类"
        if (d.imageState == ImageState.Reading) errors["image"] = "图片读取中，请稍候再保存"
        if (d.image != null && !d.image.startsWith("http") && !d.image.startsWith("file:")) errors["image"] = "图片地址需以 http(s) 开头"
        val price = ManageRules.price(d.price).getOrElse { errors["price"] = it.message!!; 0 }
        val stock = if (d.unlimited || d.skus.isNotEmpty()) null else ManageRules.stock(d.stock).getOrElse { errors["stock"] = it.message!!; 0 }
        if (d.groupIds.isNotEmpty() && d.skus.isEmpty()) errors["skus"] = "请先生成规格组合"
        val skus = d.skus.map { s ->
            val p = ManageRules.price(d.skuPrice[s.key] ?: "", "${s.label} 售价").getOrElse { errors["skus"] = it.message!!; s.price }
            val q = ManageRules.stock(d.skuStock[s.key] ?: "", "${s.label} 库存").getOrElse { errors["skus"] = it.message!!; s.stock }
            s.copy(price = p, stock = q)
        }
        if (errors.isNotEmpty()) { draft.value = d.copy(errors = errors); return }
        val product = Product(
            id = d.id, name = d.name.trim(), en = d.en.trim(), searchAliases = d.searchAliases.trim(), categoryId = d.categoryId, price = price, stock = stock,
            specGroupIds = d.groupIds.sorted(), skus = skus, image = d.image, tags = d.tags, hot = d.hot, mealGroups = mealGroups,
        )
        draft.value = d.copy(saving = true)
        viewModelScope.launchStorage(messages) {
            config.saveProduct(product, d.menuIds)
                .onSuccess { messages.post(if (d.isNew) "已新建商品 ${product.name}" else "已保存 ${product.name}"); draft.value = null }
                .onFailure { draft.value = d.copy(saving = false, errors = mapOf("name" to (it.message ?: "保存失败"))) }
        }
    }
}
