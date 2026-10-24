package ai.neargo.fnb.pos.ui.components

import ai.neargo.fnb.pos.data.Dish
import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImageContent
import com.valentinilk.shimmer.shimmer

/**
 * 菜品图：Coil 3 加载，加载中显示 shimmer 骨架，没有图或加载失败时显示菜名首字。
 */
@Composable
fun DishImage(dish: Dish, modifier: Modifier = Modifier) {
    val c = Pos.colors
    if (dish.image == null) {
        Initial(dish, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = dish.image,
        contentDescription = dish.name,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    ) {
        val state by painter.state.collectAsState()
        when (state) {
            is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty ->
                Box(Modifier.fillMaxSize().shimmer().background(c.faint))
            is AsyncImagePainter.State.Error -> Initial(dish, Modifier.fillMaxSize())
            else -> SubcomposeAsyncImageContent()
        }
    }
}

@Composable
private fun Initial(dish: Dish, modifier: Modifier) {
    val c = Pos.colors
    Box(modifier.background(c.primaryTint), contentAlignment = Alignment.Center) {
        Text(dish.name.take(1), style = PosType.hero, color = c.primaryText)
    }
}
