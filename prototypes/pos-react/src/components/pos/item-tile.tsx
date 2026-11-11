// 商品磁贴（餐饮菜品卡、零售快捷商品共用）：未选 wash · 已点 gray-100（pick）+ 墨色份数角标 · 沽清 / 缺货 / 限时置灰
import { cn } from '@/lib/cn';
import { Qty } from '@/components/ui/badge';
import type { ReactNode } from 'react';

/** 磁贴底：DishCard 的图文卡也用这一份 */
export const tileClass = (qty: number, off?: boolean) => cn('relative rounded-rt text-left flex flex-col bg-wash min-h-$tile', qty > 0 && 'bg-pick', off && 'opacity-55 [&_b]:text-placeholder [&_.p]:text-placeholder', !off && qty === 0 && 'hover:bg-wash-strong');

/** 纯文字磁贴：标题区 · 底行（价格 + 库存）· 右上份数角标 */
export function ItemTile({ title, foot, qty = 0, off, onClick, ariaLabel, className }: { title: ReactNode; foot: ReactNode; qty?: number; off?: boolean; onClick?: () => void; ariaLabel?: string; className?: string }) {
  return (
    <button type="button" onClick={onClick} aria-label={ariaLabel} className={cn(tileClass(qty, off), 'py-2.5 px-3 gap-0.5', className)}>
      <span className="flex gap-1.5 items-start pr-6 min-w-0">{title}</span>
      {foot}
      {qty > 0 && <Qty n={qty} className="absolute top-2 right-2" />}
    </button>
  );
}
/** 磁贴底行：价格墨色加粗在左，库存 / 状态在右 */
export function TileFoot({ price, stock }: { price: ReactNode; stock?: ReactNode }) {
  return <span className="flex flex-wrap justify-between items-baseline gap-x-1.5 mt-auto pt-1.5"><span className="text-md font-700 whitespace-nowrap num">{price}</span><span className="text-xs text-sub whitespace-nowrap num">{stock}</span></span>;
}
