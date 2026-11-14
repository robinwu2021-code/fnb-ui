// 平面编辑操作：所有改动先进草稿（fdraft），每次改动压一层撤销栈；保存才写回 floor 并存本机。
import { useStore } from '@/store';
import { notify } from '@/store/actions';
import { cloneFloor, DEFAULT_FLOOR, saveFloor, type FloorData } from '@/data/floors';

const S = () => useStore.getState();
export function beginEdit(area?: string) {
  const s = S(); s.set({ fedit: true, fiso: false, fdraft: cloneFloor(s.floor), fhist: [], ffut: [], fsel: null, tview: s.tview === 'iso' ? 'iso' : 'floor', wb: 'tables', ov: 'none', ...(area ? { farea: area } : {}) });
}
/** 改草稿：fn 直接改传进来的副本（或返回新对象）；压撤销栈、清重做栈 */
export function change(fn: (d: FloorData) => FloorData | void) {
  const s = S(); if (!s.fdraft) return;
  const next = cloneFloor(s.fdraft); const r = fn(next) || next;
  s.set({ fdraft: r, fhist: [...s.fhist, s.fdraft].slice(-50), ffut: [] });
}
export function undo() { const s = S(); if (!s.fhist.length || !s.fdraft) return; s.set({ fdraft: s.fhist[s.fhist.length - 1], fhist: s.fhist.slice(0, -1), ffut: [s.fdraft, ...s.ffut] }); }
export function redo() { const s = S(); if (!s.ffut.length || !s.fdraft) return; s.set({ fdraft: s.ffut[0], ffut: s.ffut.slice(1), fhist: [...s.fhist, s.fdraft] }); }
export function resetDraft() { change(() => cloneFloor(DEFAULT_FLOOR)); S().set({ fsel: null }); notify(S, 'fl.resetDone'); }
export function cancelEdit() { S().set({ fedit: false, fdraft: null, fhist: [], ffut: [], fsel: null }); }
/** 保存：写回 floor；编辑器里新加的桌台补进桌台列表（空闲），改了座位的同步座位数；删掉的桌台从平面移走，桌台本身保留 */
export function saveEdit() {
  const s = S(); const d = s.fdraft; if (!d) return;
  const tables = s.tables.map((t) => (d.place[t.id]?.seats ? { ...t, seats: d.place[t.id].seats! } : t));
  Object.entries(d.place).forEach(([id, p]) => { if (!tables.some((t) => t.id === id)) tables.push({ id, st: 'free', seats: p.seats || 4, ppl: 0, min: 0, amt: 0 }); });
  s.set({ floor: d, tables, fedit: false, fdraft: null, fhist: [], ffut: [], fsel: null });
  saveFloor(d); notify(S, 'fl.saved');
}
/** 新桌号：按桌区前缀（大厅 A、包间 V）递增 */
export function nextTableId(d: FloorData, area: string, existing: string[]) {
  const pre = area === '包间' ? 'V' : 'A';
  const nums = [...Object.keys(d.place), ...existing].filter((id) => id.startsWith(pre)).map((id) => parseInt(id.slice(1), 10) || 0);
  const n = Math.max(0, ...nums) + 1; return pre + String(n).padStart(2, '0');
}
