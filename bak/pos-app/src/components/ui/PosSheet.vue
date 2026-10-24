<script setup lang="ts">
/** 浮层：mode=modal 居中弹窗；mode=side 右侧滑出面板（Z7） */
withDefaults(defineProps<{ title: string; sub?: string; mode?: "modal" | "side"; width?: number }>(), { mode: "modal", width: 460 });
defineEmits<{ (e: "close"): void }>();
</script>

<template>
  <view
    class="zone absolute inset-0 z-40 flex bg-[rgba(10,12,16,.45)]" data-zone="Z7 浮层"
    :class="mode === 'side' ? 'justify-end' : 'items-center justify-center p-6'" @click="$emit('close')"
  >
    <view
      class="flex flex-col bg-surface text-ink overflow-hidden" :class="mode === 'side' ? 'h-full rounded-l-panel' : 'max-h-full rounded-panel'"
      :style="`width:${width}px;max-width:100%`" @click.stop
    >
      <view class="flex items-center gap-2 px-5 pt-4 pb-2">
        <view class="flex-1 min-w-0"><view class="text-[18px] font-700">{{ title }}</view><view v-if="sub" class="text-[13px] text-sub">{{ sub }}</view></view>
        <view class="w-[36px] h-[36px] flex items-center justify-center rounded-btn text-sub" @click="$emit('close')">✕</view>
      </view>
      <scroll-view scroll-y class="flex-1 min-h-0"><view class="px-5 pb-4"><slot /></view></scroll-view>
      <view v-if="$slots.footer" class="flex gap-2 px-4 pt-2 pb-4"><slot name="footer" /></view>
    </view>
  </view>
</template>
