<script setup lang="ts">
/** 按钮。variant 决定颜色从哪组变量取，形状（--r-btn）由组件风格决定。 */
withDefaults(defineProps<{ variant?: "pay" | "send" | "soft" | "sec" | "ghost" | "danger" | "main"; size?: "sm" | "md" | "lg"; block?: boolean }>(), {
  variant: "sec", size: "md", block: false,
});
defineEmits<{ (e: "click"): void }>();
const cls: Record<string, string> = {
  pay: "bg-pay-bg text-pay-fg", main: "bg-primary text-on-primary", send: "bg-send-bg text-send-fg",
  soft: "bg-primary-tint text-primary-text", sec: "bg-faint text-ink", ghost: "bg-transparent text-sub", danger: "bg-danger-tint text-danger-text",
};
</script>

<template>
  <view
    :class="[size === 'sm' ? 'pos-btn-sm' : 'pos-btn', size === 'lg' ? 'h-[52px] text-[15px]' : '', block ? 'flex-1' : '', cls[variant], variant === 'sec' ? 'pos-frame' : '']"
    hover-class="opacity-80"
    @click="$emit('click')"
  ><slot /></view>
</template>
