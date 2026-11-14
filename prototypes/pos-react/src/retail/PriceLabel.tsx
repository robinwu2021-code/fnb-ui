// 价签预览（方案 §5.1）：按模板尺寸画一张纸样——商品名 · 规格 · 大号售价 · 会员价 · 单位价 · EAN-13 条码。
// 条码模块由 domain/catalog.ean13Bars 算，这里只按位画矩形。
import { money } from '@/lib/money';
import { ean13Bars, labelUnit, toEan13 } from '@/domain/catalog';
import { item } from './data';
import { useRT } from './dict';
import type { LabelTpl } from './goods';

/** 纸样尺寸（预览按 4px/mm） */
const SIZE: Record<LabelTpl, { w: number; h: number }> = { '60x40': { w: 240, h: 160 }, '40x30': { w: 160, h: 120 }, shelf: { w: 340, h: 96 } };

export function Barcode({ code, width, height }: { code: string; width: number; height: number }) {
  const c13 = toEan13(code), bars = ean13Bars(c13), m = width / (bars.length + 14);
  return (
    <svg width={width} height={height + 12} viewBox={`0 0 ${width} ${height + 12}`} role="img" aria-label={c13}>
      {bars.split('').map((b, i) => (b === '1' ? <rect key={i} x={(i + 7) * m} y={0} width={m + 0.02} height={height} fill="var(--c-ink)" /> : null))}
      <text x={width / 2} y={height + 10} textAnchor="middle" fontSize={9} fill="var(--c-ink)" style={{ fontVariantNumeric: 'tabular-nums', letterSpacing: '.08em' }}>{c13}</text>
    </svg>
  );
}

export function PriceLabel({ id, tpl, en }: { id: string; tpl: LabelTpl; en: boolean }) {
  const t = useRT(); const it = item(id), sz = SIZE[tpl], unit = labelUnit(it.p, it.weigh);
  const name = en ? it.en : it.n, price = it.weigh ? money(it.p) + '/kg' : money(it.p);
  if (tpl === 'shelf') return (
    <div className="bg-surface rounded-[4px] flex items-stretch overflow-hidden" style={{ width: sz.w, height: sz.h }}>
      <div className="flex flex-col justify-center px-3 min-w-0 flex-1"><b className="text-md leading-tight line-clamp-2">{name}</b><small className="text-xs text-sub">{unit ? t('lb.per', { u: unit.per, p: money(unit.price) }) : t('lb.origin')}</small><Barcode code={it.code} width={120} height={18} /></div>
      <div className="flex flex-col justify-center items-end px-3 bg-wash"><b className="text-28px font-800 tracking-[-.03em] num leading-none">{price}</b>{it.mp && it.mp < it.p && <small className="text-xs num mt-1">{t('lb.member')} {money(it.mp)}</small>}</div>
    </div>
  );
  const small = tpl === '40x30';
  return (
    <div className="bg-surface rounded-[4px] flex flex-col p-2.5 gap-0.5 overflow-hidden" style={{ width: sz.w, height: sz.h }}>
      <b className={small ? 'text-sm leading-tight line-clamp-1' : 'text-md leading-tight line-clamp-2'}>{name}</b>
      {!small && <small className="text-xs text-sub">{unit ? t('lb.per', { u: unit.per, p: money(unit.price) }) : t('lb.origin')}</small>}
      <div className="flex items-end justify-between gap-2 mt-auto">
        <Barcode code={it.code} width={small ? 84 : 110} height={small ? 20 : 26} />
        <span className="flex flex-col items-end"><b className={(small ? 'text-xl' : 'text-28px') + ' font-800 tracking-[-.03em] num leading-none'}>{price}</b>{it.mp && it.mp < it.p && !small && <small className="text-xs num mt-0.5">{t('lb.member')} {money(it.mp)}</small>}</span>
      </div>
    </div>
  );
}
