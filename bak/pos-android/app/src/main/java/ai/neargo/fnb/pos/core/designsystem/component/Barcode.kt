package ai.neargo.fnb.pos.core.designsystem.component

import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.num
import ai.neargo.fnb.pos.core.model.BarcodeRules
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * EAN-13 条码（价签预览用）：条码模块由 BarcodeRules.ean13Bars 算，这里只按位画竖条，左右各留 7 个模块的静区，下方印 13 位数字。
 */
@Composable
fun Ean13Barcode(code13: String, width: Dp, height: Dp, modifier: Modifier = Modifier, color: Color = Color.Black) {
    val bars = remember(code13) { BarcodeRules.ean13Bars(code13) }
    Column(modifier.semantics { contentDescription = "条码 $code13" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.width(width).height(height)) {
            val m = size.width / (bars.length + 14)
            bars.forEachIndexed { i, b -> if (b == '1') drawRect(color, Offset((i + 7) * m, 0f), Size(m + 0.3f, size.height)) }
        }
        Text(code13, style = Pos.type.label.num().copy(fontSize = 9.sp, letterSpacing = 1.sp), color = color)
    }
}
