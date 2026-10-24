package ai.neargo.fnb.pos

import ai.neargo.fnb.pos.state.PosStore
import ai.neargo.fnb.pos.theme.PosTheme
import ai.neargo.fnb.pos.ui.shell.PosApp
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val store: PosStore by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 状态栏、导航栏图标跟随深浅色
            DisposableEffect(store.dark) {
                val style = if (store.dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            PosTheme(skin = store.skin, dark = store.dark, density = store.density) {
                PosApp(store)
            }
        }
    }
}
