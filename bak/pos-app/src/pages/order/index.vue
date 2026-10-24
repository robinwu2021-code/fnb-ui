<script setup lang="ts">
/** 点餐收银：导航 = 菜品分类，内容 = 工具栏 + 菜品宫格，详情 = 账单坞 */
import { computed, ref } from "vue";
import PosShell from "@/components/pos/PosShell.vue";
import NavRail from "@/components/pos/NavRail.vue";
import DishCard from "@/components/pos/DishCard.vue";
import OrderDock from "@/components/pos/OrderDock.vue";
import PosBtn from "@/components/ui/PosBtn.vue";
import { CATEGORIES, DISHES, HOT, type Dish } from "@/mock/data";
import { CAT_COLORS } from "@/theme/tokens";
import { addDish, qtyInCat, qtyOf } from "@/stores/order";
import { showToast } from "@/stores/toast";
import { ui } from "@/stores/ui";

const cat = ref("hot");
const q = ref("");
const tag = ref("全部");
const navItems = computed(() => CATEGORIES.map((c) => ({ id: c.id, label: c.name, dot: c.id !== "hot" && qtyInCat(c.id) > 0, color: CAT_COLORS[c.id] })));
const dishes = computed(() => {
  let list = q.value ? DISHES.filter((d) => d.name.includes(q.value) || d.id.startsWith(q.value)) : cat.value === "hot" ? DISHES.filter((d) => HOT.includes(d.id)) : DISHES.filter((d) => d.cat === cat.value);
  if (tag.value !== "全部") list = list.filter((d) => d.tags?.includes(tag.value));
  return list;
});
function add(d: Dish) {
  if (!addDish(d)) showToast(`${d.name} 已沽清`);
}
</script>

<template>
  <PosShell page="order" detail-label="查看订单">
    <template #nav="{ horizontal }"><NavRail :items="navItems" :active="cat" :horizontal="horizontal" @select="cat = $event; q = ''" /></template>
    <template #main>
      <view class="flex flex-wrap items-center gap-1.5">
        <input v-model="q" class="flex-1 min-w-[200px] h-[var(--pos-ctl)] px-4 rounded-btn bg-surface pos-frame text-[14px]" placeholder="搜索菜名 / 编号" />
        <view
          v-for="t in ['全部', '辣', '素', '特价']" :key="t" class="h-[var(--pos-ctl-s)] px-3.5 flex items-center rounded-chip text-[13px] font-500 pos-frame"
          :class="tag === t ? 'bg-on-bg text-on-fg' : 'bg-surface text-sub'" @click="tag = t"
        >{{ t }}</view>
        <PosBtn size="sm" @click="showToast('临时菜：骨架只放入口')">＋ 临时菜</PosBtn>
        <PosBtn size="sm" @click="showToast('沽清模式：骨架只放入口')">沽清</PosBtn>
      </view>
      <scroll-view scroll-y class="flex-1 min-h-0">
        <view class="grid gap-[var(--pos-gap)] p-[2px]" :class="ui.view === 'list' ? 'grid-cols-1' : 'grid-cols-[repeat(auto-fill,minmax(136px,1fr))]'">
          <DishCard v-for="d in dishes" :key="d.id" :dish="d" :qty="qtyOf(d.id)" @add="add(d)" />
        </view>
      </scroll-view>
    </template>
    <template #detail><OrderDock /></template>
  </PosShell>
</template>
