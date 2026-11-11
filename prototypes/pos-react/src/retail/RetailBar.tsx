// 零售收银 · 页首原型控制：选关键画面 + 模拟扫码（业态 = 零售且在收银页签时出现在 DevBar 里）。不属于产品界面。
import { useStore } from '@/store';
import { Seg } from '@/components/ui/toggle-group';
import { Button } from '@/components/ui/button';
import { goReturn, type RetScreen } from './sales';
import { useStock } from './stock';
import { useBiz } from '@/biz/store';
import { goScreen, scanSim, useR, type Screen } from './RetailPos';

const SCREENS: { v: Screen; zh: string; en: string }[] = [
  { v: 'empty', zh: '① 空车待扫码', en: '① Empty cart' }, { v: 'cart', zh: '② 扫了 14 件', en: '② 14 items scanned' }, { v: 'unknown', zh: '③ 未知条码', en: '③ Unknown barcode' },
  { v: 'peek', zh: '④ 查价', en: '④ Price check' }, { v: 'weigh', zh: '⑤ 称重', en: '⑤ Weighing' }, { v: 'sku', zh: '⑥ 选规格', en: '⑥ Variant' }, { v: 'held', zh: '⑦ 取单', en: '⑦ Recall' },
  { v: 'clear', zh: '⑧ 清空确认', en: '⑧ Clear cart' }, { v: 'pay', zh: '⑨ 收款 · 现金', en: '⑨ Pay · cash' }, { v: 'done', zh: '⑩ 收款完成', en: '⑩ Paid' },
  { v: 'xcart', zh: '⑪ 换货 · 补差价', en: '⑪ Exchange · pay diff' }, { v: 'xrefund', zh: '⑫ 换货 · 退差价', en: '⑫ Exchange · refund diff' },
];
const SCANS: { k: Parameters<typeof scanSim>[0]; zh: string; en: string }[] = [
  { k: 'std', zh: '标准码', en: 'Barcode' }, { k: 'weighCode', zh: '称重码', en: 'Scale label' }, { k: 'loose', zh: '散称', en: 'Loose' },
  { k: 'sku', zh: '多规格', en: 'Variant' }, { k: 'unknown', zh: '未知码', en: 'Unknown' }, { k: 'payCode', zh: '付款码', en: 'Pay code' },
];
const RETS: { v: RetScreen; zh: string; en: string }[] = [
  { v: 'r1', zh: '① 找原单', en: '① Find order' }, { v: 'r2', zh: '② 选商品', en: '② Items' }, { v: 'r3', zh: '③ 原因与金额', en: '③ Reason & amount' },
  { v: 'r4', zh: '④ 退款', en: '④ Refund' }, { v: 'nr', zh: '⑤ 无单退货 · 授权', en: '⑤ No receipt · approval' }, { v: 'rdone', zh: '⑥ 退款成功', en: '⑥ Refunded' },
  { v: 'x2', zh: '⑦ 换货 · 选退回', en: '⑦ Exchange · items back' },
];
/** 库存作业画面预设：直接选中某张示例单据 */
const STOCKS: { v: string; kind: 'receive' | 'count' | 'loss' | 'transfer'; zh: string; en: string }[] = [
  { v: 'RK1111-03', kind: 'receive', zh: '① 入库草稿', en: '① Receipt draft' }, { v: 'RK1111-02', kind: 'receive', zh: '② 已入库', en: '② Received' },
  { v: 'PD1111-01', kind: 'count', zh: '③ 盘点中', en: '③ Counting' }, { v: 'PD1110-02', kind: 'count', zh: '④ 盘点待审批', en: '④ Count pending' },
  { v: 'BS1111-01', kind: 'loss', zh: '⑤ 报损', en: '⑤ Write-off' }, { v: 'DB', kind: 'transfer', zh: '⑥ 调拨（未实现）', en: '⑥ Transfer (todo)' },
];
export function RetailBar() {
  const en = useStore((s) => s.lang) === 'en'; const screen = useR((s) => s.screen);
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2"><span className="text-sm text-sub w-72px flex-none">{en ? 'Stock' : '库存画面'}</span>
        <Seg value={''} onChange={(v) => { const x = STOCKS.find((s) => s.v === v)!; useBiz.getState().set({ rwb: 'stock' }); useStock.getState().set({ kind: x.kind, sel: v, filter: 'all' }); }} items={STOCKS.map((s) => ({ v: s.v, label: en ? s.en : s.zh }))} /></div>
      <div className="flex flex-wrap items-center gap-2"><span className="text-sm text-sub w-72px flex-none">{en ? 'Return' : '退货画面'}</span>
        <Seg value={'' as RetScreen} onChange={(v) => { useBiz.getState().set({ rwb: 'orders' }); goReturn(v); }} items={RETS.map((s) => ({ v: s.v, label: en ? s.en : s.zh }))} /></div>
      <div className="flex flex-wrap items-center gap-2"><span className="text-sm text-sub w-72px flex-none">{en ? 'Screen' : '画面'}</span>
        <Seg value={screen} onChange={(v) => { useBiz.getState().set({ rwb: 'pos' }); goScreen(v); }} items={SCREENS.map((s) => ({ v: s.v, label: en ? s.en : s.zh }))} /></div>
      <div className="flex flex-wrap items-center gap-2"><span className="text-sm text-sub w-72px flex-none">{en ? 'Sim scan' : '模拟扫码'}</span>
        {SCANS.map((s) => <Button key={s.k} variant="muted" size="sm" onClick={() => scanSim(s.k)}>{en ? s.en : s.zh}</Button>)}
        <span className="text-xs text-sub ml-2">{en ? 'Plan: docs/…零售POS设计方案.md §三' : '方案：docs/全线点餐POS-零售POS设计方案.md §三'}</span></div>
    </div>
  );
}
