// 显示开关：语言 · 布局 L1–L6 · 密度 · 框架圆角 · 按钮形状 · 皮肤 · 色系 · 菜品图 · 卡片排布 · 菜名 · 网络。页首和侧滑「设置」共用。
import { useStore } from '@/store';
import { LAYOUTS } from '@/layout/modules';
import { useT, useLang } from '@/i18n';
import { Seg } from '@/components/ui/toggle-group';
import { OptionGroup, Option } from '@/components/ui/radio-cards';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import type { ReactNode } from 'react';

const SKINS = [['mono', '墨黑', 'Ink', '#18181B'], ['brand', '虹选红', 'Brand red', '#E1251B'], ['blue', '经典蓝', 'Blue', '#2C69FF'], ['fresh', '微信绿', 'Green', '#00B578'], ['teal', '青碧', 'Teal', '#008484'], ['amber', '琥珀', 'Amber', '#A46911'], ['violet', '紫', 'Violet', '#7A4CE0']];
const SHAPES: [string, string][] = [['f6-b4', 'set.shape.crisp'], ['f14-b8', 'set.shape.std'], ['f20-pill', 'set.shape.soft'], ['f10-pill', 'set.shape.mix'], ['f2-b2', 'set.shape.square']];
const FRAMES = [['f2', '方 2', 'Square 2'], ['f6', '小 6', 'S 6'], ['f10', '中 10', 'M 10'], ['f14', '大 14', 'L 14'], ['f20', '特大 20', 'XL 20']];
const BTNS = [['b2', '方 2', 'Square 2'], ['b4', '小圆 4', 'Soft 4'], ['b8', '圆角 8', 'Round 8'], ['b12', '大圆 12', 'Round 12'], ['pill', '胶囊', 'Pill']];
function Row({ label, children }: { label: ReactNode; children: ReactNode }) { return <div className="flex flex-wrap items-center gap-2"><span className="text-sm text-sub w-72px flex-none">{label}</span>{children}</div>; }

export function StyleControls({ compact }: { compact?: boolean }) {
  const t = useT(), lang = useLang(); const s = useStore();
  return (
    <div className={cn('flex flex-col gap-2', compact && 'gap-1.5')}>
      <Row label={t('dev.lang')}><Seg value={s.lang} onChange={(v) => s.set({ lang: v })} items={[{ v: 'zh', label: '中文' }, { v: 'en', label: 'English' }]} /></Row>
      <Row label={t('edit.open')}><button type="button" onClick={() => s.act('edit:on')} className="inline-flex items-center gap-1.5 h-ctl-sm px-3 rounded-rb bg-inverse text-on-inverse text-sm font-600"><Ico name="pencil-ruler" size={16} />{t('edit.open')}</button><span className="help">{t('edit.openHint')}</span></Row>
      <Row label={t('dev.layout')}><Seg value={s.nav + '-' + s.detail} onChange={(v) => { const [nav, detail] = v.split('-'); s.set({ nav: nav as any, detail: detail as any }); }} items={LAYOUTS.map((l) => ({ v: l.nav + '-' + l.detail, label: <span title={lang === 'en' ? l.en : l.zh}>{l.id}</span> }))} /></Row>
      <Row label={t('dev.density')}><Seg value={s.density} onChange={(v) => s.set({ density: v })} items={[{ v: 'compact', label: t('dev.compact') + ' 36·44·52' }, { v: 'standard', label: t('dev.standard') + ' 40·48·56' }]} /></Row>
      <Row label={t('set.shape')}><Seg value={s.frame + '-' + s.btn} onChange={(v) => { const [frame, btn] = v.split('-'); s.set({ frame, btn }); }} items={SHAPES.map(([v, l]) => ({ v, label: t(l) }))} /></Row>
      <Row label={t('dev.frame')}><Seg value={s.frame} onChange={(v) => s.set({ frame: v })} items={FRAMES.map(([v, zh, en]) => ({ v, label: lang === 'en' ? en : zh }))} /></Row>
      <Row label={t('dev.btn')}><Seg value={s.btn} onChange={(v) => s.set({ btn: v })} items={BTNS.map(([v, zh, en]) => ({ v, label: lang === 'en' ? en : zh }))} /></Row>
      <Row label={t('dev.skin')}><OptionGroup value={s.skin} onChange={(v) => s.set({ skin: v })} className="gap-1">{SKINS.map(([v, zh, en, c]) => <Option key={v} value={v} className="min-h-ctl-sm py-0 px-2.5 text-sm" label={<span className="inline-flex items-center gap-1.5"><i className="w-10px h-10px rounded-full" style={{ background: c }} />{lang === 'en' ? en : zh}</span>} />)}</OptionGroup></Row>
      <Row label={t('dev.tone')}><Seg value={s.tone} onChange={(v) => s.set({ tone: v })} items={[{ v: 'quiet', label: t('dev.quiet') }, { v: 'color', label: t('dev.color') }]} /></Row>
      <Row label={t('dev.pic')}><Seg value={s.pic} onChange={(v) => s.set({ pic: v })} items={[{ v: 'img', label: t('order.pic.img') }, { v: 'off', label: t('order.pic.off') }]} />{s.pic === 'img' && <Seg value={s.card} onChange={(v) => s.set({ card: v })} items={[{ v: 'v', label: t('order.card.v') }, { v: 'h', label: t('order.card.h') }]} />}</Row>
      <Row label={t('dev.names')}><Seg value={s.names} onChange={(v) => s.set({ names: v })} items={[{ v: 'bi', label: t('dev.bi') }, { v: 'one', label: t('dev.one') }]} /></Row>
      <Row label={t('dev.net')}><Seg value={s.banner ? 'on' : 'off'} onChange={(v) => s.set({ banner: v === 'on' })} items={[{ v: 'off', label: t('dev.online') }, { v: 'on', label: t('dev.offline') }]} /></Row>
    </div>
  );
}
