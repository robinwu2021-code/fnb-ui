package ai.neargo.fnb.pos.core.model

import java.text.Normalizer
import java.util.Locale

/** Catalog aliases are stored with the product, so search needs no network or transliteration service. */
object ProductSearch {
    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

    fun matches(product: Product, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return listOf(product.name, product.en, product.id).any { normalize(it).contains(q) } ||
            product.searchAliases.split(Regex("[,，;；\\s]+"))
                .any { normalize(it).contains(q) }
    }
}
