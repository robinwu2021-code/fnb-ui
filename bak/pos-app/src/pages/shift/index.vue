<script setup lang="ts">
/** 交班：没有导航区；内容 = 当班数据与钱箱核对，详情 = 实盘与交班 */
import { computed, ref } from "vue";
import PosShell from "@/components/pos/PosShell.vue";
import PosBtn from "@/components/ui/PosBtn.vue";
import { showToast } from "@/stores/toast";
import { yuan } from "@/mock/data";

const SHOULD = 73800;
const counted = ref("735");
const diff = computed(() => Math.round(parseFloat(counted.value || "0") * 100) - SHOULD);
</script>

<template>
  <PosShell page="shift" :has-nav="false" detail-label="交班">
    <template #main>
      <view class="pos-panel grid grid-cols-3 py-4 text-center">
        <view><view class="text-[22px] font-700 tabular-nums">¥1,438</view><view class="text-[12px] text-sub">当班收款</view></view>
        <view><view class="text-[22px] font-700 tabular-nums">¥438</view><view class="text-[12px] text-sub">现金净收</view></view>
        <view><view class="text-[22px] font-700 tabular-nums">¥1,000</view><view class="text-[12px] text-sub">线上净收</view></view>
      </view>
      <view class="pos-panel px-4 py-2">
        <view v-for="[k, v] in [['开班备用金', '¥300.00'], ['现金净收', '＋¥438.00'], ['系统应有现金', yuan(SHOULD)]]" :key="k" class="flex justify-between py-2.5 text-[14px]">
          <text class="text-sub">{{ k }}</text><text class="font-600 tabular-nums">{{ v }}</text>
        </view>
      </view>
      <view class="rounded-tile bg-warning-tint text-warning-text px-4 py-3 text-[13px]">A03 等 2 桌尚未结账，交班后由下一班继续处理</view>
    </template>
    <template #detail>
      <view class="pos-panel h-full flex flex-col gap-3 p-3.5">
        <text class="text-[13px] font-600">实盘现金（元）</text>
        <input v-model="counted" type="digit" class="h-[var(--pos-ctl)] px-4 rounded-btn bg-faint text-[16px] tabular-nums" />
        <view class="flex justify-between text-[14px]"><text class="text-sub">盘点差异</text><text class="font-600 tabular-nums" :class="diff ? 'text-danger-text' : ''">{{ diff >= 0 ? "" : "−" }}{{ yuan(Math.abs(diff)) }}</text></view>
        <view class="text-[12px] text-sub">有差异需填写说明，提交店长审批。交班不等于门店日结。</view>
        <view class="mt-auto flex gap-1.5"><PosBtn size="lg" block @click="showToast('当班报表已打印')">当班报表</PosBtn><PosBtn variant="pay" size="lg" block @click="showToast('已提交交班')">核对并交班</PosBtn></view>
      </view>
    </template>
  </PosShell>
</template>
