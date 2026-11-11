// 全局状态：对应原型 core.js 的 S + 可变示例数据（LINES / TABLES / ORDERS）。显示类开关存 localStorage（和原型同 key 的 React 版）。
import { create } from 'zustand';
import { useMemo } from 'react';
import { DISHES, INITIAL_CAMPS, INITIAL_LINES, INITIAL_MEMBERS, INITIAL_MTAGS, INITIAL_ORDERS, INITIAL_PLANS, INITIAL_REDEEMED, INITIAL_TABLES, MEMBER, type Camp, type Dish, type Line, type Member, type OrderRow, type Plan, type Redeemed, type Table } from '@/data/sample';
import { totals, type Totals } from '@/lib/pricing';
import { ACT } from './actions';
import { wireDomainEvents } from './wiring';
import { DEFAULT_EXTRA, DEFAULT_QUICK, cleanSlots, readSlots, writeSlots, type ExtraSlots } from '@/data/slots';
import { QUICK } from '@/data/sample';
import { INITIAL_PLATS, INITIAL_SYNC, type PlatKey, type PlatState, type Region, type SyncMap } from '@/data/platforms';
import { loadFloor, type FloorData } from '@/data/floors';
import { INITIAL_DEVICES, type Device, type DeviceKind } from '@/data/devices';

