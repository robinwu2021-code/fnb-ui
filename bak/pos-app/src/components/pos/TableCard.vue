<script setup lang="ts">
/** 桌台卡（Z4）：状态用底色 + 文字，当前桌加主题色描边。 */
import { computed } from "vue";
import { yuan, type Table } from "@/mock/data";

const props = defineProps<{ table: Table; current?: boolean }>();
defineEmits<{ (e: "select"): void }>();
const LABEL = { free: "空闲", dining: "用餐中", billing: "待结账", reserved: "已预订" };
const tone = computed(() => ({
  free: "bg-surface", dining: "bg-sel-bg text-sel-fg", billing: "bg-warning-tint", reserved: "bg-transparent border border-dashed border-line",
})[props.table.status]);
</script>

<template>
  <view class="flex flex-col gap-0.5 p-2.5 min-h-[88px] rounded-tile pos-frame" :class="tone" :style="current ? 'box-shadow: inset 0 0 0 2px var(--sh-primary)' : ''" @click="$emit('select')">
    <text class="text-[12px] opacity-75">{{ LABEL[table.status] }}</text>
    <text class="text-[19px] font-700 tabular-nums">{{ table.id }}</text>
    <text class="text-[12px] opacity-75 truncate">{{ table.note || (table.guests ? `${table.guests}/${table.seats} 人 · ${table.minutes} 分` : `${table.seats} 座`) }}</text>
    <text v-if="table.amount" class="mt-auto font-600 tabular-nums">{{ yuan(table.amount) }}</text>
  </view>
</template>
