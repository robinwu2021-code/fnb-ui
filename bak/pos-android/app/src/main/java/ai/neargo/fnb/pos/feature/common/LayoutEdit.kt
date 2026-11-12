package ai.neargo.fnb.pos.feature.common

import ai.neargo.fnb.pos.core.data.PreferencesRepository
import ai.neargo.fnb.pos.core.model.ActionLayout
import ai.neargo.fnb.pos.core.model.BizType
import ai.neargo.fnb.pos.core.model.DisplayPrefs
import ai.neargo.fnb.pos.core.model.RailConfig
import ai.neargo.fnb.pos.core.model.BillSide
import ai.neargo.fnb.pos.core.model.CatSide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 「编辑收银页布局」：编辑时改动先进草稿，页面按草稿即时显示；「完成」写回偏好（本机），「取消」丢掉。
 * 只改布局相关的：分类 / 账单位置、账单宽、竖条宽、竖条与「更多」的顺序。
 */
class LayoutEditor(private val prefs: PreferencesRepository) {
    private val d = MutableStateFlow<DisplayPrefs?>(null)
    /** 非空 = 正在编辑 */
    val draft: StateFlow<DisplayPrefs?> = d

    fun start(current: DisplayPrefs) { d.value = current }
    fun cancel() { d.value = null }
    fun edit(f: (DisplayPrefs) -> DisplayPrefs) = d.update { it?.let(f) }
    fun reset(biz: BizType) = edit { it.resetLayout(biz) }

    // 拖动时用的小改动
    fun widenBill(deltaDp: Float, base: Int) = edit { it.copy(billWidth = ActionLayout.billWidth((if (it.billWidth > 0) it.billWidth else base) + deltaDp.toInt(), base)) }
    fun widenRail(deltaDp: Float) = edit { it.copy(railWidth = ActionLayout.railWidth(ActionLayout.railWidth(it.railWidth) + deltaDp.toInt())) }
    fun billSide(side: BillSide) = edit { it.copy(layout = ActionLayout.layoutOf(it.layout.cat, side)) }
    fun catSide(side: CatSide) = edit { it.copy(layout = ActionLayout.layoutOf(side, it.layout.bill)) }
    fun rail(biz: BizType, c: RailConfig) = edit { it.withRail(biz, c) }

    suspend fun done() {
        val x = d.value ?: return
        prefs.update { it.copy(layout = x.layout, billWidth = x.billWidth, railWidth = x.railWidth, diningRail = x.diningRail, retailRail = x.retailRail) }
        d.value = null
    }

}
