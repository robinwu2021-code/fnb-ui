// Tabs ← 原型 sh-tabs：侧滑里的页签，当前项墨字 + 2px 底线（允许的三种线之一：区分「换页」和「筛选」）
import * as T from '@radix-ui/react-tabs';
import { cn } from '@/lib/cn';
import type { ReactNode } from 'react';
export const Tabs = T.Root;
export const TabsContent = T.Content;
export function TabsList({ className, children }: { className?: string; children: ReactNode }) { return <T.List className={cn('flex gap-5 text-base text-sub', className)}>{children}</T.List>; }
export function TabsTrigger({ value, children }: { value: string; children: ReactNode }) {
  return <T.Trigger value={value} className="hit inline-flex items-center min-h-ctl-sm data-[state=active]:(text-ink font-600 shadow-[inset_0_-2px_0_var(--c-primary)])">{children}</T.Trigger>;
}
