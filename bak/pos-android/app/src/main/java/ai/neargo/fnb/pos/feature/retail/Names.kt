package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.core.designsystem.component.LocalLang
import ai.neargo.fnb.pos.core.model.CartLine
import ai.neargo.fnb.pos.core.model.Category
import ai.neargo.fnb.pos.core.model.Lang
import ai.neargo.fnb.pos.core.model.Product
import androidx.compose.runtime.Composable

/**
 * 零售页面里的商品、分类名按界面语言取：英文界面有英文名就用英文名（商品资料里的 en），没有就用中文名。
 * 商品名是门店数据，不交给翻译词典逐字翻（逐字翻会出现「Bag (中)」这种半中半英）。
 */
@Composable fun nameOf(p: Product): String = if (LocalLang.current == Lang.En && p.en.isNotBlank()) p.en else p.name
@Composable fun nameOf(c: Category): String = if (LocalLang.current == Lang.En && c.en.isNotBlank()) c.en else c.name

/** 购物车行名：临时商品用手填名（规格在行的说明里另外显示） */
@Composable fun lineTitle(state: RetailPosUiState, l: CartLine): String =
    l.customName ?: state.product(l.productId)?.let { nameOf(it) } ?: l.productId
