<script setup lang="ts">
/** 结账：没有导航区；内容 = 支付方式，详情 = 账单 + 现金键盘 */
import { computed, ref } from "vue";
import PosShell from "@/components/pos/PosShell.vue";
import PosBtn from "@/components/ui/PosBtn.vue";
import { totals } from "@/stores/order";
import { showToast } from "@/stores/toast";
import { yuan } from "@/mock/data";

const METHODS = [["wechat", "微信", "扫付款码"], ["alipay", "支付宝", "扫付款码"], ["cash", "现金", "自动找零"], ["card", "银行卡", "刷卡 / 挥卡"], ["member", "会员储值", "余额 ¥1,260"], ["mix", "组合支付", "分笔付"]];
const method = ref("cash");
const input = ref("");
const received = computed(() => Math.round(parseFloat(input.value || "0") * 100) || 0);
function key(k: string) {
  if (k === "⌫") input.value = input.value.slice(0, -1);
  else if (k === "." && input.value.includes(".")) return;
  else if (/\.\d\d$/.test(input.value)) return;
  else input.value += k;
}
function confirm() {
  if (method.value === "cash" && received.value < totals.value.due) return showToast("实收不足，不能确认");
  showToast(`收款成功 ${yuan(totals.value.due)}`);
}
</script>

<template>
  <PosShell page="orders" :has-nav="false" detail-label="收款">
    <template #main>
      <view class="pos-panel p-4 flex flex-col gap-3">
        <text class="text-[13px] font-600">支付方式</text>
        <view class="grid grid-cols-3 gap-[var(--pos-gap)]">
          <view
            v-for="[id, name, sub] in METHODS" :key="id" class="p-3.5 rounded-tile pos-frame" :class="method === id ? 'bg-on-bg text-on-fg' : 'bg-faint'"
            @click="method = id"
          ><view class="text-[15px] font-600">{{ name }}</view><view class="text-[12px] opacity-75">{{ sub }}</view></view>
        </view>
        <view class="rounded-tile bg-warning-tint text-warning-text px-3.5 py-2.5 text-[13px]">还有菜品未送厨时，收款后自动送厨</view>
      </view>
    </template>
    <template #detail>
      <view class="pos-panel h-full flex flex-col gap-3 p-3.5">
        <view class="text-center"><view class="text-[13px] text-sub">应收</view><view class="text-[30px] font-700 tabular-nums">{{ yuan(totals.due) }}</view></view>
        <view class="rounded-tile bg-faint px-3.5 py-2.5 flex flex-col gap-1">
          <view class="flex justify-between text-[14px]"><text class="text-sub">实收</text><text class="font-600 tabular-nums">{{ yuan(received) }}</text></view>
          <view class="flex justify-between text-[14px]"><text class="text-sub">{{ received >= totals.due ? "找零" : "还差" }}</text><text class="font-600 tabular-nums">{{ yuan(Math.abs(received - totals.due)) }}</text></view>
        </view>
        <view class="grid grid-cols-3 gap-1.5">
          <view v-for="k in ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', '⌫']" :key="k" class="h-[52px] flex items-center justify-center rounded-ctl bg-faint text-[20px] font-500 tabular-nums" @click="key(k)">{{ k }}</view>
        </view>
        <view class="mt-auto flex"><PosBtn variant="pay" size="lg" block @click="confirm">确认收款</PosBtn></view>
      </view>
    </template>
  </PosShell>
</template>
