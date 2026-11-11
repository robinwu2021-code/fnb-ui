// 零售收银 · 页首原型控制：选关键画面 + 模拟扫码（业态 = 零售且在收银页签时出现在 DevBar 里）。不属于产品界面。
import { useStore } from '@/store';
import { Seg } from '@/components/ui/toggle-group';
import { Button } from '@/components/ui/button';
import { goScreen, scanSim, useR, type Screen } from './RetailPos';

const SCREENS: { v: Screen; zh: string; en: string }[] = [
  { v: 'empty', zh: '① 空车待扫码', en: '① Empty cart' }, { v: 'cart', zh: '② 扫了 14 件', en: '② 14 items scanned' }, { v: 'unknown', zh: '③ 未知条码', en: '③ Unknown barcode' },
  { v: 'peek', zh: '④ 查价', en: '④ Price check' }, { v: 'weigh', zh: '⑤ 称重', en: '⑤ Weighing' }, { v: 'sku', zh: '⑥ 选规格', en: '⑥ Variant' }, { v: 'held', zh: '⑦ 取单', en: '⑦ Recall' },
  { v: 'clear', zh: '⑧ 清空确认', en: '⑧ Clear cart' }, { v: 'pay', zh: '⑨ 收款 · 现金', en: '⑨ Pay · cash' }, { v: 'done', zh: '⑩ 收款完成', en: '⑩ Paid' },
];
const SCANS: { k: Parameters<typeof scanSim>[0]; zh: string; en: string }[] = [
  { k: 'std', zh: '标准码', en: 'Barcode' }, { k: 'weighCode', zh: '称重码', en: 'Scale label' }, { k: 'loose', zh: '散称', en: 'Loose' },
  { k: 'sku', zh: '多规格', en: 'Variant' }, { k: 'unknown', zh: '未知码', en: 'Unknown' }, { k: 'payCode', zh: '付款码', en: 'Pay code' },
];
export function RetailBar() {
  const en = useStore((s) => s.lang) === 'en'; const screen = useR((s) => s.screen);
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2"><span className="text-sm text-sub w-72px flex-none">{en ? 'Screen' : '画面'}</span>
        <Seg value={screen} onChange={(v) => goScreen(v)} items={SCREENS.map((s) => ({ v: s.v, label: en ? s.en : s.zh }))} /></div>
      <div className="flex flex-wrap items-center gap-2"><span className="text-sm text-sub w-72px flex-none">{en ? 'Sim scan' : '模拟扫码'}</span>
        {SCANS.map((s) => <Button key={s.k} variant="muted" size="sm" onClick={() => scanSim(s.k)}>{en ? s.en : s.zh}</Button>)}
        <span className="text-xs text-sub ml-2">{en ? 'Plan: docs/…零售POS设计方案.md §三' : '方案：docs/全线点餐POS-零售POS设计方案.md §三'}</span></div>
    </div>
  );
}
