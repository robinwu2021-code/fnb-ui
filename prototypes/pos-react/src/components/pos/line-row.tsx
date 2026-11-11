// 清单行（餐饮订单行 / 零售购物车行共用）：名称 · 金额 / 说明 · 份数或步进器；可点开行内操作。
// 不用 <button> 包整行（行里还有步进器等按钮），点击区是 role=button 的 div。
import { cn } from '@/lib/cn';
import type { ReactNode } from 'react';

export function LineRow({ name, amt, meta, side, mark, strong, voided, open, highlight, onToggle, actions, className }: {
  name: ReactNode; amt: ReactNode; meta?: ReactNode; side?: ReactNode;
  /** 行首小方块（餐饮多色系下的分类色），没有就不留列 */ mark?: ReactNode;
  /** 名称加粗（可编辑的待下单 / 购物车行） */ strong?: boolean;
  voided?: boolean; open?: boolean; /** 刚加入的一行，白底提示 */ highlight?: boolean;
  onToggle?: () => void; actions?: ReactNode; className?: string;
}) {
  const tap = !!onToggle;
  return (
    <div role={tap ? 'button' : undefined} tabIndex={tap ? 0 : undefined} aria-expanded={tap ? !!open : undefined} onClick={onToggle}
      onKeyDown={tap ? (e) => { if (e.target === e.currentTarget && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); onToggle!(); } } : undefined}
      className={cn('grid gap-x-2.5 gap-y-0.75 items-center py-2.5 text-left [.ledger_&+&]:border-t-2 [.ledger_&+&]:border-surface', mark ? 'grid-cols-[10px_minmax(0,1fr)_auto]' : 'grid-cols-[minmax(0,1fr)_auto]',
        tap && 'cursor-pointer', (open || highlight) && 'bg-surface -mx-2 px-2 rounded-rk', voided && '[&>b]:line-through [&>b]:text-sub [&>b]:font-400 [&>.pr]:line-through [&>.pr]:text-sub [&>.pr]:font-400', className)}>
      {mark}<b className={cn('text-base ellipsis', strong ? 'font-600' : 'font-500')}>{name}</b><span className="pr font-600 text-right num">{amt}</span>
      <span className={cn('text-xs text-sub flex items-center gap-1.5 min-w-0 whitespace-nowrap overflow-hidden', mark ? 'col-start-2' : 'col-start-1')}>{meta}</span>
      <span className={cn('justify-self-end text-xs text-sub num', mark ? 'col-start-3' : 'col-start-2')} onClick={(e) => e.stopPropagation()}>{side}</span>
      {open && actions && <span className="col-span-full flex gap-1.5 mt-1.5 [&>button]:flex-1" onClick={(e) => e.stopPropagation()}>{actions}</span>}
    </div>
  );
}
