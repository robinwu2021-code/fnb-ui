package ai.neargo.fnb.pos.feature.retail

import ai.neargo.fnb.pos.core.model.ScanBuffer
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * 扫码枪（HID 键盘）接收：挂在页面根节点上，页面根节点拿焦点（不弹软键盘）。
 * 只在页面根节点自己持有焦点时接收：任何子节点（输入框、收款键盘等）拿着焦点就原样放行，不吞按键；
 * paused 是页面额外的暂停条件。接收时按按键间隔识别扫码枪（ScanBuffer），人手敲的键不算。
 * 收银页、库存作业页共用。
 */
@Composable
fun Modifier.hidScanner(focus: FocusRequester, paused: () -> Boolean, onCode: (String) -> Unit): Modifier {
    val buffer = remember { ScanBuffer() }
    val self = remember { booleanArrayOf(false) }   // 根节点自己有焦点（不是子节点）
    return focusRequester(focus).onFocusChanged { self[0] = it.isFocused }.onPreviewKeyEvent { e ->
        if (!self[0] || paused() || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        // 用按键发生的时间（系统事件时间戳），不用处理时的时间：切页后界面线程忙，按键排队处理，
        // 处理间隔会被拉长，按处理时间算会把扫码枪的前几个字符误判成人手输入丢掉
        val now = e.nativeKeyEvent.eventTime
        when {
            e.key == Key.Enter || e.key == Key.NumPadEnter -> buffer.enter(now)?.let { onCode(it); true } ?: false
            e.utf16CodePoint in 33..126 -> { buffer.char(e.utf16CodePoint.toChar(), now); true }
            else -> false
        }
    }.focusable()
}
