// Checkbox ← 原型 sh-check：未选 gray-150 实心块（不描边），选中主色 + 白勾
import * as C from '@radix-ui/react-checkbox';
import { forwardRef, type ComponentPropsWithoutRef } from 'react';
import { Ico } from '@/lib/icon';
import { cn } from '@/lib/cn';
export const Checkbox = forwardRef<HTMLButtonElement, ComponentPropsWithoutRef<typeof C.Root>>(function Checkbox({ className, ...props }, ref) {
  return (
    <C.Root ref={ref} className={cn('inline-grid place-items-center flex-none w-22px h-22px rounded-chip bg-check-off data-[state=checked]:bg-primary data-[state=indeterminate]:bg-primary', className)} {...props}>
      <C.Indicator className="text-primary-on"><Ico name={props.checked === 'indeterminate' ? 'minus' : 'check'} size={14} strokeWidth={2.5} /></C.Indicator>
    </C.Root>
  );
});
/** 只做样子的勾（放在整行可点的列表行里，行本身是 role=checkbox） */
export function CheckMark({ on }: { on: boolean }) {
  return <i className={cn('inline-grid place-items-center flex-none w-22px h-22px rounded-chip', on ? 'bg-primary text-primary-on' : 'bg-check-off')}>{on && <Ico name="check" size={14} strokeWidth={2.5} />}</i>;
}
