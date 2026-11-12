package ai.neargo.fnb.pos.feature.customer

import ai.neargo.fnb.pos.core.model.profile

import ai.neargo.fnb.pos.app.appContainer
import ai.neargo.fnb.pos.core.designsystem.component.GroupTitle
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.PosSwitch
import ai.neargo.fnb.pos.core.designsystem.component.ProductImage
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.designsystem.theme.PosTheme
import ai.neargo.fnb.pos.core.model.CustomerDisplayRules
import ai.neargo.fnb.pos.core.model.CustomerView
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import ai.neargo.fnb.pos.core.model.yuan
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 当前客显内容：门店数据 + 收银员当前桌台 + 结账阶段 + 本机偏好，每秒刷新（致谢页计时、轮播翻页）。 */
@Composable
fun rememberCustomerView(): Pair<CustomerView, Long> {
    val c = appContainer()
    val store by c.config.current.collectAsStateWithLifecycle()
    val session by c.session.session.collectAsStateWithLifecycle()
    val stage by c.customerStage.collectAsStateWithLifecycle()
    val prefs by c.prefs.prefs.collectAsState(initial = DisplayPrefs())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    return CustomerDisplayRules.view(store, session.currentTableId, stage, prefs.customerLines, prefs.customerSlides, now) to now
}

/** 客显屏画面（顾客面向，大字、只读）。 */
@Composable
fun CustomerScreen(view: CustomerView, now: Long, modifier: Modifier = Modifier) {
    val c = Pos.colors
    Column(modifier.fillMaxSize().background(c.canvas).padding(32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(view.store.ifBlank { "欢迎光临" }, style = Pos.type.title.copy(fontSize = 30.sp), color = c.ink, modifier = Modifier.weight(1f))
            if (view is CustomerView.Order) Text("桌台 ${view.table}", style = Pos.type.title.copy(fontSize = 26.sp), color = c.primary)
            if (view is CustomerView.Retail) Text("共 ${view.cart.pieces} 件" + if (view.cart.member) " · 会员" else "", style = Pos.type.title.copy(fontSize = 26.sp), color = c.primary)
        }
        Spacer(Modifier.height(20.dp))
        when (view) {
            is CustomerView.Idle -> Idle(view, now)
            is CustomerView.Order -> OrderView(view)
            is CustomerView.Paid -> PaidView(view)
            is CustomerView.Retail -> RetailView(view)
        }
    }
}

