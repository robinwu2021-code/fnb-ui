// 结果 ← 原型 dn-result：52 圆底 + 勾 / 横；Spinner ← sh-spin；金额行 ← dn-amount
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import type { ReactNode } from 'react';
export function Result({ bad, title, children }: { bad?: boolean; title: ReactNode; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-1.5 text-center w-full">
      <span className={cn('w-52px h-52px rounded-full grid place-items-center', bad ? 'bg-danger-tint text-danger-text' : 'bg-success-tint text-success-text')}><Ico name={bad ? 'minus' : 'check'} size={26} strokeWidth={3} /></span>
      <span className="text-lg font-600">{title}</span>{children}
    </div>
  );
}
export const Spinner = () => <i className="block w-28px h-28px rounded-full border-3 border-faint border-t-primary animate-spin" aria-hidden />;
export function Amount({ label, value, sm, short, className }: { label: ReactNode; value: ReactNode; sm?: boolean; short?: boolean; className?: string }) {
  return <div className={cn('flex justify-between items-baseline w-full', className)}><span className="help">{label}</span><b className={cn('font-700 tracking-[-.02em] num leading-[1.1]', sm ? 'text-xl' : 'text-2xl', short && 'text-danger-text')}>{value}</b></div>;
}
