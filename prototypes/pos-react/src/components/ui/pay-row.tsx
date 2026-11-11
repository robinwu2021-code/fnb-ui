// 支付方式 ← 原型 dn-pay--row：品牌色图标 + 名称 + 说明；选中 gray-200；不可用置灰写原因。RadioGroup 的卡片项
import * as RG from '@radix-ui/react-radio-group';
import { cn } from '@/lib/cn';
import type { ReactNode } from 'react';
import { currency } from '@/lib/money';
export function PayGroup({ value, onChange, children, ariaLabel }: { value: string; onChange: (v: string) => void; children: ReactNode; ariaLabel?: string }) {
  return <RG.Root value={value} onValueChange={onChange} aria-label={ariaLabel} className="flex gap-1.5 w-full">{children}</RG.Root>;
}
export function PayItem({ value, color, ch, name, desc, disabled }: { value: string; color: string; ch: string; name: ReactNode; desc: ReactNode; disabled?: boolean }) {
  return (
    <RG.Item value={value} disabled={disabled} className={cn('flex-[1_1_0] min-w-0 flex items-center gap-2.5 min-h-52px py-1.5 px-2.5 rounded-rk text-left font-600 hover:bg-wash', 'data-[state=checked]:(bg-sel-bg text-sel-fg hover:bg-sel-bg [&_small]:text-inherit [&_small]:opacity-75)', 'disabled:(text-placeholder hover:bg-transparent cursor-default [&>i]:opacity-35)')}>
      <i className="w-26px h-26px flex-none rounded-[min(var(--rt),8px)] text-on-solid text-sm not-italic grid place-items-center" style={{ background: color }}>{ch === '¥' ? currency().glyph : ch}</i>
      <span className="flex flex-col leading-[1.25] min-w-0 whitespace-nowrap"><span>{name}</span><small className="text-xs font-400 text-sub ellipsis">{desc}</small></span>
    </RG.Item>
  );
}
