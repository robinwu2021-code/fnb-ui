// 业务状态字典：19 实体 112 状态，由 tools/gen-colors.py 生成（scripts/sync-tokens.mjs 同步成 json）。
import raw from '@/data/pos-states.json';
import type { CSSProperties } from 'react';

export type StGroup = 'action' | 'progress' | 'waiting' | 'done' | 'booked' | 'held' | 'trace' | 'closed' | 'failed';
export interface StDef { zh: string; en: string; group: StGroup; icon: string }
interface Dict { groups: Record<StGroup, { zh: string; en: string; form: 'chip' | 'dot' }>; states: Record<string, Record<string, StDef>> }
export const STATES = raw as unknown as Dict;
/* 本地扩展（设备发现方案新增）：先在这里合并，之后并入 bapp/tools/gen-colors.py 再 sync:tokens，届时删掉这段 */
const EXTRA: Record<string, Record<string, StDef>> = {
  device: { found: { zh: '新发现', en: 'Found', group: 'booked', icon: 'radar' }, pairing: { zh: '连接中', en: 'Connecting', group: 'waiting', icon: 'loader-circle' } },
  feature: { todo: { zh: '未实现', en: 'Not built yet', group: 'closed', icon: 'construction' } },
};
Object.entries(EXTRA).forEach(([ent, m]) => { STATES.states[ent] = { ...(STATES.states[ent] || {}), ...m }; });

export function stateOf(ent: string, key: string): StDef | undefined { return STATES.states[ent]?.[key]; }
export function stGroup(ent: string, key: string): StGroup { return stateOf(ent, key)?.group ?? 'closed'; }
export function stForm(g: StGroup) { return STATES.groups[g]?.form ?? 'dot'; }
export function stName(ent: string, key: string, lang: 'zh' | 'en') { const s = stateOf(ent, key); return s ? (lang === 'en' ? s.en : s.zh) : key; }
/* 把状态组的 5 个角色写成局部变量：--d 图标 --t 文字 --b 浅底 --bk 状态块 --bks 要处理的状态块 */
export function stv(g: StGroup): CSSProperties {
  return { '--d': `var(--c-st-${g}-dot)`, '--t': `var(--c-st-${g}-text)`, '--b': `var(--c-st-${g}-tint)`, '--bk': `var(--c-st-${g}-block)`, '--bks': `var(--c-st-${g}-block-strong)` } as CSSProperties;
}