@Composable
private fun Idle(v: CustomerView.Idle, now: Long) {
    val c = Pos.colors
    val slide = v.slides.takeIf { it.isNotEmpty() }?.let { it[((now / CustomerDisplayRules.SLIDE_MS) % it.size).toInt()] }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.surface), contentAlignment = Alignment.Center) {
        if (slide == null) Text("欢迎光临", style = Pos.type.hero.copy(fontSize = 64.sp), color = c.primary)
        else Row(Modifier.fillMaxSize().padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(slide.image, slide.name, Modifier.weight(1.2f).fillMaxHeight().clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).padding(start = 40.dp)) {
                Text(if (slide.note.isNotBlank()) "今日特惠" else "今日推荐", style = Pos.type.label.copy(fontSize = 22.sp), color = c.sub)
                Text(slide.name, style = Pos.type.hero.copy(fontSize = 52.sp), color = c.ink, modifier = Modifier.padding(vertical = 12.dp))
                Text(yuan(slide.price) + if (slide.perKg) "/kg" else "", style = Pos.type.amount.copy(fontSize = 44.sp), color = c.primary)
                if (slide.note.isNotBlank()) Text(slide.note, style = Pos.type.strong.copy(fontSize = 28.sp), color = c.warning, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

/** 零售客显：左边逐行明细（刚扫的那行在最上、底色突出），右边原价合计、已省、换货抵扣、应付与收款找零 */
@Composable
private fun RetailView(v: CustomerView.Retail) {
    val c = Pos.colors
    val k = v.cart
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        if (v.showLines) {
            Column(Modifier.weight(1.5f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(24.dp)) {
                k.last?.let { Text("刚扫：$it", style = Pos.type.strong.copy(fontSize = 22.sp), color = c.primary, modifier = Modifier.padding(bottom = 12.dp)) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(k.lines) { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.name, style = Pos.type.strong.copy(fontSize = 26.sp), color = c.ink)
                                if (l.note.isNotBlank()) Text(l.note, style = Pos.type.label.copy(fontSize = 18.sp), color = c.primary)
                            }
                            Text(l.qty, style = Pos.type.body.copy(fontSize = 24.sp), color = c.sub, modifier = Modifier.width(130.dp))
                            Text(if (l.gift) "赠送" else yuan(l.amount), style = Pos.type.strong.copy(fontSize = 26.sp), color = if (l.gift) c.primary else c.ink)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Amount("原价合计", yuan(k.sub))
            if (k.save > 0) Amount("已省", "−" + yuan(k.save).drop(1))
            if (k.credit > 0) Amount("换货抵扣", "−" + yuan(k.credit).drop(1))
            Spacer(Modifier.weight(1f))
            Text(when { k.refund > 0 -> "退您差价"; k.credit > 0 -> "需补差价"; k.pay != null -> "应付"; else -> "当前合计" }, style = Pos.type.label.copy(fontSize = 22.sp), color = c.sub)
            Text(yuan(if (k.refund > 0) k.refund else k.due), style = Pos.type.amount.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold), color = c.primary)
            k.pay?.let { p ->
                Amount("收款方式", p.method)
                p.received?.let { Amount("实收", yuan(it)) }
                p.change?.let { Text("找零 ${yuan(it)}", style = Pos.type.amount.copy(fontSize = 40.sp), color = c.ink) }
            }
        }
    }
}

@Composable
private fun OrderView(v: CustomerView.Order) {
    val c = Pos.colors
    val t = v.totals
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        if (v.showLines) {
            Column(Modifier.weight(1.5f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(24.dp)) {
                Text("已点 ${t.items} 份", style = Pos.type.strong.copy(fontSize = 22.sp), color = c.sub, modifier = Modifier.padding(bottom = 12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(v.lines) { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.name, style = Pos.type.strong.copy(fontSize = 26.sp), color = c.ink)
                                if (l.spec.isNotBlank()) Text(l.spec, style = Pos.type.label.copy(fontSize = 18.sp), color = c.sub)
                            }
                            Text("×${l.qty}", style = Pos.type.body.copy(fontSize = 24.sp), color = c.sub, modifier = Modifier.width(90.dp))
                            Text(if (l.gifted) "赠送" else yuan(l.amount), style = Pos.type.strong.copy(fontSize = 26.sp), color = if (l.gifted) c.primary else c.ink)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Amount("小计", yuan(t.sub))
            if (t.discount > 0) Amount("优惠", "−" + yuan(t.discount).drop(1))
            if (t.service > 0) Amount("服务费", yuan(t.service))
            Spacer(Modifier.weight(1f))
            Text(if (v.checkout != null) "应付" else "当前合计", style = Pos.type.label.copy(fontSize = 22.sp), color = c.sub)
            Text(yuan(t.due), style = Pos.type.amount.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold), color = c.primary)
            v.checkout?.let { k ->
                Amount("收款方式", k.method)
                k.received?.let { Amount("实收", yuan(it)) }
                k.change?.let { Text("找零 ${yuan(it)}", style = Pos.type.amount.copy(fontSize = 40.sp), color = c.ink) }
            }
        }
    }
}

@Composable
private fun Amount(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = Pos.type.body.copy(fontSize = 22.sp), color = Pos.colors.sub, modifier = Modifier.weight(1f))
        Text(value, style = Pos.type.strong.copy(fontSize = 24.sp), color = Pos.colors.ink)
    }
}

@Composable
private fun PaidView(v: CustomerView.Paid) {
    val c = Pos.colors
    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.surface), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("✓", style = Pos.type.hero.copy(fontSize = 96.sp), color = c.primary)
        Text("收款成功", style = Pos.type.hero.copy(fontSize = 52.sp), color = c.ink, modifier = Modifier.padding(vertical = 12.dp))
        Text("${v.method} ${yuan(v.amount)}", style = Pos.type.amount.copy(fontSize = 40.sp), color = c.primary)
        if (v.change > 0) Text("找零 ${yuan(v.change)}", style = Pos.type.amount.copy(fontSize = 40.sp), color = c.ink, modifier = Modifier.padding(top = 8.dp))
        Text("谢谢惠顾，欢迎再次光临", style = Pos.type.body.copy(fontSize = 26.sp), color = c.sub, modifier = Modifier.padding(top = 24.dp))
    }
}

/** 本机副屏的 Presentation：只承载客显画面。 */
private class CustomerPresentation(private val activity: ComponentActivity, display: Display) : Presentation(activity, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(activity); setViewTreeSavedStateRegistryOwner(activity); setViewTreeViewModelStoreOwner(activity)
            setContent {
                val prefs by appContainer().prefs.prefs.collectAsState(initial = DisplayPrefs())
                PosTheme(prefs) { val (v, now) = rememberCustomerView(); CustomerScreen(v, now) }
            }
        }
        setContentView(view)
        window?.decorView?.let { d -> d.setViewTreeLifecycleOwner(activity); d.setViewTreeSavedStateRegistryOwner(activity); d.setViewTreeViewModelStoreOwner(activity) }
    }
}

