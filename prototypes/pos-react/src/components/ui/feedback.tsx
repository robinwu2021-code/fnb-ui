// 提示与反馈：Empty 空状态 · Note 区内提示 · Banner 横幅 · Skeleton 骨架 · Toaster（Sonner，墨底白字）
import { Toaster as Sonner } from 'sonner';
import { cn } from '@/lib/cn';
import type { ReactNode } from 'react';
export function Empty({ title, desc, fill, icon, className }: { title: ReactNode; desc?: ReactNode; fill?: boolean; icon?: ReactNode; className?: string }) {
  return (
    <div className={cn('flex flex-col items-center gap-1.5 p-4 text-sub text-center w-full', fill && 'flex-1 h-full justify-center', className)}>
      {icon ?? <i className="w-44px h-44px rounded-[min(var(--rb),22px)] bg-faint" />}
      <b className="text-ink">{title}</b>{desc && <span>{desc}</span>}
    </div>
  );
}
export function Note({ children, tone, className }: { children: ReactNode; tone?: 'warn' | 'bad'; className?: string }) {
  return <div className={cn('rounded-rk py-2.5 px-pad text-sm w-full', tone === 'warn' ? 'bg-warning-tint text-warning-text' : tone === 'bad' ? 'bg-danger-tint text-danger-text' : 'bg-faint text-ink', className)}>{children}</div>;
}
export function Banner({ children, bad, action }: { children: ReactNode; bad?: boolean; action?: ReactNode }) {
  return <div className={cn('flex items-center gap-2.5 min-h-40px px-pad rounded-rk text-sm w-full [&>svg]:w-16px [&>svg]:h-16px [&>svg]:flex-none', bad ? 'bg-danger-tint text-danger-text' : 'bg-warning-tint text-warning-text')}>{children}{action && <b className="ml-auto font-600 whitespace-nowrap">{action}</b>}</div>;
}
export const Skeleton = ({ w = 'l', className }: { w?: 's' | 'm' | 'l' | 'pic'; className?: string }) => <span className={cn('skel', w === 's' ? 'w-2/5' : w === 'm' ? 'w-3/5' : w === 'l' ? 'w-9/10' : 'w-full h-auto aspect-4/3 rounded-rt-in2', className)} aria-hidden />;
export function Toaster() {
  return <Sonner position="bottom-center" offset={24} duration={2600} toastOptions={{ unstyled: true, className: 'inline-flex items-center gap-3.5 h-ctl px-[calc(var(--px)+4px)] rounded-rb bg-inverse text-on-inverse text-base whitespace-nowrap w-max font-sans' }} />;
}
