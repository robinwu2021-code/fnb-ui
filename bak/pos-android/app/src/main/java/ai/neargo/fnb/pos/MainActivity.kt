package ai.neargo.fnb.pos

import kotlinx.coroutines.flow.map
import ai.neargo.fnb.pos.app.PosApp
import ai.neargo.fnb.pos.app.ShellViewModel
import ai.neargo.fnb.pos.app.posViewModel
import ai.neargo.fnb.pos.core.designsystem.theme.PosTheme
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** v3 只有浅色组件方案（规范 §9：深色未验收），系统栏固定浅色图标。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val light = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = light, navigationBarStyle = light)
        // 双屏收银机：顾客面向的副屏显示客显画面
        ai.neargo.fnb.pos.feature.customer.CustomerDisplayHost(this).attach()
        setContent {
            val shell = posViewModel { c, _ -> ShellViewModel(c.session, c.device, c.prefs, c.sync, c.messages, c.syncStatus, c.cloud.state.map { it.badge }) }
            val state by shell.state.collectAsStateWithLifecycle()
            // 偏好首次读出前不渲染，避免默认皮肤闪一下再切换
            val prefs = state.prefs ?: return@setContent
            PosTheme(prefs) { PosApp(shell) }
        }
    }
}
