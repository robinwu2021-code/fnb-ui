// 系统栏：左 店名 + 当班人 · 中 6 个工作台页签 · 右 图标入口（打印、网络、外卖、会员、显示）+ 时间
import { useStore, type ModuleKey } from '@/store';
import { MODULES } from './modules';
import { useBizDef } from '@/biz';
import { useT } from '@/i18n';
import { SysBarShell, SysEntry } from '@/components/pos/sysbar';
import { Ico } from '@/lib/icon';
import { Badge, StatusIcon } from '@/components/ui/badge';
import type { ReactNode } from 'react';
import { EditBar } from './edit/EditChrome';
import { Slot } from './edit/Slot';

function Entry({ act, label, children }: { act: string; label: string; children: ReactNode }) {
  const dispatch = useStore((s) => s.act);
  return <SysEntry label={label} onClick={() => dispatch(act)}>{children}</SysEntry>;
}
export function SysBar() {
  const t = useT(); const wb = useStore((s) => s.wb), banner = useStore((s) => s.banner), dispatch = useStore((s) => s.act);
  const tabs = useBizDef().tabs.filter((x) => !x.home); /* 页签按业态（biz/index.ts） */
  const on = MODULES[wb].home || wb; const edit = useStore((s) => s.edit);
  if (edit) return <header className="relative z-20 flex items-center gap-2 h-56px px-pad bg-surface no-bar [&>*]:flex-none shadow-[inset_0_-2px_0_var(--c-inverse)]"><EditBar /></header>;
  return (
    <SysBarShell brand={t('store.name')} sub={t('store.shift')} tabs={tabs.map((x) => ({ v: x.v, label: t(x.label) }))} cur={on} onTab={(k) => dispatch('wb:' + k)} time="12:30">
      <Slot id="s" className="mr-1 pr-1.5 border-r border-r-solid border-wash-strong" />
      <Entry act="sheet:print" label={t('entry.print')}><StatusIcon ent="device" k="no_paper" size={18} /></Entry>
      <Entry act="sheet:net" label={banner ? t('entry.net.off') : t('entry.net.on')}><StatusIcon ent="device" k={banner ? 'offline' : 'online'} size={18} /></Entry>
      <Entry act="sheet:takeout" label={t('entry.takeout')}><Ico name="bike" size={18} /><Badge n={2} className="absolute -top-1 -right-1" /></Entry>
      <Entry act="sheet:member" label={t('entry.member')}><Ico name="id-card" size={18} /></Entry>
      <Entry act="sheet:set" label={t('entry.display')}><Ico name="sliders-horizontal" size={18} /></Entry>
    </SysBarShell>
  );
}