/** 客显屏宿主：检测副屏（Presentation 类显示器），按本机偏好显示或关闭；副屏插拔自动跟随。 */
class CustomerDisplayHost(private val activity: ComponentActivity) : DefaultLifecycleObserver, DisplayManager.DisplayListener {
    private val dm = activity.getSystemService(DisplayManager::class.java)
    private var presentation: Presentation? = null
    private var enabled = false

    fun attach() {
        activity.lifecycle.addObserver(this)
        dm.registerDisplayListener(this, Handler(Looper.getMainLooper()))
        val container = (activity.application as ai.neargo.fnb.pos.app.PosApplication).container
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) { container.prefs.prefs.collect { enabled = it.customerDisplay; refresh() } }
        }
    }

    private fun refresh() {
        val d = customerDisplays(activity).firstOrNull()
        if (!enabled || d == null) { dismiss(); return }
        if (presentation?.display?.displayId == d.displayId && presentation?.isShowing == true) return
        dismiss()
        presentation = CustomerPresentation(activity, d).also { p -> runCatching { p.show() }.onFailure { presentation = null } }
    }

    private fun dismiss() { presentation?.let { runCatching { it.dismiss() } }; presentation = null }

    override fun onDisplayAdded(displayId: Int) = refresh()
    override fun onDisplayRemoved(displayId: Int) = refresh()
    override fun onDisplayChanged(displayId: Int) {}
    override fun onDestroy(owner: LifecycleOwner) { dm.unregisterDisplayListener(this); dismiss() }
}

fun customerDisplays(context: Context): List<Display> =
    context.getSystemService(DisplayManager::class.java).getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).toList()

/** 设置页：启用开关、明细与轮播开关、副屏检测与实时预览。 */
@Composable
fun CustomerDisplayPanel() {
    val c = appContainer()
    val prefs by c.prefs.prefs.collectAsState(initial = null)
    val p = prefs ?: return
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // 副屏插拔时刷新检测结果
    var displays by remember { mutableStateOf(customerDisplays(ctx)) }
    DisposableEffect(ctx) {
        val dm = ctx.getSystemService(DisplayManager::class.java)
        val l = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) { displays = customerDisplays(ctx) }
            override fun onDisplayRemoved(id: Int) { displays = customerDisplays(ctx) }
            override fun onDisplayChanged(id: Int) {}
        }
        dm.registerDisplayListener(l, Handler(Looper.getMainLooper()))
        onDispose { dm.unregisterDisplayListener(l) }
    }
    fun set(f: (DisplayPrefs) -> DisplayPrefs) { scope.launch { c.prefs.update(f) } }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupTitle("本机副屏")
        KeyValue("检测到的副屏", displays.firstOrNull()?.let { "${it.name}（${it.mode.physicalWidth}×${it.mode.physicalHeight}）" } ?: "未检测到（双屏收银机的顾客屏会自动识别）")
        KeyValue("启用客显屏", if (p.customerDisplay) "开" else "关") { PosSwitch(p.customerDisplay, { v -> set { it.copy(customerDisplay = v) } }) }
        KeyValue("显示订单明细", if (p.customerLines) "逐行显示菜品与金额" else "只显示合计") { PosSwitch(p.customerLines, { v -> set { it.copy(customerLines = v) } }) }
        KeyValue("空闲时轮播", if (p.customerSlides) "轮播推荐菜品（${CustomerDisplayRules.SLIDE_MS / 1000} 秒一页）" else "只显示欢迎语") { PosSwitch(p.customerSlides, { v -> set { it.copy(customerSlides = v) } }) }
        GroupTitle("预览（与副屏实时同步）")
        val (view, now) = rememberCustomerView()
        // 按 1280×800dp 副屏等比缩小预览：缩放密度，布局与真实副屏一致
        BoxWithConstraints(Modifier.fillMaxWidth(0.8f).aspectRatio(1.6f).clip(RoundedCornerShape(12.dp)).border(1.dp, Pos.colors.divider, RoundedCornerShape(12.dp))) {
            val d = LocalDensity.current
            val factor = maxWidth.value / 1280f
            CompositionLocalProvider(LocalDensity provides Density(d.density * factor, d.fontScale)) { CustomerScreen(view, now) }
        }
        val retail = c.config.current.collectAsStateWithLifecycle().value?.profile?.retail == true
        Text(if (retail) "跟随收银台购物车：扫码时显示刚扫的商品、逐行明细、已省与合计，收款时显示应付与找零，换货显示补差价或退差价；收款成功后显示致谢页 ${CustomerDisplayRules.PAID_HOLD_MS / 1000} 秒。空闲时优先轮播特惠商品。"
            else "跟随收银员当前桌台：点单时显示明细与合计，结账时显示应付、收款方式与找零，收款成功后显示致谢页 ${CustomerDisplayRules.PAID_HOLD_MS / 1000} 秒。", style = Pos.type.label, color = Pos.colors.sub)
    }
}
