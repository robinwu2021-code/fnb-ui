// Filter ← 原型 sh-filter：筛选标签，可多开；选中 gray-200 块 + 墨字加粗；is-off 置灰写原因
import * as T from '@radix-ui/react-toggle';
import { forwardRef, type ComponentPropsWithoutRef } from 'react';
import { cn } from '@/lib/cn';

export const Filter = forwardRef<HTMLButtonElement, ComponentPropsWithoutRef<typeof T.Root> & { alert?: boolean }>(function Filter({ className, children, ...props }, ref) {
  return (
    <T.Root ref={ref} className={cn('hit inline-flex items-center gap-1 h-ctl-sm px-[calc(var(--px)-4px)] rounded-rb bg-wash text-sub text-sm whitespace-nowrap hover:bg-strong', 'data-[state=on]:(bg-sel-bg text-sel-fg font-600)', 'disabled:(text-placeholder cursor-default hover:bg-wash)', className)} {...props}>
      {children}
    </T.Root>
  );
});
/** 筛选里的计数：要人处理的状态计数用红字 */
export function FilterCount({ n, alert }: { n: number | string; alert?: boolean }) { return <em className={cn('not-italic text-xs num', alert ? 'text-danger-text font-700' : 'opacity-80')}>{n}</em>; }
