// 快捷操作（餐饮点餐页停靠槽、零售收银共用）：四种外观 chip / icon / entry / fab；QuickBar 是一排可点的快捷操作。
// 餐饮的「可拖动编辑态」在 layout/edit/Slot.tsx，平时态直接用这里的 QuickBar。
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Badge } from '@/components/ui/badge';
import type { ReactNode } from 'react';

export type QuickForm = 'chip' | 'icon' | 'entry' | 'fab';
export const QUICK_FACE: Record<QuickForm, string> = {
  chip: 'inline-flex items-center justify-center gap-1 min-h-ctl-sm px-1.5 rounded-rk text-sm text-ink whitespace-nowrap [&>svg]:w-16px [&>svg]:h-16px',
  icon: 'inline-flex items-center justify-center w-ctl-sm h-ctl-sm rounded-rb bg-wash text-ink [&>svg]:w-18px [&>svg]:h-18px',
  entry: 'inline-flex items-center justify-center w-ctl-sm h-ctl-sm rounded-rb text-ink [&>svg]:w-18px [&>svg]:h-18px',
  fab: 'inline-flex flex-col items-center justify-center gap-0.5 w-14 h-14 rounded-full bg-inverse text-on-inverse text-[11px] leading-none font-500 shadow-[0_4px_14px_rgba(0,0,0,.22)] [&>svg]:w-20px [&>svg]:h-20px',
};
/** 一个快捷操作的外观（不带交互） */
export function QuickView({ icon, label, form, className }: { icon: string; label: ReactNode; form: QuickForm; className?: string }) {
  return <span className={cn(QUICK_FACE[form], className)}><Ico name={icon} />{(form === 'chip' || form === 'fab') && <span>{label}</span>}</span>;
}
export interface QuickItem { key: string; label: string; icon: string; onClick: () => void; on?: boolean; disabled?: boolean; badge?: number }
/** 一排快捷操作：chip 等分撑满（结单区 / 购物车头）；icon 方块（工具条尾）；entry 系统栏；fab 竖排悬浮 */
export function QuickBar({ items, form = 'chip', className }: { items: QuickItem[]; form?: QuickForm; className?: string }) {
  if (!items.length) return null;
  return (
    <div className={cn('flex gap-1', form === 'fab' && 'flex-col gap-2', className)}>
      {items.map((q) => (
        <button key={q.key} type="button" data-lp="" title={q.label} aria-label={q.label} aria-pressed={q.on} disabled={q.disabled} onClick={q.onClick}
          className={cn('hit relative disabled:opacity-40', form === 'chip' && 'flex-[1_1_auto] hover:bg-wash rounded-rk', form === 'entry' && 'hover:bg-wash rounded-rb', form === 'icon' && 'rounded-rb')}>
          <QuickView icon={q.icon} label={q.label} form={form} className={cn(form === 'chip' && 'w-full', form === 'icon' && 'hover:bg-wash-strong', q.on && 'bg-sel-bg text-sel-fg font-600 hover:bg-sel-bg')} />
          {q.badge ? <Badge n={q.badge} className={cn('absolute', form === 'chip' ? 'top-0.5 right-1' : '-top-1 -right-1')} /> : null}
        </button>
      ))}
    </div>
  );
}
