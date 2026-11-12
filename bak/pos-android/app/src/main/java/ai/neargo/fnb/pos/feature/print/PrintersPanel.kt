package ai.neargo.fnb.pos.feature.print

import ai.neargo.fnb.pos.app.appContainer
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.GroupTitle
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.PosSwitch
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.PrinterConfig
import ai.neargo.fnb.pos.core.model.KdsRules
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.util.UUID

/** 网口打印机管理（N6）：新增/编辑/删除、测试页、档口路由（厨房 / 前台）。 */
@Composable
fun PrintersPanel() {
    val c = appContainer()
    val store by c.config.current.collectAsStateWithLifecycle()
    val s = store ?: return
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<PrinterConfig?>(null) }
    val run: (suspend () -> Result<Unit>, String) -> Unit = { block, ok -> scope.launch { val r = block(); c.messages.post(r.fold({ ok }, { it.message ?: "操作失败" })) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupTitle("网口打印机（ESC/POS · RAW 9100）")
        Text("由主 POS 统一发送；未配置打印机的档口使用模拟打印。「已发送」表示已写入打印机且状态正常，不等于已确认出纸。设了备用打印机的路由，主打印机确定未发出（连续失败 ${KdsRules.FAILOVER_ATTEMPTS} 次或 ${KdsRules.FAILOVER_MS / 1000} 秒）时自动改投，单头标「备用机代打」；结果未知不改投。", style = Pos.type.label, color = Pos.colors.sub)
        if (s.printers.isEmpty()) Text("尚未添加打印机", style = Pos.type.body, color = Pos.colors.sub)
        s.printers.forEach { p ->
            KeyValue(p.name, "${p.host}:${p.port} · ${if (p.width == 32) "58mm" else "80mm"}${if (p.statusQuery) "" else " · 不查状态"}") {
                if (!p.enabled) Tag("已停用")
                PosButton("测试页", { run({ c.print.testPrint(p.id) }, "已加入测试页") }, size = BtnSize.Small)
                PosButton("编辑", { editing = p }, size = BtnSize.Small)
                PosButton("删除", { run({ c.print.removePrinter(p.id) }, "已删除 ${p.name}") }, size = BtnSize.Small, variant = BtnVariant.Danger)
            }
        }
        PosButton("添加打印机", { editing = PrinterConfig("pr-" + UUID.randomUUID().toString().take(8), "", "") }, variant = BtnVariant.Primary)
        GroupTitle("档口路由")
        val default = s.kdsStations.firstOrNull() ?: "厨房"
        // 厨房按档口路由：默认档口未设置时兼容旧「厨房」路由；其他档口未设置时跟随默认档口
        val kitchen = s.kdsStations.ifEmpty { listOf("厨房") }.map { it to "送厨单、退菜单" }
        // 零售门店多一条「标签机」路由：价签按 TSPL 发给它（标签机不打小票）
        val label = if (s.biz == ai.neargo.fnb.pos.core.model.BizType.Retail) listOf("标签机" to "价签（TSPL）") else emptyList()
        (kitchen + ("前台" to "预结单、结账单、交班单") + label).forEach { (station, use) ->
            val current = s.printRoutes[station]
            val follower = station != "前台" && station != "标签机" && station != default
            val fallback = if (station == default) s.printRoutes["厨房"] else null
            val shown = s.printers.firstOrNull { it.id == (current ?: fallback) }?.name
            KeyValue("$station · $use", shown ?: if (follower) "同默认档口（$default）" else "模拟打印") {
                PosButton(if (follower) "同默认" else "模拟", { run({ c.print.setRoute(station, null) }, if (follower) "$station 跟随默认档口" else "$station 使用模拟打印") }, size = BtnSize.Small, selected = current == null)
                s.printers.forEach { p -> PosButton(p.name, { run({ c.print.setRoute(station, p.id) }, "$station → ${p.name}") }, size = BtnSize.Small, selected = current == p.id) }
            }
            // 备用打印机：主打印机确定未发出（连续失败 ${KdsRules.FAILOVER_ATTEMPTS} 次或 ${KdsRules.FAILOVER_MS / 1000} 秒）时改投；结果未知不改投
            val primary = current ?: fallback
            if (current != null && s.printers.size > 1) {
                // 副本：同一张单另打一份到这些打印机（如出菜口联）；副本失败只提示，不影响送达判定
                val copies = s.printCopies[station].orEmpty()
                KeyValue("　$station · 副本", copies.mapNotNull { id -> s.printers.firstOrNull { it.id == id }?.name }.joinToString("、").ifBlank { "不打副本" }) {
                    s.printers.filter { it.id != primary }.forEach { p ->
                        val on = p.id in copies
                        PosButton(p.name, { run({ c.print.setCopies(station, if (on) copies - p.id else copies + p.id) }, if (on) "$station 不再给 ${p.name} 打副本" else "$station 副本 → ${p.name}") },
                            size = BtnSize.Small, selected = on)
                    }
                }
                val backup = s.printBackups[station]
                KeyValue("　$station · 备用", s.printers.firstOrNull { it.id == backup }?.name ?: "不设备用") {
                    PosButton("不设", { run({ c.print.setBackup(station, null) }, "$station 不设备用打印机") }, size = BtnSize.Small, selected = backup == null)
                    s.printers.filter { it.id != primary }.forEach { p -> PosButton(p.name, { run({ c.print.setBackup(station, p.id) }, "$station 备用 → ${p.name}") }, size = BtnSize.Small, selected = backup == p.id) }
                }
            }
        }
        if (s.biz == ai.neargo.fnb.pos.core.model.BizType.Retail) ScaleSettings()
    }
    editing?.let { p ->
        var name by remember { mutableStateOf(p.name) }
        var host by remember { mutableStateOf(p.host) }
        var port by remember { mutableStateOf(p.port.toString()) }
        var wide by remember { mutableStateOf(p.width == 48) }
        var status by remember { mutableStateOf(p.statusQuery) }
        var enabled by remember { mutableStateOf(p.enabled) }
        var error by remember { mutableStateOf<String?>(null) }
        PosDialog(if (p.name.isBlank()) "添加打印机" else "编辑打印机", onDismiss = { editing = null }, footer = {
            PosButton("取消", { editing = null }, size = BtnSize.Large)
            PosButton("保存", {
                val cfg = p.copy(name = name, host = host, port = port.toIntOrNull() ?: -1, width = if (wide) 48 else 32, statusQuery = status, enabled = enabled)
                scope.launch { val r = c.print.savePrinter(cfg); error = r.exceptionOrNull()?.message; if (r.isSuccess) { editing = null; c.messages.post("已保存打印机") } }
            }, variant = BtnVariant.Primary, size = BtnSize.Large)
        }) {
            PosTextField(name, { name = it }, "名称", hint = "例如 厨房热菜")
            PosTextField(host, { host = it }, "IP 地址", hint = "例如 192.168.1.50")
            PosTextField(port, { port = it.filter(Char::isDigit).take(5) }, "端口", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), error = error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                PosButton("58mm", { wide = false }, selected = !wide, size = BtnSize.Small)
                PosButton("80mm", { wide = true }, selected = wide, size = BtnSize.Small)
                PosButton(if (status) "查询状态：开" else "查询状态：关", { status = !status }, size = BtnSize.Small, selected = status)
                PosButton(if (enabled) "启用" else "停用", { enabled = !enabled }, size = BtnSize.Small, selected = enabled)
            }
        }
    }
}

