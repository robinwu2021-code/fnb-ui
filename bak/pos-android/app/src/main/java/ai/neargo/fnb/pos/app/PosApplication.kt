package ai.neargo.fnb.pos.app

import ai.neargo.fnb.pos.core.data.AppContainer
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

/** 应用入口：创建依赖容器；配置图片加载。 */
class PosApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ai.neargo.fnb.pos.core.designsystem.component.Translator.load(this)
    }

    // 演示菜品图来自 Wikimedia，其 UA 策略拒绝 OkHttp 默认 User-Agent（403），需带应用标识。上线换门店自有图床。
    override fun newImageLoader(context: android.content.Context): ImageLoader = ImageLoader.Builder(context).components {
        add(
            OkHttpNetworkFetcherFactory(callFactory = {
                OkHttpClient.Builder().addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", "NeargoPOS-Demo/0.2 (Android)").build())
                }.build()
            }),
        )
    }.build()
}

/** 取应用容器（Composable 内）。 */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as PosApplication).container

/** 页面级 ViewModel：从容器取依赖，带 SavedStateHandle（含导航参数）。 */
@Composable
inline fun <reified VM : ViewModel> posViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = appContainer()
    return viewModel(factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } })
}
