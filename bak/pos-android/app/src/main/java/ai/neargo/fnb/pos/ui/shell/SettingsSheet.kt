package ai.neargo.fnb.pos.ui.shell

import ai.neargo.fnb.pos.state.DetailSide
import ai.neargo.fnb.pos.state.MenuView
import ai.neargo.fnb.pos.state.NavSide
import ai.neargo.fnb.pos.state.PosFrame
import ai.neargo.fnb.pos.state.PosStore
import ai.neargo.fnb.pos.theme.Density
import ai.neargo.fnb.pos.theme.Pos
import ai.neargo.fnb.pos.theme.PosDims
import ai.neargo.fnb.pos.theme.PosType
import ai.neargo.fnb.pos.theme.Skin
import ai.neargo.fnb.pos.ui.components.Segmented
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** 设置：主框架 L1–L6、密度、菜单视图、皮肤、深浅色，以及演示用的离线、缺纸开关。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(store: PosStore, onDismiss: () -> Unit) {
    val c = Pos.colors
    val wide = LocalPosLayout.current.wide
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.elev,
        scrimColor = c.scrim,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("设置", style = PosType.display, color = c.ink)

            Label("主框架")
            if (!wide) {
                Text("当前宽度不足 960，分类固定在顶部、结单区收成底部条。横屏或平板上生效", style = PosType.caption, color = c.warningText)
                Spacer(Modifier.height(8.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PosFrame.entries.forEach { f -> FrameOption(f, store.frame == f) { store.frame = f } }
            }

            Label("密度")
            Segmented(Density.entries, store.density, { it.label }, { store.density = it })
            Text("紧凑：控件 44、间距 8；标准：控件 48、间距 12", style = PosType.caption, color = c.sub, modifier = Modifier.padding(top = 4.dp))

            Label("菜单视图")
            Segmented(MenuView.entries, store.menuView, { it.label }, { store.menuView = it })

            Label("皮肤")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skin.entries.forEach { s ->
                    val on = store.skin == s
                    val sw = Color(if (store.dark) s.dark.primary else s.light.primary)
                    Row(
                        Modifier.height(PosDims.ctl).clip(PosDims.radiusFull)
                            .background(if (on) c.primaryTint else c.faint)
                            .clickable(role = Role.RadioButton) { store.skin = s }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(16.dp).clip(CircleShape).background(sw)
                                .then(if (on) Modifier.border(2.dp, c.surface, CircleShape) else Modifier),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(s.label, style = PosType.body, color = if (on) c.primaryText else c.ink)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ToggleRow("深色模式", store.dark) { store.dark = it }
            ToggleRow("模拟离线", !store.online) { store.online = !it }
            ToggleRow("模拟打印机缺纸", !store.printerOk) { store.printerOk = !it }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = PosType.sub, color = Pos.colors.sub, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

/** 布局选项：缩略图画出导航区（深）、内容区（浅）、详情区（主色）的相对位置。 */
@Composable
private fun FrameOption(f: PosFrame, selected: Boolean, onClick: () -> Unit) {
    val c = Pos.colors
    Column(
        Modifier.width(132.dp).clip(PosDims.radiusMd)
            .background(if (selected) c.primaryTint else c.faint)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
    ) {
        FrameThumb(f, Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.name, style = PosType.strong, color = if (selected) c.primaryText else c.ink)
            Spacer(Modifier.width(6.dp))
            Text("导航${f.nav.label} · 账单${f.detail.label}", style = PosType.caption, color = c.sub)
        }
        Text(f.note, style = PosType.caption, color = c.sub)
    }
}

@Composable
private fun FrameThumb(f: PosFrame, modifier: Modifier) {
    val c = Pos.colors
    val r = RoundedCornerShape(3.dp)
    val navC = c.sub.copy(alpha = 0.55f)
    val contentC = c.surface
    val detailC = c.primary.copy(alpha = 0.7f)
    Row(modifier.clip(RoundedCornerShape(6.dp)).background(c.bg).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val detail = @Composable { Box(Modifier.width(30.dp).fillMaxHeight().clip(r).background(detailC)) }
        val rail = @Composable { Box(Modifier.width(10.dp).fillMaxHeight().clip(r).background(navC)) }
        if (f.detail == DetailSide.Left) detail()
        if (f.nav == NavSide.Left) rail()
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (f.nav == NavSide.Top) Box(Modifier.fillMaxWidth().height(8.dp).clip(r).background(navC))
            Box(Modifier.fillMaxWidth().weight(1f).clip(r).background(contentC))
        }
        if (f.nav == NavSide.Right) rail()
        if (f.detail == DetailSide.Right) detail()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Pos.colors
    Row(Modifier.fillMaxWidth().height(PosDims.ctlL), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PosType.body, color = c.ink, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary,
                uncheckedTrackColor = c.faint, uncheckedThumbColor = c.sub, uncheckedBorderColor = c.sub,
            ),
        )
    }
}
