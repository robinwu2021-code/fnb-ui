<script setup lang="ts">
/** Z5 账单坞：Z2 上下文条（桌台 + 常用操作）、订单行、Z6 金额与操作。 */
import PosBtn from "@/components/ui/PosBtn.vue";
import PosStepper from "@/components/ui/PosStepper.vue";
import { changeQty, order, pending, sendToKitchen, sent, totals } from "@/stores/order";
import { showToast } from "@/stores/toast";
import { yuan } from "@/mock/data";

const QUICK = ["改人数", "换桌", "并台", "整单备注", "更多"];
function send() {
  const n = sendToKitchen();
  showToast(n ? `已送厨 ${n} 道菜` : "没有待下单的菜");
}
function checkout() { uni.redirectTo({ url: "/pages/checkout/index" }); }
</script>

<template>
  <view class="pos-panel h-full flex flex-col overflow-hidden">
    <view class="zone px-3.5 pt-3 pb-2.5 flex flex-col gap-2" data-zone="Z2 上下文">
      <view class="flex items-baseline justify-between">
        <text class="text-[20px] font-700">{{ order.table }}</text>
        <text class="text-[12px] text-sub">{{ order.zone }} · {{ order.guests }} 人 · {{ order.minutes }} 分钟</text>
      </view>
      <view class="flex flex-wrap gap-1">
        <PosBtn v-for="q in QUICK" :key="q" size="sm" @click="showToast(`${q}：骨架只放入口`)">{{ q }}</PosBtn>
      </view>
    </view>
    <scroll-view scroll-y class="flex-1 min-h-0">
      <view class="px-2">
        <view v-if="pending.length" class="px-1.5 pt-2 pb-1 text-[12px] text-sub">待下单</view>
        <view v-for="l in pending" :key="l.uid" class="grid grid-cols-[1fr_auto] gap-x-2 gap-y-0.5 px-1.5 py-2 rounded-ctl">
          <text class="font-500">{{ l.name }}</text>
          <text class="text-right font-600 tabular-nums">{{ yuan(l.unit * l.qty) }}</text>
          <text class="text-[12px] text-sub">{{ l.spec }}</text>
          <view class="justify-self-end"><PosStepper :value="l.qty" @change="changeQty(l, $event)" /></view>
        </view>
        <view v-if="sent.length" class="px-1.5 pt-3 pb-1 text-[12px] text-sub">已下单</view>
        <view v-for="l in sent" :key="l.uid" class="grid grid-cols-[1fr_auto] gap-x-2 gap-y-0.5 px-1.5 py-2 rounded-ctl">
          <text class="font-500">{{ l.name }}</text>
          <text class="text-right font-600 tabular-nums">{{ yuan(l.unit * l.qty) }}</text>
          <view class="flex items-center gap-1.5 text-[12px] text-sub">
            <text class="pos-chip" :class="l.served ? '!bg-success-tint !text-success-text' : l.rush ? '!bg-warning-tint !text-warning-text' : ''">{{ l.served ? "已上" : l.rush ? "催菜中" : "制作中" }}</text>
            <text>{{ l.spec }}</text>
          </view>
          <view class="justify-self-end flex items-center gap-1.5 text-[12px] text-sub">
            ×{{ l.qty }}
            <text v-if="!l.served && !l.rush" class="pos-chip !bg-primary-tint !text-primary-text" @click="l.rush = true; showToast(`已通知厨房催 ${l.name}`)">催</text>
          </view>
        </view>
      </view>
    </scroll-view>
    <view class="zone p-2 flex flex-col gap-2" data-zone="Z6 金额与操作">
      <view class="rounded-tile bg-faint px-3.5 py-3 flex flex-col gap-1">
        <view class="flex flex-wrap gap-1 mb-1">
          <text class="pos-chip !bg-surface" :class="order.member ? '!bg-on-bg !text-on-fg' : ''" @click="order.member = !order.member">会员</text>
          <text class="pos-chip !bg-surface" @click="showToast('优惠券：骨架只放入口')">优惠券</text>
          <text class="pos-chip !bg-surface" @click="showToast('整单折扣需主管授权')">折扣</text>
          <text class="pos-chip !bg-surface" :class="order.roundOff ? '!bg-on-bg !text-on-fg' : ''" @click="order.roundOff = !order.roundOff">抹零</text>
        </view>
        <view class="flex justify-between text-[13px] text-sub tabular-nums"><text>小计</text><text>{{ yuan(totals.sub) }}</text></view>
        <view class="flex justify-between text-[13px] text-sub tabular-nums"><text>服务费 10%</text><text>{{ yuan(totals.service) }}</text></view>
        <view v-if="totals.member" class="flex justify-between text-[13px] text-primary-text tabular-nums"><text>会员 9.5 折</text><text>−{{ yuan(totals.member) }}</text></view>
        <view v-if="totals.round" class="flex justify-between text-[13px] text-primary-text tabular-nums"><text>抹零</text><text>−{{ yuan(totals.round) }}</text></view>
        <view class="flex justify-between items-baseline mt-1"><text class="text-[13px] text-sub">应收</text><text class="text-[24px] font-700 tabular-nums">{{ yuan(totals.due) }}</text></view>
      </view>
      <view class="flex gap-1.5">
        <PosBtn size="lg" @click="showToast('已挂单')">挂</PosBtn>
        <PosBtn size="lg" block @click="showToast('预结单已打印')">预结</PosBtn>
        <PosBtn size="lg" variant="send" block @click="send">送厨</PosBtn>
        <PosBtn size="lg" variant="pay" block @click="checkout">结账</PosBtn>
      </view>
    </view>
  </view>
</template>
