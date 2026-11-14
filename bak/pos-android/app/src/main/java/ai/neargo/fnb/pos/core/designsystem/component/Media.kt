package ai.neargo.fnb.pos.core.designsystem.component

import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.valentinilk.shimmer.shimmer

/** 商品图：Coil 3 加载，加载中 shimmer；无图或失败显示线性餐具占位。只接收 URL，不依赖业务实体。 */
@Composable
fun ProductImage(url: String?, contentDescription: String, modifier: Modifier = Modifier) {
    if (url == null) {
        ImagePlaceholder(modifier)
        return
    }
    SubcomposeAsyncImage(model = url, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = modifier) {
        val state by painter.state.collectAsState()
        when (state) {
            is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty -> Box(Modifier.fillMaxSize().shimmer().background(Pos.colors.control))
            is AsyncImagePainter.State.Error -> ImagePlaceholder(Modifier.fillMaxSize())
            else -> SubcomposeAsyncImageContent()
        }
    }
}

@Composable
fun ImagePlaceholder(modifier: Modifier) {
    Box(modifier.background(Pos.colors.control), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Restaurant, null, tint = Pos.colors.sub, modifier = Modifier.size(32.dp))
    }
}
