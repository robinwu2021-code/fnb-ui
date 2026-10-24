<script setup lang="ts">
/** 菜品卡（Z4）。已点：sel 色 + 份数角标；沽清：灰；「经典收银」风格下按分类上色。 */
import { computed } from "vue";
import { ui } from "@/stores/ui";
import { CAT_COLORS, STYLES } from "@/theme/tokens";
import { yuan, type Dish } from "@/mock/data";

const props = defineProps<{ dish: Dish; qty: number }>();
defineEmits<{ (e: "add"): void }>();
const cat = computed(() => STYLES[ui.style].catTiles && !props.dish.soldOut);
const style = computed(() => (cat.value ? `background:${CAT_COLORS[props.dish.cat]};color:#fff` : ""));
</script>

<template>
  <view
    class="relative flex flex-col gap-0.5 p-3 rounded-tile pos-frame"
    :class="[dish.soldOut ? 'bg-faint text-sub' : qty ? 'bg-sel-bg text-sel-fg pos-sel' : 'bg-surface', ui.view === 'list' ? 'flex-row items-center min-h-[var(--pos-ctl)]' : 'min-h-[var(--pos-tile-h)]']"
    :style="style" @click="$emit('add')"
  >
    <text v-if="qty" class="absolute top-2 right-2 min-w-[20px] h-[20px] px-1.5 rounded-chip bg-count-bg text-count-fg text-[12px] font-600 leading-[20px] text-center">{{ qty }}</text>
    <text class="text-[15px] font-600 pr-6 truncate" :class="ui.view === 'list' ? 'flex-1' : ''">{{ dish.name }}</text>
    <text class="text-[12px] opacity-70 truncate">{{ dish.soldOut ? "已沽清" : dish.stock ? `剩 ${dish.stock} · ${dish.desc}` : dish.desc }}</text>
    <text class="text-[16px] font-700 tabular-nums" :class="ui.view === 'list' ? 'ml-4' : 'mt-auto'">{{ yuan(dish.price, false) }}</text>
  </view>
</template>
