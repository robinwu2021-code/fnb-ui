// 侧滑面板 ← 原型 OVL.sheet：外卖 · 会员 · 打印 · 网络 · 设置（显示开关）五个页签
import { useStore } from '@/store';
import { MEMBER } from '@/data/sample';
import { useT } from '@/i18n';
import { Sheet } from '@/components/ui/sheet';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Cells, Cell } from '@/components/ui/description-list';
import { StatusTag, StatusIcon, Chip } from '@/components/ui/badge';
import { Note, Empty } from '@/components/ui/feedback';
import { Switch } from '@/components/ui/switch';
import { Keypad } from '@/components/ui/keypad';
import { Ico } from '@/lib/icon';
import { StyleControls } from '@/dev/StyleControls';
import { money } from '@/lib/money';
import { pendingDelivery, platRef } from '@/lib/channels';
import { PlatLogo } from '@/components/ui/plat-logo';

const TABS = ['takeout', 'member', 'print', 'net', 'set'] as const;
export function SheetPanel({ open }: { open: boolean }) {
  const t = useT(); const s = useStore(), dispatch = s.act;
  return (
    <Sheet open={open} onClose={() => dispatch('ov:none')} title={t('sheet.' + s.sheet)} closeLabel={t('common.close')}>
      <Tabs value={s.sheet} onValueChange={(v) => dispatch('sheet:' + v)} className="contents">
        <TabsList>{TABS.map((k) => <TabsTrigger key={k} value={k}>{t('sheet.' + k)}</TabsTrigger>)}</TabsList>
        <TabsContent value="takeout" className="contents">
          {pendingDelivery(s.orders).length === 0 && <Empty title={t('sheet.takeout.none')} />}
          {pendingDelivery(s.orders).map((o) => (
            <Cells key={o[0]}><Cell k={<span className="text-ink inline-flex items-center gap-1.5">{platRef(o[1], s, s.lang).key && <PlatLogo k={platRef(o[1], s, s.lang).key!} size={18} />}{t('sheet.takeout.row', { p: platRef(o[1], s, s.lang).label, n: o[4], m: money(o[6]) })}</span>}><StatusTag ent="delivery" k={o[8]} /></Cell><Cell k={<span className="help">{t('sheet.takeout.at', { t: o[5] })}</span>}><span className="flex gap-gap"><Button variant="muted" size="sm" onClick={() => dispatch('toast:sheet.rejected')}>{t('sheet.reject')}</Button><Button variant="soft" size="sm" onClick={() => dispatch('toast:sheet.accepted')}>{t('sheet.accept')}</Button></span></Cell></Cells>
          ))}
        </TabsContent>
        <TabsContent value="member" className="contents">
          {s.member ? (
            <>
              <div className="bg-wash rounded-rt py-2.5 px-3 flex flex-col gap-2">
                <div className="flex items-center gap-1.5 flex-wrap"><StatusIcon ent="member" k="linked" /><b>{MEMBER.name}</b><Chip>{MEMBER.layer}</Chip>{MEMBER.tags.map((x) => <Chip key={x}>{x}</Chip>)}</div>
                <div className="grid grid-cols-3 gap-2"><span><small className="block text-xs text-sub">{t('sheet.member.bal')}</small><b className="num">{money(MEMBER.bal)}</b></span><span><small className="block text-xs text-sub">{t('sheet.member.coupons')}</small><b className="num">{t('sheet.member.couponsV', { n: MEMBER.coupons })}</b></span><span><small className="block text-xs text-sub">{t('sheet.member.visits')}</small><b className="num">{t('sheet.member.visitsV')}</b></span></div>
              </div>
              <div className="flex flex-wrap gap-gap"><Button variant="soft" onClick={() => dispatch('toast:toast.notYet')}>{t('sheet.member.topup')}</Button><Button variant="muted" onClick={() => dispatch('gomember')}>{t('sheet.member.detail')}</Button><Button variant="muted" onClick={() => dispatch('munbind')}>{t('sheet.member.unbind')}</Button></div>
            </>
          ) : (
            <>
              <Empty icon={<Ico name="scan-line" size={44} className="text-success-text" />} title={t('sheet.member.scan')} desc={t('sheet.member.scanHint')} />
              <div className="flex items-center justify-end h-ctl px-pad rounded-rk bg-wash num font-600">{s.mph || <span className="help">{t('sheet.member.phoneHint')}</span>}</div>
              <Keypad keys={['1', '2', '3', '4', '5', '6', '7', '8', '9', 'clr', '0', 'back'].map((k) => ({ k, fn: k === 'clr' || k === 'back', label: k === 'clr' ? t('sheet.clr') : undefined, aria: k === 'back' ? t('auth.back') : undefined }))} onKey={(k) => dispatch('mph:' + k)} />
              {s.mph.length === 11 && (/6621$/.test(s.mph) ? <Button block onClick={() => dispatch('scan:member')}>{t('sheet.member.found')}</Button> : <><Note>{t('sheet.member.notFound')}</Note><div><Button variant="soft" onClick={() => dispatch('toast:toast.notYet')}>{t('sheet.member.new')}</Button></div></>)}
            </>
          )}
          <span className="help">{t('sheet.member.help')}</span>
        </TabsContent>
        <TabsContent value="print" className="contents">
          <Cells><Cell k={t('sheet.print.front')}><StatusTag ent="device" k="online" label={t('sheet.print.ok')} /></Cell><Cell k={t('sheet.print.hot')}><StatusTag ent="device" k="no_paper" label={t('sheet.print.noPaper')} /></Cell><Cell k={t('sheet.print.cold')}><StatusTag ent="device" k="online" label={t('sheet.print.ok')} /></Cell><Cell k={t('sheet.print.bar')}><StatusTag ent="device" k="online" label={t('sheet.print.ok')} /></Cell></Cells>
          <div className="flex flex-wrap gap-gap"><Button variant="muted" onClick={() => dispatch('toast:sheet.reprinted')}>{t('sheet.reprint')}</Button><Button variant="muted" onClick={() => dispatch('toast:toast.drawer')}>{t('sheet.drawer')}</Button><Button variant="soft" onClick={() => dispatch('toast:sheet.reprinted2')}>{t('sheet.reprintFailed')}</Button><Button variant="link" className="hit text-sm ml-auto" onClick={() => dispatch('dev:page')}>{t('dv.allDevices')}</Button></div>
        </TabsContent>
        <TabsContent value="net" className="contents">
          <Cells><Cell k={t('sheet.net.server')}>{s.banner ? <StatusTag ent="device" k="offline" label={t('sheet.net.disconnected')} /> : <StatusTag ent="device" k="online" label={t('sheet.net.connected')} />}</Cell><Cell k={t('sheet.net.gw')}>{s.banner ? <StatusTag ent="device" k="offline" label={t('sheet.net.unavailable')} /> : <StatusTag ent="device" k="online" label={t('sheet.net.connected')} />}</Cell><Cell k={t('sheet.net.pending')} v={t('sheet.net.orders', { n: s.banner ? 3 : 0 })} num /></Cells>
          <Note>{t('sheet.net.note')}</Note>
          <Cells><Cell k={t('sheet.net.sim')}><Switch checked={s.banner} onCheckedChange={(v) => s.set({ banner: v })} aria-label={t('sheet.net.sim')} /></Cell></Cells>
        </TabsContent>
        <TabsContent value="set" className="contents">
          <StyleControls /><div><Button variant="soft" onClick={() => dispatch('goset')}>{t('sheet.set.open')}</Button></div>
        </TabsContent>
      </Tabs>
    </Sheet>
  );
}
