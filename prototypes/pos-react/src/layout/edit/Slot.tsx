// 停靠槽：平时是普通按钮（点了派发 QUICK 的动作）；编辑态（只在点餐页）变成虚线槽：容量、空位提示、插入线、满槽替换提示、每项右上角 ×。
import type { ReactNode } from 'react';
import { useStore } from '@/store';
import { QUICK } from '@/data/sample';
import { SLOTS, type SlotForm, type SlotId } from '@/data/slots';
import { useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { QuickBar, QuickView } from '@/components/pos/quick';
import { beginPointer, moveQuick, sameSrc, useDrag } from './drag';

/** 一个快捷方式的外观（不带交互），平时、编辑态、拖影共用；外观在 components/pos/quick.tsx（零售共用） */
export function QuickFace({ k, form, className }: { k: string; form: SlotForm; className?: string }) {
  const t = useT(); const q = QUICK[k]; if (!q) return null;
  return <QuickView icon={q[1]} label={t(q[0])} form={form} className={className} />;
}
export const useEditing = () => useStore((s) => s.edit && s.wb === 'order');

export function Slot({ id, className }: { id: SlotId; className?: string }) {
  const t = useT(); const def = SLOTS[id];
  const list = useStore((s) => (id === 'q' ? s.quick : s.slots[id]));
  const dispatch = useStore((s) => s.act);
  const editing = useEditing();
  const src = useDrag((d) => d.src), over = useDrag((d) => d.over), tap = useDrag((d) => d.tap);
  const grow = id === 'q' ? 'flex-[1_1_auto]' : '';

  if (!editing) {
    const items = list.filter((k) => QUICK[k]).map((k) => ({ key: k, label: t(QUICK[k][0]), icon: QUICK[k][1], onClick: () => dispatch(QUICK[k][2]) }));
    return <QuickBar items={items} form={def.form} className={className} />;
  }

  const dragging = src?.kind === 'quick' ? src : null;
  const hot = over?.kind === 'slot' && over.slot === id && dragging ? over : null;
  const others = list.filter((k) => !(dragging && dragging.key === k && dragging.from === id));
  const marker = <i key="__m" aria-hidden className={cn('flex-none rounded-full bg-ink', def.axis === 'x' ? 'w-[3px] self-stretch min-h-6' : 'h-[3px] w-14')} />;
  const nodes: ReactNode[] = []; let n = 0;
  list.forEach((k) => {
    const isSrc = !!dragging && dragging.key === k && dragging.from === id;
    if (!isSrc && hot && n === hot.index) nodes.push(marker);
    if (!isSrc) n++;
    const sel = sameSrc(tap, { kind: 'quick', key: k, from: id });
    nodes.push(
      <div key={k} data-editable="" data-slot-item="" data-qk={k} data-from={id} onPointerDown={(e) => beginPointer(e, { kind: 'quick', key: k, from: id })}
        className={cn('edit-item relative', grow, isSrc && 'opacity-30')}>
        <QuickFace k={k} form={def.form} className={cn(id === 'q' && 'w-full', def.form !== 'fab' && 'bg-wash', sel && 'outline outline-2 outline-ink outline-offset-1')} />
        <button type="button" data-editable="" aria-label={t('edit.remove')} onPointerDown={(e) => e.stopPropagation()} onClick={() => { useDrag.setState({ tap: null }); moveQuick(k, 'lib'); }}
          className="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full bg-inverse text-on-inverse inline-flex items-center justify-center shadow z-1 [&>svg]:w-12px [&>svg]:h-12px"><Ico name="x" size={12} /></button>
      </div>,
    );
  });
  if (hot && hot.index >= others.length) nodes.push(marker);
  const full = !!hot?.full;
  return (
    <div data-editable="" data-slot={id} data-axis={def.axis} data-cap={def.cap}
      className={cn('relative flex items-center gap-1.5 p-1.5 rounded-rk', id !== 's' && 'pt-2.5', ' outline outline-1.5 outline-dashed -outline-offset-1 transition-colors',
        def.axis === 'y' && 'flex-col items-end',
        full ? 'outline-danger-dot bg-danger-tint' : hot ? 'outline-ink bg-wash-strong' : 'ed-line ed-glass', className)}>
      <span className={cn(id === 's' ? 'static self-center text-[10px] leading-tight px-0.5 bg-transparent' : 'absolute -top-2 left-2 px-1', 'rounded-1 text-[10px] leading-4 font-600 whitespace-nowrap pointer-events-none', id === 's' ? (full ? 'text-danger-text' : 'text-sub') : full ? 'bg-danger-strong text-on-solid' : hot ? 'bg-inverse text-on-inverse' : 'bg-surface text-sub')}>
        {t(def.label)} {list.length}/{def.cap}{full ? ' · ' + t('edit.full') : ''}
      </span>
      {list.length === 0 && !hot ? <span className={cn('text-xs text-placeholder whitespace-nowrap px-2 flex items-center justify-center', def.form === 'fab' ? 'w-14 h-14 text-center whitespace-normal' : 'min-h-ctl-sm')}>{t('edit.dropHere')}</span> : nodes}
    </div>
  );
}
