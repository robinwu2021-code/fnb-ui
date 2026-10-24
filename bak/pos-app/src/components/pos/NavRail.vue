<script setup lang="ts">
/** Z3 导航：菜品分类 / 桌区 / 订单状态共用。竖排或横排由布局决定。 */
import { computed } from "vue";
import { ui } from "@/stores/ui";
import { STYLES } from "@/theme/tokens";

const props = defineProps<{ items: { id: string; label: string; count?: number; dot?: boolean; color?: string }[]; active: string; horizontal?: boolean }>();
defineEmits<{ (e: "select", id: string): void }>();
const catColor = computed(() => STYLES[ui.style].catTiles);
</script>

<template>
  <scroll-view :scroll-x="horizontal" :scroll-y="!horizontal" class="pos-panel h-full">
    <view class="flex gap-0.5 p-1.5" :class="horizontal ? 'flex-row w-max' : 'flex-col'">
      <view
        v-for="it in items" :key="it.id"
        class="flex items-center gap-1.5 shrink-0 rounded-ctl text-[14px] font-500 whitespace-nowrap"
        :class="[horizontal ? 'h-[var(--pos-ctl-s)] px-3.5' : 'min-h-[46px] px-3', it.id === active ? 'bg-on-bg text-on-fg font-600' : 'text-sub']"
        :style="catColor && it.color && it.id === active ? `background:${it.color};color:#fff` : ''"
        @click="$emit('select', it.id)"
      >
        <view v-if="catColor && it.color" class="w-[9px] h-[9px] rounded-[2px] shrink-0" :style="`background:${it.id === active ? '#fff' : it.color}`" />
        <text class="flex-1">{{ it.label }}</text>
        <text v-if="it.count" class="text-[12px] tabular-nums opacity-80">{{ it.count }}</text>
        <view v-else-if="it.dot" class="w-[6px] h-[6px] rounded-full bg-count-bg" />
      </view>
    </view>
  </scroll-view>
</template>
