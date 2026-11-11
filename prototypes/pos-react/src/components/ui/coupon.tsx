// 券卡 ← 原型 dn-coupon：面值大字 · 名称 · 类型与有效期或不可用原因；选中 gray-200；不可用 60%。平台标识 PlatMark ← dn-plat
import { cn } from '@/lib/cn';
import { plat } from '@/data/sample';
import type { ReactNode } from 'react';
export function PlatMark({ k }: { k: string }) { const p = plat(k); return <i className="inline-grid place-items-center w-18px h-18px flex-none rounded-4px text-11px not-italic font-700 mr-1.5 align-[-3px]" style={{ background: p.bg, color: p.fg }} aria-hidden>{p.ch}</i>; }
export function CouponCard({ value, title, lines, right, on, off, onClick, className }: { value: ReactNode; title: ReactNode; lines: ReactNode[]; right?: ReactNode; on?: boolean; off?: boolean; onClick?: () => void; className?: string }) {
  const Comp = onClick && !off ? 'button' : 'div';
  return (
    <Comp type={onClick && !off ? 'button' : undefined} onClick={off ? undefined : onClick} aria-pressed={onClick ? on : undefined}
      className={cn('grid grid-cols-[64px_minmax(0,1fr)_auto] gap-2.5 items-center min-h-64px py-2 px-3 rounded-rt bg-wash text-left w-full [&>svg]:w-20px [&>svg]:h-20px [&>svg]:text-ink', on && 'bg-sel-bg text-sel-fg [&_small]:text-inherit [&_.v]:text-inherit [&>svg]:text-inherit', off && 'opacity-60 cursor-default', className)}>
      <span className="v text-xl font-700 text-danger-text text-center num">{value}</span>
      <span className="flex flex-col min-w-0"><b className="font-600">{title}</b>{lines.map((l, i) => <small key={i} className="text-xs text-sub">{l}</small>)}</span>
      {right}
    </Comp>
  );
}
