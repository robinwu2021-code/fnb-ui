// 数字键 ← 原型 dn-keys：数字键块色 · 功能键深一档 · 确认键主色实心（跨两行）；退格用图标
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import type { ReactNode } from 'react';
export type KeyDef = { k: string; label?: ReactNode; fn?: boolean; ok?: boolean; off?: boolean; aria?: string };
export function Keypad({ keys, onKey, cols = 3, className }: { keys: KeyDef[]; onKey: (k: string) => void; cols?: 3 | 4; className?: string }) {
  return (
    <div className={cn('grid gap-gap w-full content-start', cols === 4 ? 'grid-cols-4' : 'grid-cols-3', className)}>
      {keys.map((d) => (
        <button key={d.k} type="button" aria-label={d.aria} disabled={d.off} onClick={() => onKey(d.k)}
          className={cn('h-ctl-lg rounded-rk grid place-items-center text-xl font-500 num bg-wash hover:bg-wash-strong [&>svg]:w-22px [&>svg]:h-22px', d.fn && 'text-md bg-wash-strong text-ink', d.ok && 'row-span-2 h-auto bg-primary text-primary-on text-md font-600 hover:bg-primary-hover', d.ok && d.off && 'bg-faint text-placeholder')}>
          {d.label ?? (d.k === 'back' ? <Ico name="delete" /> : d.k)}
        </button>
      ))}
    </div>
  );
}
/** 授权码圆点 */
export function Pins({ n, total = 4, err }: { n: number; total?: number; err?: boolean }) {
  return <div className="flex gap-3.5 justify-center w-full py-1">{Array.from({ length: total }, (_, i) => <i key={i} className={cn('w-14px h-14px rounded-full', i < n ? (err ? 'bg-danger-strong' : 'bg-ink') : 'bg-faint')} />)}</div>;
}
