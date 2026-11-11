// 菜品卡 ← 原型 dishCard：有图模式出图文卡（没图的菜把菜名放大写在图位上），无图模式出纯文字卡；图文卡分上下 / 左右。
// 未选 wash · 已点 gray-100 + 墨色份数角标 · 沽清 / 限时置灰
import { useStore } from '@/store';
import { CK, TAGS, dish, type Dish } from '@/data/sample';
import { qtyOf } from '@/lib/pricing';
import { cn } from '@/lib/cn';
import { useLang, useT } from '@/i18n';
import { Ico } from '@/lib/icon';
import { CatMark, Chip, Qty, StatusTag } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/feedback';
import { ItemTile, TileFoot, tileClass } from '@/components/pos/item-tile';

export function DishCard({ d }: { d: Dish }) {
  const t = useT(), lang = useLang();
  const lines = useStore((s) => s.lines), pic = useStore((s) => s.pic), card = useStore((s) => s.card), names = useStore((s) => s.names), dispatch = useStore((s) => s.act);
  const q = qtyOf(lines, d.n), rest = d.s == null ? null : Math.max(0, d.s - q), out = rest === 0, k = CK[d.c] || 'gray', off = out || !!d.timed;
  const first = lang === 'en' ? d.en : d.n, second = lang === 'en' ? d.n : d.en;
  const tags = (d.tg || []).map((x) => <span key={x} className="inline-flex flex-none text-sub" title={x} aria-label={x}><Ico name={TAGS[x]} size={13} /></span>);
  const nameEl = (
    <span className="flex flex-col min-w-0 gap-px">
      <b className={cn('text-md font-600 leading-[1.3] line-clamp-2', q > 0 && 'font-700', pic === 'off' && 'text-lg font-700')}>{first}</b>
      {(names === 'bi' || d.tg) && <span className="text-xs text-sub leading-[1.3] flex items-center gap-0.75 whitespace-nowrap">{tags}{names === 'bi' && <span className="min-w-0 ellipsis">{second}</span>}</span>}
    </span>
  );
  const stock = d.timed ? <StatusTag ent="stock" k="timed" label={d.timed} form="dot" /> : out ? <StatusTag ent="stock" k="sold_out" form="dot" /> : rest == null ? t('order.stock.ok') : rest <= 5 ? <StatusTag ent="stock" k="low" label={t('order.stock.low', { n: rest })} form="dot" /> : t('order.stock.n', { n: rest });
  const foot = <TileFoot price={d.spec ? t('order.from', { p: d.p }) : '¥' + d.p} stock={stock} />;
  const base = tileClass(q, off);
  const onClick = () => dispatch('add:' + d.n);
  if (pic === 'off') return <ItemTile qty={q} off={off} onClick={onClick} ariaLabel={first} title={<><CatMark k={k} className="mt-1.25" />{nameEl}</>} foot={foot} />;
  const h = card === 'h';
  const slot = d.img ? <img src={d.img} alt={first} loading="lazy" className="absolute inset-0 w-full h-full object-cover" />
    : h ? <span className="flex flex-col justify-center items-center text-center h-full min-w-0"><b className="text-xl font-700 leading-tight">{lang === 'en' ? d.en.split(' ').map((w) => w[0]).join('').slice(0, 3) : d.n.slice(0, 2)}</b></span>
    : <span className="flex flex-col justify-end gap-0.5 h-full min-w-0"><b className="text-xl font-700 leading-tight tracking-[-.01em] line-clamp-2">{first}</b>{names === 'bi' && <small className="text-sm opacity-75 ellipsis">{second}</small>}</span>;
  const picEl = (
    <span className={cn('relative grid place-items-center overflow-hidden', h ? 'aspect-square rounded-rt-in2' : 'aspect-4/3 rounded-rt-in', d.img ? 'bg-wash-strong' : cn('bg-slot-bg place-items-stretch text-left', h ? 'p-2' : 'p-3'), off && 'grayscale opacity-50', !d.img && off && 'text-placeholder')} style={!d.img ? { color: 'var(--cat-' + k + '-fg)' } : undefined}>
      {slot}{out && <Chip onCard className="absolute left-1.5 bottom-1.5 z-1">{t('order.soldOut')}</Chip>}{q > 0 && <Qty n={q} className={cn('absolute', h ? 'top-1 right-1' : 'top-2 right-2')} />}
    </span>
  );
  const info = d.img || h ? <><span className="flex gap-1.5 items-start min-w-0"><CatMark k={k} className="mt-1.25" />{nameEl}</span>{foot}</> : foot;
  return (
    <button type="button" onClick={onClick} aria-label={first} className={cn(base, h ? 'grid grid-cols-[72px_minmax(0,1fr)] gap-2.5 p-2 items-stretch' : 'p-0 overflow-hidden gap-0')}>
      {picEl}<span className={cn('flex flex-col', h ? 'py-0.5 pr-0.5' : 'flex-1 px-3 pt-2 pb-2.5')}>{info}</span>
    </button>
  );
}
export function DishSkeleton() {
  const pic = useStore((s) => s.pic), card = useStore((s) => s.card);
  return <span className="flex flex-col gap-2 min-h-$tile" aria-hidden>{pic === 'img' && card !== 'h' && <Skeleton w="pic" />}<Skeleton w="l" /><Skeleton w="s" /></span>;
}
export { dish };
