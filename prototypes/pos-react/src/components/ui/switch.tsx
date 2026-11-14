// Switch ← 原型 sh-switch：关闭态 gray-500 轨道（≥3:1），开启主色；是控件不是按钮
import * as S from '@radix-ui/react-switch';
import { forwardRef, type ComponentPropsWithoutRef } from 'react';
import { cn } from '@/lib/cn';
export const Switch = forwardRef<HTMLButtonElement, ComponentPropsWithoutRef<typeof S.Root>>(function Switch({ className, ...props }, ref) {
  return (
    <S.Root ref={ref} className={cn('hit relative flex-none w-44px h-26px rounded-[min(var(--rb),13px)] bg-control-off data-[state=checked]:bg-primary', className)} {...props}>
      <S.Thumb className="block w-22px h-22px rounded-[min(var(--rb),11px)] bg-surface translate-x-2px transition-transform data-[state=checked]:translate-x-20px" />
    </S.Root>
  );
});
