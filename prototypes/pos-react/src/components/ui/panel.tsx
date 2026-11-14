// Panel ← 原型 sh-panel：头 .hd（固定）· 主体 .bd（单独滚动）· 底 .ft（固定）。
// ledger 变体 = P2 块色清单 + 白色窄底：清单是唯一的灰色区域，底部白、不加线。
import { cn } from '@/lib/cn';
import type { ReactNode, CSSProperties } from 'react';
import { Button } from './button';

export function Panel({ head, children, foot, className, bodyClass, ledger, area, style }: { head?: ReactNode; children: ReactNode; foot?: ReactNode; className?: string; bodyClass?: string; ledger?: boolean; area?: 'nav' | 'content' | 'detail'; style?: CSSProperties }) {
  return (
    <section className={cn('bg-surface rounded-rc flex flex-col min-h-0 min-w-0 overflow-hidden', className)} style={{ gridArea: area, ...style }}>
      {head != null && <div data-lp="" className="px-pad pt-pad flex flex-col gap-gap flex-none">{head}</div>}
      <div className={cn('flex-1 min-h-0 overflow-y-auto', ledger ? 'ledger bg-wash mx-pad mt-gap rounded-rt py-2.5 px-3 flex flex-col' : 'p-pad', bodyClass)}>{children}</div>
      {foot != null && <div className={cn('flex flex-col gap-gap flex-none', ledger ? 'pt-2.5 px-pad pb-pad gap-2 bg-surface' : 'px-pad pb-pad')}>{foot}</div>}
    </section>
  );
}
/** 区块：面板分段，段间 16；区块标题 12 号 600，右侧放计数或一个文字操作 */
export function Block({ title, right, children, className }: { title?: ReactNode; right?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <div className={cn('[&+&]:mt-4', className)}>
      {title != null && <div className="sec"><span>{title}</span>{right != null && <span className="inline-flex gap-4">{right}</span>}</div>}
      {children}
    </div>
  );
}
/** 白色窄底的金额行：label 为空时不出 */
export function DueFoot({ label, amt, note, short, children }: { label?: ReactNode; amt?: ReactNode; note?: ReactNode; short?: boolean; children?: ReactNode }) {
  return (
    <>
      {label != null && label !== '' && (
        <div className="flex justify-between items-baseline gap-3">
          <span className="flex flex-col text-sm text-sub leading-tight">{label}<small className="text-xs">{note}</small></span>
          <b className={cn('text-28px font-700 tracking-[-.02em] num leading-[1.1]', short && 'text-danger-text')}>{amt}</b>
        </div>
      )}
      {children}
    </>
  );
}
/** 底部一排操作：block 的按钮等分 */
export function Acts({ children, className }: { children: ReactNode; className?: string }) { return <div className={cn('acts flex gap-gap', className)}>{children}</div>; }
/** 面板里的统计行：数字 22/600 + 12 号标签（图标 + 字） */
export function Stat({ items }: { items: { n: ReactNode; l: ReactNode }[] }) {
  return (
    <div className="grid grid-flow-col auto-cols-fr py-3 text-center w-full">
      {items.map((it, i) => (<div key={i}><div className="text-xl font-600 leading-tight tracking-[-.01em] num">{it.n}</div><div className="text-xs text-sub inline-flex items-center gap-1">{it.l}</div></div>))}
    </div>
  );
}
export { Button };
