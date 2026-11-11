// 桌台平面（方案 §4.2）：每个桌区一张栅格画布（1 格 = 1 单位，渲染时按内容区宽度换算像素）。
// place：桌号 → 摆放（桌区、坐标、占格、形状、朝向）；fixtures：固定物。实体固定物参与碰撞，分区 / 包间 / 门只是地面标记。
export type Shape = 'square' | 'rect' | 'round' | 'booth';
export type Rot = 0 | 90 | 180 | 270;
export interface Place { area: string; x: number; y: number; w: number; h: number; shape: Shape; rot: Rot; seats?: number }
export type FixKind = 'kitchen' | 'bar' | 'cashier' | 'pillar' | 'plant' | 'door' | 'zone' | 'room';
export interface Fixture { id: string; kind: FixKind; x: number; y: number; w: number; h: number; label?: string }
export interface AreaPlan { cols: number; rows: number; fixtures: Fixture[] }
export interface FloorData { areas: Record<string, AreaPlan>; place: Record<string, Place> }

/** 参与碰撞的固定物（地面标记不挡） */
export const SOLID: FixKind[] = ['kitchen', 'bar', 'cashier', 'pillar', 'plant'];
export const FIX_ICON: Record<FixKind, string> = { kitchen: 'chef-hat', bar: 'wine', cashier: 'monitor-smartphone', pillar: 'square', plant: 'leaf', door: 'door-open', zone: 'square-dashed', room: 'door-closed' };
/** 桌台库：[key, 形状, 宽, 高, 座位] */
export const TABLE_LIB: [string, Shape, number, number, number][] = [['t2', 'square', 3, 3, 2], ['t4', 'square', 4, 4, 4], ['t6', 'rect', 7, 4, 6], ['r8', 'round', 6, 6, 8], ['booth', 'booth', 5, 4, 4]];
/** 固定物库：[类型, 宽, 高] */
export const FIX_LIB: [FixKind, number, number][] = [['pillar', 1, 1], ['plant', 2, 2], ['bar', 6, 3], ['cashier', 5, 3], ['zone', 10, 8], ['door', 4, 1]];

const P = (area: string, x: number, y: number, w: number, h: number, shape: Shape, rot: Rot = 0): Place => ({ area, x, y, w, h, shape, rot });
export const DEFAULT_FLOOR: FloorData = {
  areas: {
    大厅: { cols: 40, rows: 26, fixtures: [
      { id: 'z-win', kind: 'zone', x: 0, y: 0, w: 8, h: 26, label: '靠窗区' },
      { id: 'kitchen', kind: 'kitchen', x: 31, y: 0, w: 9, h: 5, label: '厨房' },
      { id: 'bar', kind: 'bar', x: 35, y: 7, w: 5, h: 11, label: '吧台' },
      { id: 'cashier', kind: 'cashier', x: 31, y: 21, w: 6, h: 5, label: '收银' },
      { id: 'door', kind: 'door', x: 17, y: 25, w: 5, h: 1, label: '入口' },
      { id: 'p1', kind: 'pillar', x: 15, y: 14, w: 1, h: 1 }, { id: 'p2', kind: 'pillar', x: 24, y: 14, w: 1, h: 1 },
      { id: 'plant1', kind: 'plant', x: 9, y: 23, w: 2, h: 2 }, { id: 'plant2', kind: 'plant', x: 27, y: 23, w: 2, h: 2 },
    ] },
    包间: { cols: 40, rows: 26, fixtures: [
      { id: 'corr', kind: 'zone', x: 0, y: 12, w: 40, h: 2, label: '走廊' },
      ...[0, 1, 2].flatMap((i) => [
        { id: 'rm' + (i + 1), kind: 'room' as FixKind, x: 1 + i * 13, y: 0, w: 12, h: 12, label: 'V0' + (i + 1) },
        { id: 'rm' + (i + 4), kind: 'room' as FixKind, x: 1 + i * 13, y: 14, w: 12, h: 12, label: 'V0' + (i + 4) },
      ]),
      { id: 'door-r', kind: 'door', x: 0, y: 12, w: 1, h: 2, label: '入口' },
    ] },
  },
  place: {
    A01: P('大厅', 1, 2, 5, 4, 'booth'), A02: P('大厅', 1, 8, 5, 4, 'booth'), A03: P('大厅', 1, 14, 5, 4, 'booth'), A04: P('大厅', 1, 20, 5, 4, 'booth'),
    A05: P('大厅', 11, 2, 4, 4, 'square'), A06: P('大厅', 17, 2, 4, 4, 'square'), A07: P('大厅', 23, 2, 4, 4, 'square'),
    A08: P('大厅', 11, 8, 4, 4, 'round'), A09: P('大厅', 17, 8, 4, 4, 'round'), A10: P('大厅', 23, 8, 4, 4, 'round'),
    A11: P('大厅', 10, 17, 7, 4, 'rect'), A12: P('大厅', 21, 17, 7, 4, 'rect'),
    V01: P('包间', 4, 3, 6, 6, 'round'), V02: P('包间', 17, 3, 6, 6, 'round'), V03: P('包间', 30, 3, 6, 6, 'round'),
    V04: P('包间', 4, 17, 6, 6, 'round'), V05: P('包间', 17, 17, 6, 6, 'round'), V06: P('包间', 30, 17, 6, 6, 'round'),
  },
};
export const cloneFloor = (f: FloorData): FloorData => JSON.parse(JSON.stringify(f));
export const AREAS = Object.keys(DEFAULT_FLOOR.areas);

