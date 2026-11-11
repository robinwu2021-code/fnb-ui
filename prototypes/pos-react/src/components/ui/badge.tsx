// 业务状态 StatusTag ← st-tag：图标 + 文字；chip 组加浅底，dot 组不铺底。StatusIcon ← st-ic。Chip ← sh-chip。Badge 红角标。Qty 份数角标。
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { stForm, stv, stateOf } from '@/lib/states';
import { useLang } from '@/i18n';
import type { ReactNode } from 'react';

export function StatusTag({ ent, k, label, form, className }: { ent: string; k: string; label?: ReactNode; form?: 'chip' | 'dot'; className?: string }) {
  const lang = useLang(); const st = stateOf(ent, k);
  if (!st) return <>{label ?? k}</>;
  const f = form ?? stForm(st.group);
  return (
    <span className={cn('inline-flex items-center gap-1 text-xs font-500 whitespace-nowrap align-middle text-st-t [&>svg]:text-st-d [&>svg]:flex-none', f === 'chip' ? 'h-22px px-2 rounded-chip bg-st-b' : 'p-0 h-auto', className)} style={stv(st.group)}>
      <Ico name={st.icon} size={14} />{label ?? (lang === 'en' ? st.en : st.zh)}
    </span>
  );
}
export function StatusIcon({ ent, k, size = 16, className }: { ent: string; k: string; size?: number; className?: string }) {
  const st = stateOf(ent, k); if (!st) return null;
  return <span className={cn('inline-flex text-st-d', className)} style={stv(st.group)}><Ico name={st.icon} size={size} /></span>;
}
export function Chip({ children, className, act, onCard, onClick }: { children: ReactNode; className?: string; act?: boolean; onCard?: boolean; onClick?: () => void }) {
  const cls = cn('inline-flex items-center gap-1 h-22px px-2 rounded-chip text-xs font-500 whitespace-nowrap', act ? 'bg-mark-bg text-mark-text' : onCard ? 'bg-surface text-ink' : 'bg-faint text-sub', onClick && 'hit cursor-pointer', className);
  return onClick ? <button type="button" className={cls} onClick={onClick}>{children}</button> : <span className={cls}>{children}</span>;
}
export const Badge = ({ n, className }: { n: number | string; className?: string }) => <i className={cn('inline-block min-w-18px h-18px px-1.25 rounded-full bg-danger-strong text-on-solid text-xs font-600 leading-18px text-center not-italic num', className)}>{n}</i>;
export const Qty = ({ n, className }: { n: number; className?: string }) => <em className={cn('inline-block min-w-22px h-22px px-1.5 rounded-full bg-primary text-primary-on text-xs font-700 leading-22px text-center not-italic num', className)}>{n}</em>;
/** 分类小方块：黑白灰色系下不显示（base.css .cat-mark） */
export const CatMark = ({ k, className }: { k: string; className?: string }) => <i className={cn('cat-mark inline-block w-10px h-10px flex-none rounded-[min(var(--rb),3px)]', className)} style={{ background: `var(--cat-${k}-solid)` }} />;
