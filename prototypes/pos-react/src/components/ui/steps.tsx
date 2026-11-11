// Steps ← 原型 dn-steps：已过的步打勾、次要字；当前步 gray-200 块 + 墨字加粗；未到的步次要字；步间 8px 短线
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import type { ReactNode } from 'react';
export function Steps({ items, current }: { items: ReactNode[]; current: number }) {
  return (
    <ol className="flex gap-1 m-0 mb-1.5 p-0 list-none">
      {items.map((s, i) => { const n = i + 1, cur = n === current, done = n < current; return (
        <li key={i} aria-current={cur ? 'step' : undefined} className={cn('inline-flex items-center gap-1.5 h-ctl-sm px-2.5 rounded-rb text-sub text-sm whitespace-nowrap', cur && 'sel', '[li+&]:before:(content-[""] w-8px h-1px bg-wash-strong mr--2 ml--8px)')}>
          <i className={cn('inline-grid place-items-center w-18px h-18px rounded-full bg-wash-strong not-italic text-xs font-600 text-ink', cur && 'bg-ink text-surface')}>{done ? <Ico name="check" size={12} /> : n}</i><span>{s}</span>
        </li>); })}
    </ol>
  );
}
