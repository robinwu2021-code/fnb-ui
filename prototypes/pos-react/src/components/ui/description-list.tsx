// Cells ← 原型 sh-cells / sh-cell：左标签右值的分组列表，行高 44，放在白面板里不套底、不画线
import { cn } from '@/lib/cn';
import type { ReactNode } from 'react';
export function Cells({ children, className }: { children: ReactNode; className?: string }) { return <div className={cn('w-full', className)}>{children}</div>; }
export function Cell({ k, v, go, num, strong, className, children }: { k: ReactNode; v?: ReactNode; go?: boolean; num?: boolean; strong?: boolean; className?: string; children?: ReactNode }) {
  return (
    <div className={cn('flex items-center justify-between gap-3 min-h-$row', className)}>
      <span className={strong ? 'text-ink' : 'text-sub'}>{k}</span>
      {children ?? <span className={cn(num && 'num', go && 'after:content-["_›"] after:text-sub')}>{v}</span>}
    </div>
  );
}
