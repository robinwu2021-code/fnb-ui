<script setup lang="ts">
/** Z1 系统栏：门店与收银员、页面切换、系统入口、时钟。颜色由组件风格的 --pos-bar-* 决定。 */
import { onMounted, onUnmounted, ref } from "vue";
import { narrow, settingsOpen } from "@/stores/ui";
import { showToast } from "@/stores/toast";

const props = defineProps<{ page: string }>();
const PAGES = [
  { key: "tables", label: "桌台", url: "/pages/tables/index" },
  { key: "order", label: "点餐", url: "/pages/order/index" },
  { key: "orders", label: "订单", url: "/pages/orders/index" },
  { key: "shift", label: "交班", url: "/pages/shift/index" },
];
const ENTRIES = [
  { key: "takeout", label: "外卖", badge: 2 },
  { key: "member", label: "会员" },
  { key: "cashbox", label: "钱箱" },
  { key: "print", label: "打印异常", warn: true },
  { key: "net", label: "在线" },
];
const now = ref("");
const tick = () => { const d = new Date(); now.value = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`; };
let timer: ReturnType<typeof setInterval> | undefined;
onMounted(() => { tick(); timer = setInterval(tick, 30000); });
onUnmounted(() => timer && clearInterval(timer));

function go(p: (typeof PAGES)[number]) { if (p.key !== props.page) uni.redirectTo({ url: p.url }); }
function entry(key: string) { showToast(`「${ENTRIES.find((e) => e.key === key)?.label}」面板：骨架只放入口`); }
</script>

<template>
  <view class="zone flex items-center gap-1 h-[56px] px-3 bg-bar-bg text-bar-fg pos-frame" data-zone="Z1 系统栏">
    <view class="flex items-center gap-2.5 mr-3 shrink-0">
      <view class="w-[32px] h-[32px] rounded-ctl bg-ink text-surface flex items-center justify-center font-700">松</view>
      <!-- 窄屏（竖持平板）只留门店标识，给页面切换和系统入口让位 -->
      <view v-if="!narrow"><view class="text-[15px] font-600 whitespace-nowrap">虹选小馆 · 文三路店</view><view class="text-[12px] text-bar-sub">收银台 01 · 小周</view></view>
    </view>
    <view class="flex gap-0.5 mr-auto">
      <view
        v-for="p in PAGES" :key="p.key" class="h-[38px] px-3.5 flex items-center shrink-0 rounded-btn text-[14px] font-500 whitespace-nowrap"
        :class="p.key === page ? 'bg-on-bg text-on-fg' : 'text-bar-sub'" @click="go(p)"
      >{{ p.label }}</view>
    </view>
    <view
      v-for="e in ENTRIES" :key="e.key" class="h-[38px] px-2.5 flex items-center shrink-0 rounded-btn text-[13px] font-500 text-bar-sub whitespace-nowrap"
      :class="{ '!text-danger-text': e.warn }" @click="entry(e.key)"
    >
      {{ e.label }}
      <text v-if="e.badge" class="ml-1 min-w-[16px] h-[16px] px-1 rounded-full bg-danger text-white text-[10px] leading-[16px] text-center">{{ e.badge }}</text>
    </view>
    <view class="h-[38px] px-3 flex items-center shrink-0 rounded-btn text-[13px] font-600 bg-bar-hover whitespace-nowrap" @click="settingsOpen.value = true">设置</view>
    <text class="pl-2 text-[13px] text-bar-sub tabular-nums">{{ now }}</text>
  </view>
</template>
