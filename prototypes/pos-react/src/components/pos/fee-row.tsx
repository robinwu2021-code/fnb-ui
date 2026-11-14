// 费用与优惠 · 紧凑行（餐饮结单区 AdjRows、零售购物车共用）：图标 · 名称 · 金额；加价墨色、减价优惠色、不可用置灰
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import type { ReactNode } from 'react';

export function FeeRow({ icon, name, amt, neg, off, onClick }: { icon: string; name: ReactNode; amt?: ReactNode; neg?: boolean; off?: boolean; onClick?: () => void }) {
  const cls = cn('grid grid-cols-[18px_minmax(0,1fr)_auto] gap-2 items-center min-h-ctl-sm text-sm text-sub w-full text-left [&>svg]:w-16px [&>svg]:h-16px', off && 'text-placeholder');
  const inner = <><Ico name={icon} /><span className="ellipsis">{name}</span><span className={cn('font-500 num', neg ? 'text-saving' : 'text-ink')}>{amt}</span></>;
  return onClick ? <button type="button" className={cls} onClick={onClick}>{inner}</button> : <div className={cls}>{inner}</div>;
}
