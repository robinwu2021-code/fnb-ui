// 订单行 ← 原型 oline：色块小方块 · 菜名 · 金额 / 做法或状态 · 份数或步进器；已下单行点开行内操作；批量模式行首换勾
import { useStore } from '@/store';
import { dish, LINE_ST, type Line } from '@/data/sample';
import { money } from '@/lib/money';
import { cn } from '@/lib/cn';
import { useLang, useT } from '@/i18n';
import { CatMark, StatusTag } from '@/components/ui/badge';
import { Stepper } from '@/components/ui/stepper';
import { CheckMark } from '@/components/ui/checkbox';
import { Button } from '@/components/ui/button';
import { CK } from '@/data/sample';
import { LineRow } from '@/components/pos/line-row';

export function OrderLine({ l, i, edit, tappable }: { l: Line; i: number; edit: boolean; tappable?: boolean }) {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const name = lang === 'en' ? dish(l.n).en : l.n;
  const pr = l.st === 'gift' ? <><s className="text-placeholder">{money(l.p * l.q)}</s> ¥0.00</> : money(l.p * l.q);
  const meta = edit ? (l.sub || t('order.each', { p: l.p })) : <><StatusTag ent="item" k={LINE_ST[l.st]} form="dot" />{l.rp && <StatusTag ent="item" k="repriced" form="dot" />}{l.sub && l.st !== 'gift' && <span>{l.sub}</span>}</>;
  const side = edit ? <Stepper q={l.q} onDec={() => dispatch('dec:' + i)} onInc={() => dispatch('inc:' + i)} /> : '×' + l.q;
  const ck = !edit && s.batch && l.st !== 'void', on = ck && s.sel.indexOf(i) > -1;
  const base = 'grid gap-x-2.5 gap-y-0.75 items-center py-2.5 [.ledger_&+&]:border-t-2 [.ledger_&+&]:border-surface';
  if (ck) return (
    <div role="checkbox" aria-checked={on} tabIndex={0} onClick={() => dispatch('pick:' + i)} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); dispatch('pick:' + i); } }}
      className={cn(base, 'grid-cols-[22px_minmax(0,1fr)_auto] -mx-2 px-2 rounded-rk cursor-pointer', on && 'bg-wash-strong')}>
      <span className="row-span-2"><CheckMark on={on} /></span><b className="text-base font-500 ellipsis">{name}</b><span className="font-600 text-right num">{pr}</span>
      <span className="col-start-2 text-xs text-sub flex items-center gap-1.5 min-w-0 whitespace-nowrap overflow-hidden">{meta}</span><span className="col-start-3 justify-self-end text-xs text-sub num">{side}</span>
    </div>
  );
  const tap = !edit && l.st !== 'void' && tappable, open = tap && s.lact === i;
  /* 黑白灰色系下分类小方块不显示，网格也不留它的列（原型在这里把第一列留成 10px，菜名被挤成一个字） */
  const mark = s.tone === 'color' ? <CatMark k={CK[dish(l.n).c] || 'gray'} /> : undefined;
  return (
    <LineRow name={name} amt={pr} meta={meta} side={side} mark={mark} strong={edit} voided={l.st === 'void'} open={open} onToggle={tap ? () => dispatch('lact:' + i) : undefined}
      actions={<>
        <Button variant="muted" size="sm" onClick={() => dispatch('rpline:' + i)}>{t('order.reprice')}</Button><Button variant="muted" size="sm" onClick={() => dispatch('ov:x2')}>{t('order.comp')}</Button>
        <Button variant="muted" size="sm" onClick={() => dispatch('ov:x3')}>{t('order.transfer')}</Button><Button variant="danger" size="sm" onClick={() => dispatch('ov:x2')}>{t('order.void')}</Button>
      </>} />
  );
}
