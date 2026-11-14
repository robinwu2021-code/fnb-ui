// Option ← 原型 sh-option / dn-pay / dn-plan / dn-coupon / dn-layout：RadioGroup 的卡片项。
// 共用一条规则：未选 wash · 当前 gray-200 + 墨字加粗 · 禁用置灰写原因。
import * as RG from '@radix-ui/react-radio-group';
import { cn } from '@/lib/cn';
import type { ReactNode } from 'react';
import { curNode } from '@/lib/money';

export function OptionGroup<T extends string>({ value, onChange, className, children, ariaLabel }: { value: T; onChange: (v: T) => void; className?: string; children: ReactNode; ariaLabel?: string }) {
  return <RG.Root value={value} onValueChange={(v) => onChange(v as T)} aria-label={ariaLabel} className={cn('flex flex-wrap gap-gap items-center', className)}>{children}</RG.Root>;
}
export function Option({ value, label, sub, disabled, className }: { value: string; label: ReactNode; sub?: ReactNode; disabled?: boolean; className?: string }) {
  return (
    <RG.Item value={value} disabled={disabled} className={cn('inline-flex flex-col justify-center items-start min-h-ctl py-1 px-[calc(var(--px)-2px)] rounded-rk bg-wash text-base text-left hover:bg-strong', 'data-[state=checked]:(bg-sel-bg text-sel-fg font-600 [&_small]:text-inherit [&_small]:font-400)', 'disabled:(text-placeholder [&_small]:text-placeholder hover:bg-wash cursor-default)', className)}>
      <span>{curNode(label)}</span>
      {sub != null && <small className="text-xs text-sub">{curNode(sub)}</small>}
    </RG.Item>
  );
}
/** 多选的选项卡（加料）：外观同 Option，用 aria-pressed */
export function OptionMulti({ on, onToggle, label, sub, disabled, className }: { on: boolean; onToggle: () => void; label: ReactNode; sub?: ReactNode; disabled?: boolean; className?: string }) {
  return (
    <button type="button" aria-pressed={on} disabled={disabled} onClick={onToggle} className={cn('inline-flex flex-col justify-center items-start min-h-ctl py-1 px-[calc(var(--px)-2px)] rounded-rk bg-wash text-base text-left hover:bg-strong', on && 'bg-sel-bg text-sel-fg font-600 [&_small]:text-inherit [&_small]:font-400', 'disabled:(text-placeholder hover:bg-wash cursor-default)', className)}>
      <span>{curNode(label)}</span>
      {sub != null && <small className="text-xs text-sub">{curNode(sub)}</small>}
    </button>
  );
}
