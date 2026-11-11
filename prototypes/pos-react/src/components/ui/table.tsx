// Table ← 原型 sh-table：列宽用 cols 传 grid 模板；每行一个 display:contents 容器；不画行线；可选中的行整行可点、当前行 gray-100
import { cn } from '@/lib/cn';
import type { CSSProperties, ReactNode } from 'react';
import { curNode } from '@/lib/money';
export function Table({ cols, head, children, className, zebra }: { cols: string; head: ReactNode[]; children: ReactNode; className?: string; zebra?: boolean }) {
  return (
    <div className={cn('grid items-center gap-x-3 text-sm', className)} style={{ gridTemplateColumns: cols } as CSSProperties}>
      <div className="contents [&>*]:text-sub [&>*]:text-xs [&>*]:pb-2 [&>*]:min-w-0">{head.map((h, i) => <span key={i}>{h}</span>)}</div>
      {children}
    </div>
  );
}
export function Tr({ children, on, onClick, className }: { children: ReactNode; on?: boolean; onClick?: () => void; className?: string }) {
  const cls = cn('contents [&>*]:py-2 [&>*]:min-w-0', onClick && '[&>*]:cursor-pointer', on && '[&>*]:bg-wash-strong [&>*]:shadow-[-6px_0_0_var(--c-wash-strong),6px_0_0_var(--c-wash-strong)] [&>*:first-child]:shadow-[-8px_0_0_var(--c-wash-strong),6px_0_0_var(--c-wash-strong)] [&>*:last-child]:shadow-[-6px_0_0_var(--c-wash-strong),8px_0_0_var(--c-wash-strong)]', className);
  return onClick ? <div role="row" tabIndex={0} aria-selected={on} onClick={onClick} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick(); } }} className={cls}>{children}</div> : <div role="row" className={cls}>{children}</div>;
}
export const Td = ({ children, r, className }: { children?: ReactNode; r?: boolean; className?: string }) => <span className={cn(r && 'text-right num', className)}>{curNode(children)}</span>;
/** 菜品缩略图：44 方块；没图写首字 */
export function Th({ img, text, k }: { img?: string; text: string; k?: string }) {
  return <span className="w-44px h-44px rounded-rt-in overflow-hidden bg-slot-bg grid place-items-center font-700 text-md my-1" style={k ? { color: `var(--cat-${k}-fg)` } : undefined}>{img ? <img src={img} alt="" loading="lazy" className="w-full h-full object-cover" /> : <span>{text}</span>}</span>;
}
/** 双行名：主名 + 次要小字 */
export const Nm2 = ({ a, b }: { a: ReactNode; b?: ReactNode }) => <span className="flex flex-col leading-[1.3]"><b className="font-600 text-base">{a}</b>{b && <small className="text-sub text-xs">{b}</small>}</span>;
