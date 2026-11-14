// Input / Search / Textarea ← 原型 sh-input / sh-search：浅灰块，无描边；搜索框占满剩余宽度
import { forwardRef, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react';
import { Ico } from '@/lib/icon';
import { cn } from '@/lib/cn';
export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { err?: boolean; num?: boolean }>(function Input({ className, err, num, ...props }, ref) {
  return <input ref={ref} className={cn('flex items-center h-ctl px-pad rounded-rk bg-wash text-ink w-full', num && 'text-right num font-600', err && 'bg-danger-tint', className)} {...props} />;
});
export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea({ className, ...props }, ref) {
  return <textarea ref={ref} rows={3} className={cn('block w-full min-h-72px px-pad py-3 rounded-rk bg-wash text-ink resize-none', className)} {...props} />;
});
export function Search({ placeholder, extra, className }: { placeholder: string; extra?: ReactNode; className?: string }) {
  return (
    <label className={cn('flex items-center gap-2 h-ctl-sm px-px rounded-rb bg-wash text-base w-full', className)}>
      <Ico name="search" size={16} className="flex-none text-sub" />
      <input placeholder={placeholder} className="flex-1 min-w-0 bg-transparent" />
      {extra && <span className="ml-auto text-sub text-sm whitespace-nowrap">{extra}</span>}
    </label>
  );
}
