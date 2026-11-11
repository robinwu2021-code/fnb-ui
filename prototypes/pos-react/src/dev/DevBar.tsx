// 页首：业态 + 工作台切换 + 显示开关（对应原型 pos-ui.html 页首的 pd-switch），不属于产品界面
import { useStore } from '@/store';
import { BIZ } from '@/biz';
import { useBiz, type BizKey, type RetailTab } from '@/biz/store';
import { RetailBar } from '@/retail/RetailBar';
import { useT } from '@/i18n';
import { Seg } from '@/components/ui/toggle-group';
import { StyleControls } from './StyleControls';
import { useState } from 'react';
export function DevBar({ page }: { page: 'app' | 'components' }) {
  const t = useT(); const s = useStore(); const [open, setOpen] = useState(false);
  const biz = useBiz((b) => b.biz), rwb = useBiz((b) => b.rwb), setBiz = useBiz((b) => b.set);
  const tabs = BIZ[biz].tabs;
  return (
    <header className="flex flex-col gap-2 mb-4">
      <div className="flex items-baseline gap-3 flex-wrap"><h1 className="text-lg font-700 m-0">全线点餐 POS · React + UnoCSS</h1><span className="text-sm text-sub">prototypes/pos-react · 对照 prototypes/bapp/pos-ui.html · pos-components.html</span><Seg value={page} onChange={(v) => { location.hash = v === 'app' ? '' : '#' + v; }} items={[{ v: 'app', label: t('dev.app') }, { v: 'components', label: t('dev.components') }]} /></div>
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-sub w-72px flex-none">{s.lang === 'en' ? 'Business' : '业态'}</span>
        <Seg value={biz} onChange={(v) => setBiz({ biz: v })} items={(Object.keys(BIZ) as BizKey[]).map((k) => ({ v: k, label: BIZ[k].label[s.lang === 'en' ? 1 : 0] }))} />
        <span className="text-sm text-sub ml-3">{t('dev.wb')}</span>
        <Seg value={biz === 'retail' ? rwb : s.wb} onChange={(v) => (biz === 'retail' ? setBiz({ rwb: v as RetailTab }) : s.act('wb:' + v))} items={tabs.map((x) => ({ v: x.v, label: t(x.label) }))} />
        <button type="button" className="hit text-sm text-primary-text font-600 ml-2" aria-expanded={open} onClick={() => setOpen((o) => !o)}>{open ? '收起开关 ▴' : '显示开关 ▾'}</button>
      </div>
      {page === 'app' && biz === 'retail' && (rwb === 'pos' || rwb === 'orders' || rwb === 'stock' || rwb === 'goods') && <RetailBar />}
      {open && <StyleControls compact />}
    </header>
  );
}
