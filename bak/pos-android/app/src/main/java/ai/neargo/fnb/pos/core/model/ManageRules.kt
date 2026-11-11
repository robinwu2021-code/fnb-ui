package ai.neargo.fnb.pos.core.model

import java.security.MessageDigest

/**
 * 管理配置纯规则（v3.2–v3.4）：字段校验、规格解析、SKU 组合生成、餐牌关联。失败返回可展示的原因。
 */
object ManageRules {
    const val MAX_SKUS = 100


    fun required(v: String, label: String): String? = if (v.isBlank()) "请填写$label" else null

    fun unique(name: String, existing: List<String>, label: String): String? =
        if (existing.any { it.equals(name.trim(), ignoreCase = true) }) "$label「${name.trim()}」已存在" else null

    /** 桌号：字母、数字、短横线，1–8 位，门店内唯一。 */
    fun tableId(id: String, existing: List<String>): String? = when {
        id.isBlank() -> "请填写桌号"
        !Regex("^[A-Za-z0-9-]{1,8}$").matches(id.trim()) -> "桌号只能包含字母、数字、短横线，最多 8 位"
        existing.any { it.equals(id.trim(), ignoreCase = true) } -> "桌号 ${id.trim()} 已存在"
        else -> null
    }

    fun seats(v: String): Result<Int> = v.trim().toIntOrNull()?.takeIf { it in 1..30 }?.let { Result.success(it) }
        ?: Result.failure(IllegalArgumentException("座位数应为 1–30 的整数"))

    /** 金额（元）→ 分：非负、最多两位小数、上限 99999。 */
    fun price(v: String, label: String = "售价"): Result<Long> {
        val s = v.trim()
        if (!Regex("^\\d+(\\.\\d{1,2})?$").matches(s)) return Result.failure(IllegalArgumentException("$label 应为非负金额，最多两位小数"))
        val fen = Money.parse(s, Money.MAX_UNIT) ?: return Result.failure(IllegalArgumentException("$label 不能超过 99999"))
        return Result.success(fen)
    }

    fun stock(v: String, label: String = "库存"): Result<Int> = v.trim().toIntOrNull()?.takeIf { it >= 0 }?.let { Result.success(it) }
        ?: Result.failure(IllegalArgumentException("$label 应为非负整数"))

    /** 规格选项：`名称:加价,名称:加价`，同组名称不重复，加价非负、最多两位小数。返回（名称，加价分）。 */
    fun options(v: String): Result<List<Pair<String, Long>>> {
        val parts = v.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Result.failure(IllegalArgumentException("请至少填写一个选项，格式：标准:0,大份:20"))
        val out = mutableListOf<Pair<String, Long>>()
        for (p in parts) {
            val kv = p.split(':', '：')
            val name = kv[0].trim()
            if (name.isEmpty()) return Result.failure(IllegalArgumentException("选项名称不能为空"))
            if (out.any { it.first == name }) return Result.failure(IllegalArgumentException("选项「$name」重复"))
            val delta = if (kv.size > 1) price(kv[1], "「$name」加价").getOrElse { return Result.failure(it) } else 0L
            out += name to delta
        }
        return Result.success(out)
    }

    /**
     * 生成 SKU：所选规格组按 ID 排序后做笛卡尔组合，最多 100 个。
     * 仍存在的组合保留手动价格与库存；未手动调价的随基础价重算；新组合库存默认取 defaultStock。
     */
    fun skus(groups: List<SpecGroup>, basePrice: Long, defaultStock: Int, old: List<Sku>): Result<List<Sku>> {
        val sorted = groups.filter { it.options.isNotEmpty() }.sortedBy { it.id }
        if (sorted.isEmpty()) return Result.success(emptyList())
        val count = sorted.fold(1L) { acc, g -> acc * g.options.size }
        if (count > MAX_SKUS) return Result.failure(IllegalArgumentException("规格组合 $count 个，超过上限 $MAX_SKUS 个"))
        var combos: List<List<Pair<SpecGroup, SpecOption>>> = listOf(emptyList())
        for (g in sorted) combos = combos.flatMap { c -> g.options.map { o -> c + (g to o) } }
        val byKey = old.associateBy { it.key }
        return Result.success(combos.map { c ->
            val key = c.joinToString("|") { (g, o) -> "${g.id}:${o.id}" }
            val auto = basePrice + c.sumOf { it.second.delta }
            val prev = byKey[key]
            Sku(
                key = key, optionIds = c.map { it.second.id }, label = c.joinToString(" · ") { it.second.name },
                price = if (prev?.manualPrice == true) prev.price else auto,
                stock = prev?.stock ?: defaultStock,
                manualPrice = prev?.manualPrice ?: false, manualStock = prev?.manualStock ?: false,
            )
        })
    }

    /** 点餐时按每组所选选项找到 SKU。 */
    fun findSku(product: Product, optionIds: List<String>): Sku? = product.skus.firstOrNull { it.optionIds.toSet() == optionIds.toSet() }

    /** 替换某商品的餐牌关联，不影响其他商品、不重复；因解除而变空的餐牌自动停用。 */
    fun relink(menus: List<Menu>, productId: String, menuIds: Set<String>): List<Menu> = menus.map { m ->
        val has = productId in m.productIds
        val want = m.id in menuIds
        val ids = when {
            want && !has -> m.productIds + productId
            !want && has -> m.productIds - productId
            else -> m.productIds
        }
        m.copy(productIds = ids, published = m.published && ids.isNotEmpty())
    }

    fun canPublish(menu: Menu): String? = if (menu.productIds.isEmpty()) "空餐牌不能发布，请先添加商品" else null

    // —— 账号 ——
    fun email(v: String): String? = if (Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(v.trim())) null else "请输入正确的邮箱"
    fun password(v: String): String? = if (v.length >= 8) null else "密码至少 8 位"
    fun hash(password: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$password".toByteArray()).joinToString("") { "%02x".format(it) }
}
