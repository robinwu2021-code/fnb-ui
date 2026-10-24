<script setup lang="ts">
/** 桌台：导航 = 桌区，内容 = 状态统计 + 桌台宫格，详情 = 所选桌台 */
import { computed, ref } from "vue";
import PosShell from "@/components/pos/PosShell.vue";
import NavRail from "@/components/pos/NavRail.vue";
import TableCard from "@/components/pos/TableCard.vue";
import PosBtn from "@/components/ui/PosBtn.vue";
import { TABLES, yuan } from "@/mock/data";
import { showToast } from "@/stores/toast";

const zone = ref("all");
const cur = ref("A03");
const ZONES = [{ id: "all", label: "全部" }, { id: "hall", label: "大厅" }, { id: "room", label: "包间" }, { id: "take", label: "外带" }];
const navItems = computed(() => ZONES.map((z) => ({ ...z, count: TABLES.filter((t) => z.id === "all" || t.zone === z.id).length })));
const list = computed(() => TABLES.filter((t) => zone.value === "all" || t.zone === zone.value));
const stats = computed(() => (["free", "dining", "billing", "reserved"] as const).map((s) => TABLES.filter((t) => t.status === s).length));
const table = computed(() => TABLES.find((t) => t.id === cur.value)!);
const go = (url: string) => uni.redirectTo({ url });
</script>

<template>
  <PosShell page="tables" detail-label="查看桌台">
    <template #nav="{ horizontal }"><NavRail :items="navItems" :active="zone" :horizontal="horizontal" @select="zone = $event" /></template>
    <template #main>
      <view class="pos-panel grid grid-cols-4 py-3 text-center">
        <view v-for="(n, i) in stats" :key="i"><view class="text-[20px] font-700 tabular-nums">{{ n }}</view><view class="text-[12px] text-sub">{{ ["空闲", "用餐中", "待结账", "已预订"][i] }}</view></view>
      </view>
      <scroll-view scroll-y class="flex-1 min-h-0">
        <view class="grid grid-cols-[repeat(auto-fill,minmax(112px,1fr))] gap-[var(--pos-gap)] p-[2px]">
          <TableCard v-for="t in list" :key="t.id" :table="t" :current="t.id === cur" @select="cur = t.id" />
        </view>
      </scroll-view>
    </template>
    <template #detail>
      <view class="pos-panel h-full flex flex-col gap-3 p-3.5">
        <view class="zone flex flex-col gap-2" data-zone="Z2 上下文">
          <view class="flex items-baseline justify-between"><text class="text-[20px] font-700">{{ table.id }}</text><text class="pos-chip">{{ { free: "空闲", dining: "用餐中", billing: "待结账", reserved: "已预订" }[table.status] }}</text></view>
          <text class="text-[13px] text-sub">{{ table.note || (table.guests ? `${table.guests} 人 · 已用餐 ${table.minutes} 分钟` : `${table.seats} 座`) }}</text>
          <view v-if="table.status !== 'free'" class="flex flex-wrap gap-1">
            <PosBtn v-for="op in ['改人数', '换桌', '并台', '清台']" :key="op" size="sm" @click="showToast(`${op}：骨架只放入口`)">{{ op }}</PosBtn>
          </view>
        </view>
        <view v-if="table.amount" class="rounded-tile bg-faint px-3.5 py-3 flex justify-between items-baseline"><text class="text-[13px] text-sub">已点</text><text class="text-[22px] font-700 tabular-nums">{{ yuan(table.amount) }}</text></view>
        <view class="mt-auto flex gap-1.5">
          <template v-if="table.status === 'free' || table.status === 'reserved'"><PosBtn variant="pay" size="lg" block @click="showToast(`${table.id} 已开台`)">开台</PosBtn></template>
          <template v-else>
            <PosBtn variant="send" size="lg" block @click="go('/pages/order/index')">加菜</PosBtn>
            <PosBtn variant="pay" size="lg" block @click="go('/pages/checkout/index')">结账</PosBtn>
          </template>
        </view>
      </view>
    </template>
  </PosShell>
</template>
