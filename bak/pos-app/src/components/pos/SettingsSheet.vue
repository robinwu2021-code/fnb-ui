<script setup lang="ts">
/** 设置（Z7 侧滑面板）：布局、组件风格、主题色、深浅、密度、菜单视图、功能区标注。改动即时生效并记在本机。 */
import PosSheet from "@/components/ui/PosSheet.vue";
import PosSeg from "@/components/ui/PosSeg.vue";
import { settingsOpen, ui } from "@/stores/ui";
import { DENSITY, LAYOUTS, SKINS, STYLES, type LayoutId, type SkinId, type StyleId } from "@/theme/tokens";

const layouts = Object.entries(LAYOUTS) as [LayoutId, (typeof LAYOUTS)[LayoutId]][];
const styles = Object.entries(STYLES) as [StyleId, (typeof STYLES)[StyleId]][];
const skins = Object.entries(SKINS) as [SkinId, (typeof SKINS)[SkinId]][];
/** 布局缩略图：三个小格的排列 */
function cells(id: LayoutId) {
  const { nav, detail } = LAYOUTS[id];
  if (nav === "top") return detail === "left" ? "d n|d m" : "n d|m d";
  const order = nav === "left" ? (detail === "left" ? "d n m" : "n m d") : detail === "left" ? "d m n" : "m n d";
  return order;
}
</script>

<template>
  <PosSheet title="界面设置" sub="只影响本机显示" mode="side" :width="440" @close="settingsOpen.value = false">
    <view class="text-[13px] font-600 mt-2 mb-2">布局</view>
    <view class="grid grid-cols-3 gap-2">
      <view
        v-for="[id, l] in layouts" :key="id" class="p-2 rounded-ctl bg-faint flex flex-col gap-1.5"
        :class="ui.layout === id ? '!bg-on-bg text-on-fg' : ''" @click="ui.layout = id"
      >
        <view class="flex flex-col gap-[2px] h-[34px]">
          <view v-for="(row, ri) in cells(id).split('|')" :key="ri" class="flex-1 flex gap-[2px]">
            <view
              v-for="(c, ci) in row.split(' ')" :key="ci" class="rounded-[2px]"
              :class="c === 'm' ? 'flex-[3] bg-current opacity-25' : c === 'd' ? 'flex-[2] bg-current opacity-60' : 'flex-1 bg-current opacity-40'"
            />
          </view>
        </view>
        <text class="text-[13px] font-600">{{ id }}</text>
        <text class="text-[11px] opacity-75">{{ l.note }}</text>
      </view>
    </view>

    <view class="text-[13px] font-600 mt-5 mb-2">组件风格</view>
    <view class="flex flex-col gap-1.5">
      <view
        v-for="[id, s] in styles" :key="id" class="px-3 py-2.5 rounded-ctl bg-faint" :class="ui.style === id ? '!bg-on-bg text-on-fg' : ''"
        @click="ui.style = id"
      >
        <view class="text-[14px] font-600">{{ s.name }}</view>
        <view class="text-[12px] opacity-75">{{ s.note }}</view>
      </view>
    </view>

    <view class="text-[13px] font-600 mt-5 mb-2">主题色 <text class="font-400 text-sub">· 底色固定黑白灰</text></view>
    <view class="flex flex-wrap gap-2">
      <view
        v-for="[id, s] in skins" :key="id" class="h-[34px] pl-1.5 pr-3 flex items-center gap-1.5 rounded-chip bg-faint text-[13px]"
        :class="ui.skin === id ? '!bg-on-bg text-on-fg' : ''" @click="ui.skin = id"
      >
        <view class="w-[20px] h-[20px] rounded-full" :style="`background:${ui.mode === 'dark' ? s.dark : s.light}`" />{{ s.name }}
      </view>
    </view>

    <view class="flex flex-wrap items-center gap-x-6 gap-y-3 mt-5">
      <view class="flex items-center gap-2"><text class="text-[13px] font-600">深浅</text>
        <PosSeg v-model="ui.mode" :options="[{ value: 'light', label: '浅色' }, { value: 'dark', label: '深色' }]" /></view>
      <view class="flex items-center gap-2"><text class="text-[13px] font-600">密度</text>
        <PosSeg v-model="ui.density" :options="Object.entries(DENSITY).map(([value, d]) => ({ value, label: d.name }))" /></view>
      <view class="flex items-center gap-2"><text class="text-[13px] font-600">菜单视图</text>
        <PosSeg v-model="ui.view" :options="[{ value: 'grid', label: '卡片' }, { value: 'list', label: '列表' }]" /></view>
      <view class="flex items-center gap-2"><text class="text-[13px] font-600">标注功能区</text>
        <PosSeg :model-value="ui.zones ? 'on' : 'off'" :options="[{ value: 'off', label: '关' }, { value: 'on', label: '开' }]" @update:model-value="ui.zones = $event === 'on'" /></view>
    </view>
  </PosSheet>
</template>
