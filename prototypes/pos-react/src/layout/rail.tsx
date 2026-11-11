// 资源栏 ← 原型 dn-rail：白面板、项高 44、当前项 gray-200 块 + 墨字加粗；分组小标题；导航在上时横排
import { useStore } from '@/store';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { CatMark } from '@/components/ui/badge';
import { useEffect, useRef, type ReactNode } from 'react';

export type RailItem = { cap?: ReactNode; v?: string; label?: ReactNode; second?: ReactNode; icon?: string; k?: string; dot?: boolean; em?: ReactNode };
export function Rail({ items, cur, onPick, ariaLabel }: { items: RailItem[]; cur: string; onPick: (v: string) => void; ariaLabel?: string }) {
  const top = useStore((s) => s.nav) === 'top';
  const ref = useRef<HTMLElement>(null);
  useEffect(() => { const nav = ref.current, c = nav?.querySelector<HTMLElement>('[aria-current]'); if (nav && c && (c.offsetTop < nav.scrollTop || c.offsetTop + c.offsetHeight > nav.scrollTop + nav.clientHeight)) nav.scrollTop = c.offsetTop - nav.clientHeight / 3; }, [cur]);
  return (
    <nav ref={ref} data-lp="" aria-label={ariaLabel} style={{ gridArea: 'nav' }} className={cn('bg-surface rounded-rc p-1 flex gap-0.5 min-h-0 min-w-0 no-bar', top ? 'flex-row items-center w-full overflow-x-auto' : 'flex-col overflow-y-auto')}>
      {items.map((it, i) => {
        if (it.cap != null) return top ? null : <span key={i} className="text-xs text-placeholder px-2.5 pt-1.5 pb-0.5 [span+&]:mt-2.5">{it.cap}</span>;
        const on = it.v === cur;
        return (
          <button key={it.v} type="button" aria-current={on ? 'true' : undefined} onClick={() => onPick(it.v!)}
            className={cn('flex items-center justify-between gap-1.5 px-2.5 rounded-rk text-sub whitespace-nowrap text-left', top ? 'flex-none min-h-ctl-sm px-[calc(var(--px)-2px)] rounded-rb' : 'min-h-ctl', it.second != null && 'py-1.5', on && 'sel')}>
            <span className="inline-flex items-center gap-1.5 min-w-0">
              {it.icon && <Ico name={it.icon} size={16} />}{it.k && <CatMark k={it.k} />}
              {it.second != null ? <span className="flex flex-col items-start justify-center leading-tight min-w-0"><b className="font-inherit">{it.label ?? it.v}</b><small className={cn('text-xs font-400 ellipsis', on ? 'text-inherit opacity-75' : 'text-sub')}>{it.second}</small></span> : <span>{it.label ?? it.v}</span>}
            </span>
            {it.dot ? <i className="w-6px h-6px rounded-full bg-primary flex-none" /> : it.em != null ? <em className="not-italic text-xs num">{it.em}</em> : null}
          </button>
        );
      })}
    </nav>
  );
}
