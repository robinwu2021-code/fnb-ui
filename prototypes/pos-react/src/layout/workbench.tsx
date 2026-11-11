// 工作台：按 MODULES 声明的布局 + 页首「布局」L1–L6 算网格；各模块渲染自己的 nav / content / detail（各自带 gridArea）
import { useStore } from '@/store';
import { layoutOf } from './modules';
import { SysBar } from './sysbar';
import { Banner } from '@/components/ui/feedback';
import { StatusIcon } from '@/components/ui/badge';
import { useT } from '@/i18n';
import { TablesWB } from '@/modules/tables/TablesWB';
import { OrderWB } from '@/modules/order/OrderWB';
import { CheckoutWB } from '@/modules/checkout/CheckoutWB';
import { OrdersWB } from '@/modules/orders/OrdersWB';
import { MenuWB } from '@/modules/menu/MenuWB';
import { MembersWB } from '@/modules/members/MembersWB';
import { ReportsWB } from '@/modules/reports/ReportsWB';
import { ShiftWB } from '@/modules/shift/ShiftWB';
import { SettingsWB } from '@/modules/settings/SettingsWB';
import { StubWB } from '@/modules/stubs/StubWB';
import { Overlays } from '@/overlays';
import { Toaster } from '@/components/ui/feedback';
import type { ComponentType } from 'react';
import { AreaFrames, DropZones, Ghost, LIB_H, Library, LongPressRing, TapMenu, useLongPressEntry } from './edit/EditChrome';
import { cn } from '@/lib/cn';
import { useBiz } from '@/biz/store';
import { RetailWorkbench } from '@/retail/RetailWorkbench';

const RENDER: Record<string, ComponentType> = { tables: TablesWB, order: OrderWB, checkout: CheckoutWB, orders: OrdersWB, menu: MenuWB, members: MembersWB, reports: ReportsWB, shift: ShiftWB, settings: SettingsWB };
/** 按业态分流：零售用 retail/RetailWorkbench（同一套壳和网格），餐饮走下面原来的工作台 */
export function Workbench() { const biz = useBiz((s) => s.biz); return biz === 'retail' ? <RetailWorkbench /> : <FnbWorkbench />; }
function FnbWorkbench() {
  const t = useT(); const wb = useStore((s) => s.wb), nav = useStore((s) => s.nav), detail = useStore((s) => s.detail), banner = useStore((s) => s.banner), dispatch = useStore((s) => s.act);
  const edit = useStore((s) => s.edit);
  useLongPressEntry();
  const [areas, cols, rows] = layoutOf(wb, nav, detail);
  const Mod = RENDER[wb] ?? StubWB;
  return (
    <>
      <SysBar />
      <div className="px-gap pt-gap">{banner && <Banner action={<button type="button" className="hit" onClick={() => dispatch('sheet:net')}>{t('banner.view')}</button>}><StatusIcon ent="device" k="offline" />{t('banner.offline')}</Banner>}</div>
      <div id="wb-grid" className={cn('relative grid gap-gap p-gap min-h-0 [&>*]:min-h-0 [&>*]:min-w-0 transition-[padding] duration-200', edit && 'wb-edit')}
        style={{ gridTemplateAreas: areas, gridTemplateColumns: cols, gridTemplateRows: rows, paddingBottom: edit && wb === 'order' ? LIB_H + 8 : undefined }}>
        <Mod />
        <AreaFrames />
        <DropZones />
      </div>
      <Library />
      <TapMenu />
      <Ghost />
      <LongPressRing />
      <Overlays />
      <Toaster />
    </>
  );
}
