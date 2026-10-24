<script setup lang="ts">
/**
 * 页面外壳：Z1 系统栏 + 三区网格（Z3 导航 / Z4 内容 / Z5 详情）+ Z7 浮层。
 * 三区的位置由布局（L1–L6）决定；没有导航或详情的页面自动退化成两栏 / 一栏。
 * 窄屏（应用宽度 < 1000）：导航强制横排在顶部，详情区收成底部抽屉。
 */
import { computed, ref } from "vue";
import SysBar from "./SysBar.vue";
import SettingsSheet from "./SettingsSheet.vue";
import { layout, narrow, rootStyle, settingsOpen, ui, viewport } from "@/stores/ui";
import { toast } from "@/stores/toast";

const props = withDefaults(defineProps<{ page: string; hasNav?: boolean; hasDetail?: boolean; detailLabel?: string }>(), {
  hasNav: true, hasDetail: true, detailLabel: "查看详情",
});
const drawer = ref(false);

const grid = computed(() => {
  const M = "minmax(0,1fr)";
  const N = "96px";
  const D = viewport.width >= 1280 ? "360px" : "320px";
  const { nav, detail } = layout.value;
  const showDetail = props.hasDetail && !narrow.value;
  if (narrow.value || !showDetail) {
    return props.hasNav ? { cols: M, rows: `auto ${M}`, areas: `"nav" "main"` } : { cols: M, rows: M, areas: `"main"` };
  }
  if (!props.hasNav) {
    return detail === "left" ? { cols: `${D} ${M}`, rows: M, areas: `"detail main"` } : { cols: `${M} ${D}`, rows: M, areas: `"main detail"` };
  }
  if (nav === "top") {
    return detail === "left"
      ? { cols: `${D} ${M}`, rows: `auto ${M}`, areas: `"detail nav" "detail main"` }
      : { cols: `${M} ${D}`, rows: `auto ${M}`, areas: `"nav detail" "main detail"` };
  }
  const order = nav === "left" ? (detail === "left" ? ["detail", "nav", "main"] : ["nav", "main", "detail"]) : detail === "left" ? ["detail", "main", "nav"] : ["main", "nav", "detail"];
  return { cols: order.map((a) => (a === "nav" ? N : a === "detail" ? D : M)).join(" "), rows: M, areas: `"${order.join(" ")}"` };
});
const gridStyle = computed(() => `grid-template-columns:${grid.value.cols};grid-template-rows:${grid.value.rows};grid-template-areas:${grid.value.areas}`);
const horizontalNav = computed(() => layout.value.nav === "top");
</script>

<template>
  <view class="pos-root relative h-screen flex flex-col overflow-hidden" :class="{ 'zones-on': ui.zones }" :style="rootStyle">
    <SysBar :page="page" />
    <view class="flex-1 min-h-0 grid gap-[var(--pos-gap)] p-[var(--pos-gap)]" :style="gridStyle">
      <view v-if="hasNav" class="zone min-w-0 min-h-0" style="grid-area: nav" data-zone="Z3 导航"><slot name="nav" :horizontal="horizontalNav" /></view>
      <view class="zone min-w-0 min-h-0 flex flex-col gap-[var(--pos-gap)]" style="grid-area: main" data-zone="Z4 内容"><slot name="main" /></view>
      <view v-if="hasDetail && !narrow" class="zone min-w-0 min-h-0" style="grid-area: detail" data-zone="Z5 详情"><slot name="detail" /></view>
    </view>

    <!-- 窄屏：详情区收成底部条 + 抽屉 -->
    <view v-if="hasDetail && narrow" class="px-[var(--pos-gap)] pb-[var(--pos-gap)]">
      <view class="pos-btn bg-pay-bg text-pay-fg" @click="drawer = true">{{ detailLabel }}</view>
    </view>
    <view v-if="hasDetail && narrow && drawer" class="absolute inset-0 z-30 flex flex-col justify-end bg-[rgba(10,12,16,.45)]" @click="drawer = false">
      <view class="h-[86%] px-[var(--pos-gap)] pb-[var(--pos-gap)]" @click.stop><slot name="detail" /></view>
    </view>

    <slot name="overlay" />
    <SettingsSheet v-if="settingsOpen.value" />
    <view v-if="toast.show" class="absolute left-1/2 bottom-6 z-50 -translate-x-1/2 px-4 h-[42px] flex items-center rounded-btn bg-ink text-surface text-[14px] font-500 whitespace-nowrap">{{ toast.text }}</view>
  </view>
</template>
