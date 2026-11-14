// StatusTile ← 原型 dn-table / dn-ocard：整块状态底表达状态，不画色条、不描边。
// 常态 --bk（浅底 30%）· 要处理 --bks（60%）· 空闲 / 停用 / 已关闭 浅灰 · 当前 / 选中 = gray-200 块 + 墨字
import { cn } from '@/lib/cn';
import { stv, type StGroup } from '@/lib/states';
import type { ReactNode, CSSProperties } from 'react';
export function StatusTile({ group, alert, quiet, dim, current, onClick, className, children, style, ariaLabel }: { group?: StGroup; alert?: boolean; quiet?: boolean; dim?: boolean; current?: boolean; onClick?: () => void; className?: string; children: ReactNode; style?: CSSProperties; ariaLabel?: string }) {
  const Comp = onClick ? 'button' : 'div';
  return (
    <Comp type={onClick ? 'button' : undefined} onClick={onClick} aria-pressed={onClick ? current : undefined} aria-label={ariaLabel}
      className={cn('relative overflow-hidden rounded-rt text-left flex flex-col', quiet ? 'bg-wash' : alert ? 'bg-st-bks' : 'bg-st-bk', !current && !alert && onClick && 'hover:bg-wash-strong', current && 'bg-sel-bg text-sel-fg [&>b]:text-inherit [&_.a]:text-inherit', dim && 'opacity-40', className)}
      style={{ ...(group ? stv(group) : {}), ...style }}>
      {children}
    </Comp>
  );
}
