<script setup lang="ts">
defineProps<{ value: number; size?: "sm" | "md" }>();
defineEmits<{ (e: "change", delta: number): void }>();
</script>

<template>
  <view class="inline-flex items-center rounded-btn bg-faint" :class="size === 'md' ? 'h-[var(--pos-ctl)]' : 'h-[28px]'" @click.stop>
    <view class="flex items-center justify-center h-full px-2.5 text-ink" @click="$emit('change', -1)">−</view>
    <text class="min-w-[22px] text-center font-600 tabular-nums">{{ value }}</text>
    <view class="flex items-center justify-center h-full px-2.5 text-ink" @click="$emit('change', 1)">＋</view>
  </view>
</template>
