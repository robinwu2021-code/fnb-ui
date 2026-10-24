<script setup lang="ts">
/** 订单：导航 = 订单状态，内容 = 订单列表，详情 = 订单详情 */
import { computed, ref } from "vue";
import PosShell from "@/components/pos/PosShell.vue";
import NavRail from "@/components/pos/NavRail.vue";
import PosBtn from "@/components/ui/PosBtn.vue";
import { ORDER_LIST, yuan } from "@/mock/data";
import { showToast } from "@/stores/toast";

const status = ref("进行中");
const cur = ref("#1024");
const STATUS = ["进行中", "挂起", "待结账", "已结账"];
const navItems = computed(() => STATUS.map((s) => ({ id: s, label: s, count: ORDER_LIST.filter((o) => o.status === s).length })));
const list = computed(() => ORDER_LIST.filter((o) => o.status === status.value));
const sel = computed(() => ORDER_LIST.find((o) => o.no === cur.value) ?? ORDER_LIST[0]);
</script>

<template>
  <PosShell page="orders" detail-label="查看订单">
    <template #nav="{ horizontal }"><NavRail :items="navItems" :active="status" :horizontal="horizontal" @select="status = $event" /></template>
    <template #main>
      <scroll-view scroll-y class="flex-1 min-h-0">
        <view class="flex flex-col gap-[var(--pos-gap)]">
          <view
            v-for="o in list" :key="o.no" class="pos-panel px-4 py-3 flex items-center gap-3" :class="o.no === cur ? 'bg-sel-bg text-sel-fg pos-sel' : ''"
            @click="cur = o.no"
          >
            <view class="flex-1"><view class="text-[15px] font-600">{{ o.label }} <text class="text-[12px] text-sub font-400">{{ o.no }}</text></view>
              <view class="text-[12px] text-sub">{{ o.items }} 件 · {{ o.time }}</view></view>
            <text class="pos-chip">{{ o.status }}</text>
            <text class="text-[16px] font-700 tabular-nums">{{ yuan(o.amount) }}</text>
          </view>
          <view v-if="!list.length" class="py-16 text-center text-sub text-[13px]">这里没有订单</view>
        </view>
      </scroll-view>
    </template>
    <template #detail>
      <view class="pos-panel h-full flex flex-col gap-3 p-3.5">
        <view class="flex items-baseline justify-between"><text class="text-[20px] font-700">{{ sel.label }}</text><text class="text-[12px] text-sub">{{ sel.no }} · {{ sel.status }}</text></view>
        <view class="rounded-tile bg-faint px-3.5 py-3 flex justify-between items-baseline"><text class="text-[13px] text-sub">{{ sel.items }} 件</text><text class="text-[22px] font-700 tabular-nums">{{ yuan(sel.amount) }}</text></view>
        <view class="mt-auto flex flex-wrap gap-1.5">
          <PosBtn size="lg" block @click="showToast('已补打')">补打</PosBtn>
          <PosBtn size="lg" block @click="showToast('合单：骨架只放入口')">合单</PosBtn>
          <PosBtn variant="pay" size="lg" block @click="showToast(sel.status === '挂起' ? '已取单' : '已打开订单')">{{ sel.status === "挂起" ? "取单" : "打开" }}</PosBtn>
        </view>
      </view>
    </template>
  </PosShell>
</template>