/** 本机电子秤（零售称重）：网口秤或串口服务器地址、问答指令、单位；实时显示连接状态和读数。存在本机偏好，不同收银台各接各的秤 */
@Composable
private fun ScaleSettings() {
    val c = appContainer()
    val prefs by c.prefs.prefs.collectAsStateWithLifecycle(null)
    val p = prefs ?: return
    val state by c.scale.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var host by remember(p.scaleHost) { mutableStateOf(p.scaleHost) }
    var port by remember(p.scalePort) { mutableStateOf(p.scalePort.toString()) }
    var poll by remember(p.scalePoll) { mutableStateOf(p.scalePoll) }
    var grams by remember(p.scaleGrams) { mutableStateOf(p.scaleGrams) }
    GroupTitle("电子秤（本机）")
    Text("网口秤，或用串口服务器把秤的串口转成 TCP；秤设成「连续输出」。问答式的秤填查询指令（如 W\\r）。地址留空 = 不接秤，称重时手输", style = Pos.type.label, color = Pos.colors.sub)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PosTextField(host, { host = it.trim() }, "IP 地址", Modifier.weight(2f))
        PosTextField(port, { port = it.filter(Char::isDigit).take(5) }, "端口", Modifier.weight(1f))
        PosTextField(poll, { poll = it.take(16) }, "查询指令（可不填）", Modifier.weight(1.5f))
    }
    KeyValue("没写单位的读数按", if (grams) "克" else "千克") { PosSwitch(grams, { grams = it }) }
    val status = when (val st = state) {
        ai.neargo.fnb.pos.core.hardware.ScaleState.Off -> "未接秤"
        ai.neargo.fnb.pos.core.hardware.ScaleState.Connecting -> "连接中…"
        is ai.neargo.fnb.pos.core.hardware.ScaleState.Error -> st.message
        is ai.neargo.fnb.pos.core.hardware.ScaleState.Live -> st.reading?.let { "已连接 · %d.%03d kg · %s".format(it.grams / 1000, it.grams % 1000, if (it.stable) "稳定" else "称重中") } ?: "已连接，等读数"
    }
    KeyValue("状态", status) {
        PosButton("保存", { scope.launch { c.prefs.update { it.copy(scaleHost = host, scalePort = port.toIntOrNull() ?: 4001, scalePoll = poll, scaleGrams = grams) }; c.messages.post(if (host.isBlank()) "已关闭电子秤" else "已保存，正在连接电子秤") } },
            size = BtnSize.Small, variant = BtnVariant.Primary)
    }
}