type Box = { x: number; y: number; w: number; h: number };
export const overlap = (a: Box, b: Box) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
/** 在某桌区里放 box 是否合法：不出界、不压桌台、不压实体固定物（skip = 自己） */
export function fits(f: FloorData, area: string, box: Box, skip: string): boolean {
  const a = f.areas[area]; if (!a) return false;
  if (box.x < 0 || box.y < 0 || box.x + box.w > a.cols || box.y + box.h > a.rows) return false;
  for (const [id, p] of Object.entries(f.place)) if (id !== skip && p.area === area && overlap(p, box)) return false;
  for (const x of a.fixtures) if (x.id !== skip && SOLID.includes(x.kind) && overlap(x, box)) return false;
  return true;
}
/** 从中间往外找一个放得下 w×h 的空位 */
export function freeSpot(f: FloorData, area: string, w: number, h: number): { x: number; y: number } | null {
  const a = f.areas[area]; const cx = Math.floor((a.cols - w) / 2), cy = Math.floor((a.rows - h) / 2);
  for (let r = 0; r < Math.max(a.cols, a.rows); r++)
    for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) {
      if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue;
      const box = { x: cx + dx, y: cy + dy, w, h }; if (fits(f, area, box, '')) return box;
    }
  return null;
}
/** 自动排布：把某桌区的桌台按 6 列铺开（导入旧数据的兜底），实体固定物保留 */
export function autoArrange(f: FloorData, area: string): FloorData {
  const n = cloneFloor(f); const ids = Object.keys(n.place).filter((id) => n.place[id].area === area).sort();
  ids.forEach((id) => { n.place[id] = { ...n.place[id], x: -100, y: -100 }; });
  ids.forEach((id) => { const p = n.place[id]; const s = freeSpotScan(n, area, p.w, p.h); if (s) n.place[id] = { ...p, ...s }; });
  return n;
}
function freeSpotScan(f: FloorData, area: string, w: number, h: number) {
  const a = f.areas[area];
  for (let y = 1; y + h <= a.rows; y += 1) for (let x = 1; x + w <= a.cols; x += 1) { if (fits(f, area, { x: x - 1, y: y - 1, w: w + 2, h: h + 2 }, '')) return { x, y, w, h }; } // 四周留 1 格过道
  return freeSpot(f, area, w, h);
}

/** 保存的平面存在本机（原型）；读不到用默认 */
export const FLOOR_KEY = 'pos-react.floor';
export function loadFloor(): FloorData {
  try { const o = JSON.parse(localStorage.getItem(FLOOR_KEY) || 'null'); if (o && o.areas && o.place) return o; } catch { /* 无痕模式等读不到时用默认 */ }
  return cloneFloor(DEFAULT_FLOOR);
}
export function saveFloor(f: FloorData) { try { localStorage.setItem(FLOOR_KEY, JSON.stringify(f)); } catch { /* 存不了就只在本次会话有效 */ } }
