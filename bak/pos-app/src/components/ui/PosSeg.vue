<script setup lang="ts">
/** 分段控件：2–4 个互斥选项 */
defineProps<{ options: { value: string; label: string }[]; modelValue: string }>();
defineEmits<{ (e: "update:modelValue", v: string): void }>();
</script>

<template>
  <view class="inline-flex gap-0.5 p-[3px] rounded-btn bg-faint">
    <view
      v-for="o in options" :key="o.value"
      class="h-[30px] px-3 flex items-center rounded-btn text-[13px] font-500 whitespace-nowrap"
      :class="o.value === modelValue ? 'bg-surface text-ink' : 'text-sub'"
      @click="$emit('update:modelValue', o.value)"
    >{{ o.label }}</view>
  </view>
</template>
