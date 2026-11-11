// 渠道 = 本店两个（堂食、扫码点餐）+ 当前地区已连接的外送平台。菜品页、同步页、同步动作都从这里取，不写死平台。
import { platsOf, type PlatDef, type PlatKey, type PlatState, type Region } from '@/data/platforms';
export interface Channel { key: string; name: string; en: string; icon: string; plat?: PlatDef }
export const LOCAL_CH: Channel[] = [{ key: 'dine', name: '堂食', en: 'Dine-in', icon: 'utensils' }, { key: 'mini', name: '扫码点餐', en: 'QR ordering', icon: 'qr-code' }];
/** 当前地区已连接（含暂停接单）的平台 */
export function livePlats(s: { region: Region; plats: Record<PlatKey, PlatState> }): PlatDef[] {
  return platsOf(s.region).filter((p) => p.impl && s.plats[p.key].st !== 'disconnected');
}
export function channelsOf(s: { region: Region; plats: Record<PlatKey, PlatState> }): Channel[] {
  return [...LOCAL_CH, ...livePlats(s).map((p) => ({ key: p.key, name: p.name, en: p.name, icon: 'bike', plat: p }))];
}
/** 暂停接单的显示文字：Uber Eats 暂停到某时刻（close = 今天打烊）· talabat 带原因 · 其他只写已暂停 */
export function pauseLabel(ps: PlatState, t: (k: string, p?: Record<string, string | number>) => string) {
  if (ps.pausedUntil) return t('pf.pausedUntil', { t: ps.pausedUntil === 'close' ? t('pf.untilClose') : ps.pausedUntil });
  if (ps.pauseWhy) return t('pf.pausedWhy', { r: t('pf.why.' + ps.pauseWhy) });
  return t('pf.st.paused');
}
