// 布局表：对应原型 ui/layout.js 的 MODULES。每个模块声明布局 W1–W4 和栏宽，不在渲染里写死。
import type { ModuleKey, NavPos, DetailPos } from '@/store';
export const NAV = { sm: '104px', md: '128px', lg: '160px' } as const;
export type Layout = 'W1' | 'W2' | 'W3' | 'W4';
export interface ModuleDef { name: string; layout: Layout; nav?: string; detail?: string; detailSide?: DetailPos; home?: ModuleKey }
export const MODULES: Record<ModuleKey, ModuleDef> = {
  tables: { name: 'tab.tables', layout: 'W1', nav: NAV.md },
  order: { name: 'tab.order', layout: 'W1', nav: NAV.sm },
  checkout: { name: 'tab.checkout', layout: 'W2', detail: '440px', detailSide: 'left' },
  orders: { name: 'tab.orders', layout: 'W1', nav: NAV.md },
  menu: { name: 'tab.menu', layout: 'W1', nav: NAV.md, home: 'settings' },
  members: { name: 'tab.members', layout: 'W1', nav: NAV.md, home: 'settings' },
  reports: { name: 'tab.reports', layout: 'W4', nav: NAV.lg },
  shift: { name: 'tab.shift', layout: 'W3', home: 'settings' },
  settings: { name: 'tab.settings', layout: 'W4', nav: NAV.lg },
};
export const LAYOUTS: { id: string; nav: NavPos; detail: DetailPos; zh: string; en: string }[] = [
  { id: 'L1', nav: 'left', detail: 'right', zh: '导航左 · 详情右', en: 'Nav left · detail right' }, { id: 'L2', nav: 'top', detail: 'right', zh: '导航上 · 详情右', en: 'Nav top · detail right' },
  { id: 'L3', nav: 'top', detail: 'left', zh: '导航上 · 详情左', en: 'Nav top · detail left' }, { id: 'L4', nav: 'right', detail: 'left', zh: '导航右 · 详情左', en: 'Nav right · detail left' },
  { id: 'L5', nav: 'left', detail: 'left', zh: '导航左 · 详情左', en: 'Nav left · detail left' }, { id: 'L6', nav: 'right', detail: 'right', zh: '导航右 · 详情右', en: 'Nav right · detail right' },
];
/* 网格：[grid-template-areas, grid-template-columns, grid-template-rows] */
function gridNoDetail(w: string, nav: NavPos): [string, string, string] {
  if (nav === 'top') return ['"nav" "content"', 'minmax(0,1fr)', 'auto minmax(0,1fr)'];
  return nav === 'right' ? ['"content nav"', 'minmax(0,1fr) ' + w, 'minmax(0,1fr)'] : ['"nav content"', w + ' minmax(0,1fr)', 'minmax(0,1fr)'];
}
function grid(hasNav: boolean, N: string, D: string, det: DetailPos, nav: NavPos): [string, string, string] {
  if (!hasNav) return det === 'right' ? ['"content detail"', '1fr ' + D, 'minmax(0,1fr)'] : ['"detail content"', D + ' 1fr', 'minmax(0,1fr)'];
  if (nav === 'top') return det === 'right' ? ['"nav detail" "content detail"', '1fr ' + D, 'auto minmax(0,1fr)'] : ['"detail nav" "detail content"', D + ' 1fr', 'auto minmax(0,1fr)'];
  const order = ({ 'left-right': ['nav', 'content', 'detail'], 'right-left': ['detail', 'content', 'nav'], 'left-left': ['nav', 'detail', 'content'], 'right-right': ['content', 'nav', 'detail'] } as Record<string, string[]>)[nav + '-' + det];
  const w: Record<string, string> = { nav: N, content: '1fr', detail: D };
  return ['"' + order.join(' ') + '"', order.map((a) => w[a]).join(' '), 'minmax(0,1fr)'];
}
export function layoutOf(wb: ModuleKey, nav: NavPos, detail: DetailPos) {
  const m = MODULES[wb];
  if (m.layout === 'W4') return gridNoDetail(m.nav!, nav);
  return grid(m.layout === 'W1', m.nav || NAV.sm, m.detail || '360px', m.detailSide || detail, nav);
}
