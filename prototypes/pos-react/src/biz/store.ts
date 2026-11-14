// 业态开关（方案 §1.1）：门店级配置「餐饮 / 零售」。只放状态和跳转，不引组件（i18n、设置页都会引它，避免循环）。
// 餐饮的当前工作台仍是主 store 的 wb；零售的当前工作台是这里的 rwb。原型存 localStorage。
import { create } from 'zustand';

export type BizKey = 'fnb' | 'retail';
export type RetailTab = 'pos' | 'orders' | 'goods' | 'reports' | 'settings' | 'members' | 'shift' | 'stock';
interface BizState { biz: BizKey; rwb: RetailTab; set: (p: Partial<Omit<BizState, 'set'>>) => void }
const KEY = 'pos-biz';
function load(): Partial<BizState> { try { const o = JSON.parse(localStorage.getItem(KEY) || '{}'); return { biz: o.biz === 'retail' ? 'retail' : 'fnb', rwb: o.rwb || 'pos' }; } catch { return {}; } }
export const useBiz = create<BizState>((set) => ({ biz: 'fnb', rwb: 'pos', ...load(), set: (p) => set(p) }));
useBiz.subscribe((s) => { try { localStorage.setItem(KEY, JSON.stringify({ biz: s.biz, rwb: s.rwb })); } catch {} });

