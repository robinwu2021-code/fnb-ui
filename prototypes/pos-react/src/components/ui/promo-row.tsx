// 优惠行 ← 原型 dn-promo（结账左栏）：✓ 已用 · ○ 可选 · ⊘ 置灰写原因；金额靠右；不画线
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import type { ReactNode } from 'react';
export function PromoRow({ on, title, desc, amt, why, onClick, icon }: { on?: boolean; title: ReactNode; desc?: ReactNode; amt?: ReactNode; why?: ReactNode; onClick?: () => void; icon?: string }) {
  const act = onClick && !why; const Comp = act ? 'button' : 'div';
  return (
    <Comp type={act ? 'button' : undefined} onClick={act ? onClick : undefined} className={cn('grid grid-cols-[18px_minmax(0,1fr)_auto] gap-2.5 items-center min-h-ctl py-1.5 px-2.5 rounded-rk w-full text-left [&>svg]:w-18px [&>svg]:h-18px [&>svg]:text-sub', on && '[&>svg]:text-saving', act && !on && 'hover:bg-wash')}>
      <Ico name={icon ?? (on ? 'circle-check' : why ? 'circle-slash' : 'circle')} />
      <span className="flex flex-col min-w-0 leading-[1.3]"><b className={cn('font-600', why && 'text-sub font-500')}>{title}</b><small className="text-xs text-sub ellipsis">{why || desc}</small></span>
      <span className={cn('font-600 num', on ? 'text-ink' : 'text-sub')}>{amt ?? (act ? '›' : '')}</span>
    </Comp>
  );
}
