package ai.neargo.fnb.pos.feature.kds

import ai.neargo.fnb.pos.app.appContainer
import ai.neargo.fnb.pos.core.designsystem.component.BtnSize
import ai.neargo.fnb.pos.core.designsystem.component.BtnVariant
import ai.neargo.fnb.pos.core.designsystem.component.GroupTitle
import ai.neargo.fnb.pos.core.designsystem.component.KeyValue
import ai.neargo.fnb.pos.core.designsystem.component.PosButton
import ai.neargo.fnb.pos.core.designsystem.component.PosDialog
import ai.neargo.fnb.pos.core.designsystem.component.PosTextField
import ai.neargo.fnb.pos.core.designsystem.component.Tag
import ai.neargo.fnb.pos.core.designsystem.component.Text
import ai.neargo.fnb.pos.core.designsystem.theme.Pos
import ai.neargo.fnb.pos.core.model.DeliveryMode
import ai.neargo.fnb.pos.core.model.DeliveryRules
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** 厨房档口：档口列表（第一个为默认）与菜品分类 → 档口映射。送厨时按档口拆任务卡。 */
@Composable
fun StationsPanel() {
    val c = appContainer()
    val store by c.config.current.collectAsStateWithLifecycle()
    val s = store ?: return
    val scope = rememberCoroutineScope()
    var stations by remember(s.kdsStations) { mutableStateOf(s.kdsStations) }
    var mapping by remember(s.stationOf) { mutableStateOf(s.stationOf) }
    var modes by remember(s.stationDelivery) { mutableStateOf(s.stationDelivery) }
    var adding by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupTitle("档口（${stations.size}）")
        stations.forEachIndexed { i, st ->
            KeyValue(st, if (i == 0) "默认档口（未指定分类的菜品）" else "") {
                if (i > 0) PosButton("设为默认", { stations = listOf(st) + stations.filterNot { it == st } }, size = BtnSize.Small)
                if (stations.size > 1) PosButton("删除", { stations = stations - st; mapping = mapping.filterValues { it != st } }, size = BtnSize.Small, variant = BtnVariant.Danger)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosTextField(adding, { adding = it.take(8) }, "新档口名称", Modifier.weight(1f), hint = "例如 凉菜、吧台")
            PosButton("添加", { if (adding.isNotBlank() && adding.trim() !in stations) { stations = stations + adding.trim(); adding = "" } }, modifier = Modifier.padding(top = 20.dp))
        }
        GroupTitle("分类送往档口")
        s.categories.forEach { cat ->
            val cur = mapping[cat.id]?.takeIf { it in stations } ?: stations.first()
            KeyValue(cat.name, "") {
                stations.forEach { st -> PosButton(st, { mapping = mapping + (cat.id to st) }, size = BtnSize.Small, selected = cur == st) }
            }
        }
        GroupTitle("出单方式（决定哪一路必须送达，未满足时拦结账）")
        stations.forEach { st ->
            val cur = modes[st] ?: DeliveryMode.Paper
            KeyValue(st, cur.desc) {
                DeliveryMode.entries.forEach { m -> PosButton(m.label, { modes = modes + (st to m) }, size = BtnSize.Small, selected = cur == m) }
            }
        }
        if (error != null) Text(error!!, style = Pos.type.label, color = Pos.colors.danger)
        PosButton("保存档口配置", {
            scope.launch {
                val r = c.kds.saveKdsStations(stations, mapping).mapCatching { c.kds.saveDeliveryModes(modes.filterKeys { it in stations }).getOrThrow() }
                error = r.exceptionOrNull()?.message
                if (r.isSuccess) c.messages.post("已保存档口配置，之后的送厨按档口拆单")
            }
        }, variant = BtnVariant.Primary, size = BtnSize.Large)
        Text("每台厨房屏选择一个档口，只接收该档口的任务卡；修改只影响之后送厨的菜品。含厨房屏的出单方式：${DeliveryRules.SCREEN_TIMEOUT_MS / 1000} 秒内没有厨房屏收到即为未送达，可改打纸单或人工确认。", style = Pos.type.label, color = Pos.colors.sub)
    }
}

/** 厨房屏选择档口（档口来自主机同步的门店配置）。 */
@Composable
fun StationPickDialog(current: String, onDismiss: () -> Unit) {
    val c = appContainer()
    val store by c.config.current.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val list = store?.kdsStations ?: listOf("厨房")
    PosDialog("选择厨房屏档口", onDismiss, subtitle = "只接收所选档口的任务卡；切换后本机重新拉取", footer = { PosButton("取消", onDismiss, size = BtnSize.Large) }) {
        list.forEachIndexed { i, st ->
            KeyValue(st, if (i == 0) "默认档口" else "") {
                if (st == current) Tag("当前")
                PosButton("选择", { scope.launch { c.setKdsStation(st); c.messages.post("本机已设为「$st」厨房屏"); onDismiss() } }, size = BtnSize.Small, variant = BtnVariant.Primary, enabled = st != current)
            }
        }
    }
}
