// 系统栏骨架（餐饮、零售共用）：左 店名 + 当班人 · 中 工作台页签（≤ 6）· 右 图标入口 + 时间。页签和入口由业态传入。
import { cn } from '@/lib/cn';
import type { ReactNode } from 'react';

export function SysBarShell({ brand, sub, tabs, cur, onTab, children, time }: { brand: ReactNode; sub: ReactNode; tabs: { v: string; label: ReactNode }[]; cur: string; onTab: (v: string) => void; children?: ReactNode; time: ReactNode }) {
  return (
    <header data-lp="" className="flex items-center gap-1.5 h-56px px-pad bg-surface overflow-x-auto no-bar [&>*]:flex-none">
      <span className="font-600 text-base leading-tight mr-2 whitespace-nowrap">{brand}<small className="block font-400 text-xs text-sub">{sub}</small></span>
      <nav className="flex gap-0.5 mr-auto" aria-label="workbench">
        {tabs.map((k) => (
          <button key={k.v} type="button" aria-current={cur === k.v ? 'page' : undefined} onClick={() => onTab(k.v)}
            className={cn('hit h-ctl-sm px-[calc(var(--px)-2px)] inline-flex items-center rounded-rb text-sub whitespace-nowrap font-500', cur === k.v && 'sel')}>{k.label}</button>
        ))}
      </nav>
      {children}
      <span className="text-sub num ml-1 font-600">{time}</span>
    </header>
  );
}
/** 系统栏图标入口：36 × 36，热区 44；名称写在 aria-label 和 title */
export function SysEntry({ label, onClick, children }: { label: string; onClick?: () => void; children: ReactNode }) {
  return <button type="button" className="hit relative w-ctl-sm h-ctl-sm inline-flex items-center justify-center rounded-rb text-ink [&>svg]:w-18px [&>svg]:h-18px [&_svg]:w-18px [&_svg]:h-18px" title={label} aria-label={label} onClick={onClick}>{children}</button>;
}
