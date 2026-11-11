// 报表：指标块 Kpi · 竖条 VBars · 横条 HBars。单一系列只用主色；不画网格和灰底轨道；条间 2px，数据端 4px 圆角
import { cn } from '@/lib/cn';
import type { ReactNode } from 'react';
export function Kpi({ label, value, note, st }: { label: ReactNode; value: ReactNode; note?: ReactNode; st?: 'up' | 'down' }) {
  return <div className="flex flex-col gap-0.5 py-2"><span className="text-xs text-sub">{label}</span><b className="text-xl font-700 tracking-[-.01em] num">{value}</b>{note && <span className={cn('text-xs text-sub', st === 'up' && 'text-success-text', st === 'down' && 'text-danger-text')}>{note}</span>}</div>;
}
export const Kpis = ({ children, sm }: { children: ReactNode; sm?: boolean }) => <div className={cn('grid gap-x-4 gap-y-1', sm ? 'grid-cols-2 m-0' : 'grid-cols-[repeat(auto-fill,minmax(140px,1fr))] mb-4')}>{children}</div>;
export function VBars({ data, fmt, label }: { data: [string, number][]; fmt: (v: number) => string; label: string }) {
  const max = Math.max(...data.map((d) => d[1]));
  return <div className="flex items-stretch gap-0.5 h-168px pt-2" role="img" aria-label={label}>{data.map((d) => <span key={d[0]} className="flex-1 flex flex-col justify-end items-center gap-1.5 min-w-0" title={d[0] + ' · ' + fmt(d[1])}><i className="block w-[min(70%,28px)] bg-primary rounded-t-4px hover:opacity-75" style={{ height: Math.max(2, Math.round((d[1] / max) * 100)) + '%' }} /><small className="text-xs text-sub num">{d[0]}</small></span>)}</div>;
}
export function HBars({ data, fmt }: { data: [string, number][]; fmt: (v: number) => string }) {
  const max = Math.max(...data.map((d) => d[1]));
  return <div className="flex flex-col gap-0.5">{data.map((d) => <div key={d[0]} className="grid grid-cols-[minmax(72px,30%)_minmax(0,1fr)_80px] gap-3 items-center min-h-28px" title={d[0] + ' · ' + fmt(d[1])}><span className="text-sm ellipsis">{d[0]}</span><span><i className="block h-12px bg-primary rounded-r-4px hover:opacity-75" style={{ width: Math.max(1, Math.round((d[1] / max) * 100)) + '%' }} /></span><span className="text-sm text-right num">{fmt(d[1])}</span></div>)}</div>;
}
export const yuan = (v: number) => '¥' + v.toLocaleString();