export type ModuleKey = 'tables' | 'order' | 'checkout' | 'orders' | 'menu' | 'members' | 'reports' | 'shift' | 'settings';
export type NavPos = 'left' | 'top' | 'right';
export type DetailPos = 'left' | 'right';
export interface StyleState {
  lang: 'zh' | 'en'; names: 'bi' | 'one'; skin: string; tone: 'quiet' | 'color'; frame: string; btn: string; density: 'compact' | 'standard';
  nav: NavPos; detail: DetailPos; pic: 'img' | 'off'; card: 'v' | 'h'; banner: boolean; quick: string[]; slots: ExtraSlots;
  /** 门店地区：决定可对接的外送平台（台湾 / 阿联酋） */
  region: Region;
  /** 桌台视图：宫格 / 平面 / 2.5D 空间 */
  tview: 'grid' | 'floor' | 'iso';
}
export interface PosState extends StyleState {
  /** 布局编辑态：长按面板头进入；editSnap 存进入前的布局，取消时还原 */
  edit: boolean; editSnap: Pick<StyleState, 'nav' | 'detail' | 'quick' | 'slots'> | null;
  wb: ModuleKey; ov: string; sheet: string;
  cat: string; table: string; tst: string; spec: string;
  lines: Line[]; tables: Table[]; orders: OrderRow[];
  member: boolean; mph: string; note: string[]; noteDraft: string[] | null;
  lact: number; batch: boolean; bmode: '' | 'split'; sel: number[];
  rpLine: number; rpMode: 'price' | 'disc' | 'off'; rpVal: number | null; rpWhy: string; shareN: number; holdWhy: string;
  pv: { id: string; n: number }[]; cpn: string; manual: number; mwhy: string; pAct: boolean; paid: [string, number][];
  /* 结账 */
  pay: string; got: number; ent: string | null; svWait: boolean; mixm: string; change: number; showLines: boolean; pvLast: string; pvN: number; cpScan: boolean; mval: number; memberBal: number;
  /* 订单 */
  oview: 'list' | 'card'; ost: string; och: string; oper: string; osel: string; redeemed: Record<string, Redeemed[]>; pvu: string; pvuWhy: string;
  /* 菜品管理 */
  dishes: Dish[]; msub: string; mdf: string; mdc: string; mdsel: number; mcsel: number; mmsel: number; mssel: number; sync: SyncMap; syncing: boolean; synced: boolean;
  /* 会员与营销 */
  members: Member[]; mtags: [string, number][]; plans: Plan[]; camps: Camp[]; msec: string; mlayer: string; msel: number; mtsel: number; csel: number; cst: string; plan: number; rcm: number; rcState: string; redeem: string;
  /* 报表 · 设置 */
  rsec: string; rrange: string; rby: string; sg: string; si: string;
  /* 设备：列表 · 选中 · 类型筛选 · 添加设备侧滑（预选类型 / 改连接的设备 id） */
  /* 外送平台：各平台连接状态 · 详情里选中的平台 · 同步页看的对象 · 添加平台侧滑预选 */
  /* 桌台平面：看哪个桌区 · 已保存的平面 · 编辑态（草稿、撤销 / 重做栈、选中项） */
  farea: string; floor: FloorData; fedit: boolean; fdraft: FloorData | null; fhist: FloorData[]; ffut: FloorData[]; fsel: { kind: 't' | 'f'; id: string } | null;
  /** 2.5D：缩放两档 · 编辑器里预览 2.5D */
  fzoom: 1 | 2; fiso: boolean;
  plats: Record<PlatKey, PlatState>; psel: '' | PlatKey; syncObj: 'dish' | 'menu' | 'store'; paddKey: '' | PlatKey;
  devices: Device[]; dsel: string; dkind: 'all' | DeviceKind; daddKind: DeviceKind | ''; daddEdit: string;
  set: (p: Partial<PosState> | ((s: PosState) => Partial<PosState>)) => void;
  /** 动作分发：'add:宫保鸡丁' → ACT.add('宫保鸡丁')，和原型 data-act 同一套名字 */
  act: (a: string) => void;
}
const STYLE_KEYS: (keyof StyleState)[] = ['lang', 'names', 'skin', 'tone', 'frame', 'btn', 'density', 'nav', 'detail', 'pic', 'card', 'quick', 'slots', 'region', 'tview'];
const KEY = 'pos-react';
function loadStyle(): Partial<StyleState> {
  try {
    const o = JSON.parse(localStorage.getItem(KEY) || '{}'); const r: Partial<StyleState> = {}; STYLE_KEYS.forEach((k) => { if (o[k] != null) (r as any)[k] = o[k]; });
    const all = cleanSlots(readSlots({ quick: r.quick || DEFAULT_QUICK, slots: { ...DEFAULT_EXTRA, ...(r.slots || {}) } }), Object.keys(QUICK));
    return { ...r, ...writeSlots(all) };
  } catch { return {}; }
}
const FLOOR0 = loadFloor();
export const useStore = create<PosState>((set, get) => ({
  lang: 'zh', names: 'bi', skin: 'mono', tone: 'quiet', frame: 'f14', btn: 'b8', density: 'compact', nav: 'left', detail: 'right', pic: 'img', card: 'v', banner: false,
  tview: 'grid', region: 'tw', quick: DEFAULT_QUICK.slice(), slots: { t: DEFAULT_EXTRA.t.slice(), s: DEFAULT_EXTRA.s.slice(), f: DEFAULT_EXTRA.f.slice() },
  ...loadStyle(),
  edit: false, editSnap: null,
  wb: 'order', ov: 'none', sheet: 'set', cat: '热销', table: 'A03', tst: '全部', spec: '',
  lines: INITIAL_LINES.map((l) => ({ ...l })), tables: [...INITIAL_TABLES.map((t) => ({ ...t })), ...Object.entries(FLOOR0.place).filter(([id]) => !INITIAL_TABLES.some((t) => t.id === id)).map(([id, p]) => ({ id, st: 'free' as const, seats: p.seats || 4, ppl: 0, min: 0, amt: 0 }))], orders: INITIAL_ORDERS.slice(),
  member: false, mph: '', note: ['note.n1'], noteDraft: null,
  lact: -1, batch: false, bmode: '', sel: [],
  rpLine: 2, rpMode: 'disc', rpVal: 9, rpWhy: '', shareN: 2, holdWhy: '',
  pv: [], cpn: '', manual: 0, mwhy: '', pAct: true, paid: [],
  pay: '', got: 300, ent: null, svWait: false, mixm: 'cash', change: 0, showLines: false, pvLast: '', pvN: 1, cpScan: false, mval: 0, memberBal: MEMBER.bal,
  dishes: DISHES.map((d) => ({ ...d, tg: d.tg ? [...d.tg] : undefined })), msub: '菜品', mdf: 'all', mdc: '全部', mdsel: 0, mcsel: 0, mmsel: 0, mssel: 0, sync: JSON.parse(JSON.stringify(INITIAL_SYNC)), syncing: false, synced: false,
  members: INITIAL_MEMBERS.map((m) => [...m] as Member), mtags: INITIAL_MTAGS.map((x) => [...x] as [string, number]), plans: INITIAL_PLANS.map((p) => ({ ...p })), camps: INITIAL_CAMPS.map((c) => [...c] as Camp), msec: '会员', mlayer: '全部', msel: 0, mtsel: 0, csel: 0, cst: 'all', plan: 0, rcm: 0, rcState: '', redeem: '',
  farea: '大厅', floor: FLOOR0, fedit: false, fdraft: null, fhist: [], ffut: [], fsel: null, fzoom: 1, fiso: false,
  plats: JSON.parse(JSON.stringify(INITIAL_PLATS)), psel: '', syncObj: 'dish', paddKey: '',
  devices: INITIAL_DEVICES.map((d) => ({ ...d })), dsel: 'p1', dkind: 'all', daddKind: '', daddEdit: '',
  rsec: '概况', rrange: '今天', rby: 'qty', sg: 'display', si: 'shape',
  oview: 'list', ost: 'all', och: '全部', oper: '今天', osel: '#1024', redeemed: JSON.parse(JSON.stringify(INITIAL_REDEEMED)), pvu: '', pvuWhy: '',
  set: (p) => set(p as any),
  act: (a) => { const i = a.indexOf(':'); const k = i < 0 ? a : a.slice(0, i), v = i < 0 ? '' : a.slice(i + 1); const f = ACT[k]; if (f) f(v, get, (p) => set(p as any)); },
}));
useStore.subscribe((s) => { try { const o: any = {}; STYLE_KEYS.forEach((k) => (o[k] = s[k])); localStorage.setItem(KEY, JSON.stringify(o)); } catch {} });
wireDomainEvents(useStore.getState, (p) => useStore.setState(p as Partial<PosState>));
export function useTotals(): Totals {
  const lines = useStore((s) => s.lines), pv = useStore((s) => s.pv), pAct = useStore((s) => s.pAct), cpn = useStore((s) => s.cpn), manual = useStore((s) => s.manual), paid = useStore((s) => s.paid);
  return useMemo(() => totals({ lines, pv, pAct, cpn, manual, paid }), [lines, pv, pAct, cpn, manual, paid]);
}
