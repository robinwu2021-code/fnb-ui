// 零售工作台壳：页签、布局来自 biz/index.ts 的 BIZ.retail；收银是零售自己的模块，其余页签复用餐饮模块（方案 §2.3）。
// 系统栏骨架、网格算法（layoutOf）、浮层、Toast 都和餐饮同一套。
import type { ComponentType } from 'react';
import { useStore } from '@/store';
import { useT } from '@/i18n';
import { layoutOf } from '@/layout/modules';
import { BIZ, goModule, useBiz, useCurTab } from '@/biz';
import { SysBarShell, SysEntry } from '@/components/pos/sysbar';
import { StatusIcon } from '@/components/ui/badge';
import { Note, Toaster } from '@/components/ui/feedback';
import { Ico } from '@/lib/icon';
import { Overlays } from '@/overlays';
import { RetailOrdersWB } from './RetailOrdersWB';
import { ReturnDialog } from './ReturnDialog';
import { ReportsWB } from '@/modules/reports/ReportsWB';
import { SettingsWB } from '@/modules/settings/SettingsWB';
import { MembersWB } from '@/modules/members/MembersWB';
import { ShiftWB } from '@/modules/shift/ShiftWB';
import { RetailPosWB } from './RetailPos';
import { StockWB } from './StockWB';
import { GoodsWB } from './GoodsWB';
import { useRT } from './dict';

/** 零售页签 → 模块。pos、orders、goods、stock 是零售自己的；其余复用餐饮模块（第 4–6 期再按零售字段改造） */
const RENDER: Record<string, ComponentType> = { pos: RetailPosWB, orders: RetailOrdersWB, goods: GoodsWB, reports: ReportsWB, settings: SettingsWB, members: MembersWB, shift: ShiftWB, stock: StockWB };
/** 不出「复用餐饮模块」提示的页签：零售自己的，和机制共用、内容已按业态切换的设置 */
const OWN = ['pos', 'orders', 'goods', 'stock', 'settings'];

function RetailSysBar({ on }: { on: string }) {
  const t = useT(), rt = useRT();
  return (
    <SysBarShell brand={t('store.name')} sub={t('store.shift')} cur={on} onTab={goModule} time="10:26"
      tabs={BIZ.retail.tabs.filter((x) => !x.home).map((x) => ({ v: x.v, label: t(x.label) }))}>
      <SysEntry label={rt('entry.scanner')}><Ico name="scan-barcode" size={18} /></SysEntry>
      <SysEntry label={rt('entry.scale')}><Ico name="scale" size={18} /></SysEntry>
      <SysEntry label={rt('entry.print')}><Ico name="printer" size={18} /></SysEntry>
      <SysEntry label={rt('entry.net')}><StatusIcon ent="device" k="online" size={18} /></SysEntry>
      <SysEntry label={rt('entry.member')} onClick={() => goModule('members')}><Ico name="id-card" size={18} /></SysEntry>
      <SysEntry label={rt('entry.display')} onClick={() => { useStore.getState().set({ sg: 'display', si: 'shape' }); goModule('settings'); }}><Ico name="sliders-horizontal" size={18} /></SysEntry>
    </SysBarShell>
  );
}
export function RetailWorkbench() {
  const t = useT(); const nav = useStore((s) => s.nav), detail = useStore((s) => s.detail);
  const { tab, on } = useCurTab(); useBiz((s) => s.rwb);
  const [areas, cols, rows] = layoutOf(tab.layout, nav, detail);
  const Mod = RENDER[tab.v];
  return (
    <>
      <RetailSysBar on={on} />
      <div className="px-gap pt-gap">{!OWN.includes(tab.v) && <Note className="flex items-center gap-2"><Ico name="recycle" size={16} />{t('biz.reuse')}</Note>}</div>
      <div className="relative grid gap-gap p-gap min-h-0 [&>*]:min-h-0 [&>*]:min-w-0" style={{ gridTemplateAreas: areas, gridTemplateColumns: cols, gridTemplateRows: rows }}>
        <Mod />
      </div>
      <ReturnDialog />
      <Overlays />
      <Toaster />
    </>
  );
}
