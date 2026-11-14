// Seg ← 原型 sh-seg：浅灰轨道，每段高 36，当前段 gray-200 块 + 墨字加粗。单选。
import * as TG from '@radix-ui/react-toggle-group';
import { cn } from '@/lib/cn';
import type { ReactNode } from 'react';

export function Seg<T extends string>({ value, onChange, items, className, size = 'sm' }: { value: T; onChange: (v: T) => void; items: { v: T; label: ReactNode; disabled?: boolean }[]; className?: string; size?: 'sm' | 'md' }) {
  return (
    <TG.Root type="single" value={value} onValueChange={(v) => v && onChange(v as T)} className={cn('inline-flex max-w-full overflow-x-auto overflow-y-hidden no-bar bg-wash rounded-rk', className)}>
      {items.map((it) => (
        <TG.Item key={it.v} value={it.v} disabled={it.disabled} className={cn('hit flex-none inline-flex items-center px-[calc(var(--px)-2px)] rounded-rk text-sub text-sm whitespace-nowrap', size === 'sm' ? 'h-ctl-sm' : 'h-ctl', 'data-[state=on]:(bg-sel-bg text-sel-fg font-600)', 'disabled:text-placeholder')}>
          {it.label}
        </TG.Item>
      ))}
    </TG.Root>
  );
}
