// Stepper ← 原型 sh-stepper：浅灰块，减 · 数 · 加；订单行里也是 36，不再缩小
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { useT } from '@/i18n';
export function Stepper({ q, onDec, onInc, size = 'sm', className }: { q: number; onDec?: () => void; onInc?: () => void; size?: 'sm' | 'md'; className?: string }) {
  const t = useT(); const d = size === 'sm' ? 'w-ctl-sm h-ctl-sm' : 'w-ctl h-ctl';
  return (
    <span className={cn('inline-flex items-center rounded-rb bg-wash num', size === 'sm' ? 'h-ctl-sm' : 'h-ctl', className)}>
      <button type="button" className={cn('hit grid place-items-center', d)} aria-label={t('common.minus')} onClick={(e) => { e.stopPropagation(); onDec?.(); }}><Ico name="minus" size={16} /></button>
      <b className="min-w-22px text-center">{q}</b>
      <button type="button" className={cn('hit grid place-items-center', d)} aria-label={t('common.plus')} onClick={(e) => { e.stopPropagation(); onInc?.(); }}><Ico name="plus" size={16} /></button>
    </span>
  );
}
