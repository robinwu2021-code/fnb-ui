/** 示例数据（本地演示用，不接后端）。金额一律以「分」为单位。 */

export interface Category { id: string; name: string }
export interface Dish { id: string; name: string; cat: string; price: number; desc: string; tags?: string[]; soldOut?: boolean; stock?: number; hasSpec?: boolean }
export interface Table { id: string; zone: "hall" | "room" | "take"; seats: number; status: "free" | "dining" | "billing" | "reserved"; guests?: number; minutes?: number; amount?: number; note?: string }

export const CATEGORIES: Category[] = [
  { id: "hot", name: "热销" }, { id: "sig", name: "招牌菜" }, { id: "cold", name: "凉菜" }, { id: "wok", name: "热菜" },
  { id: "staple", name: "主食" }, { id: "soup", name: "汤羹" }, { id: "drink", name: "饮品" }, { id: "set", name: "套餐" },
];

export const DISHES: Dish[] = [
  { id: "101", name: "松鼠桂鱼", cat: "sig", price: 8800, desc: "招牌 · 酸甜", hasSpec: true },
  { id: "102", name: "酸菜鱼", cat: "sig", price: 6200, desc: "新品", tags: ["新品"], hasSpec: true },
  { id: "103", name: "东坡肉", cat: "sig", price: 4800, desc: "今日售完", soldOut: true },
  { id: "201", name: "口水鸡", cat: "cold", price: 3200, desc: "凉菜 · 辣", tags: ["辣"] },
  { id: "202", name: "拍黄瓜", cat: "cold", price: 1200, desc: "素", tags: ["素"] },
  { id: "301", name: "宫保鸡丁", cat: "wok", price: 3800, desc: "微辣 · 含花生", tags: ["辣"], hasSpec: true },
  { id: "302", name: "干煸四季豆", cat: "wok", price: 2600, desc: "特价 · 原价 ¥32", tags: ["特价", "素"] },
  { id: "303", name: "上汤时蔬", cat: "wok", price: 2800, desc: "时蔬", tags: ["素"] },
  { id: "401", name: "米饭", cat: "staple", price: 300, desc: "按碗" },
  { id: "402", name: "葱油拌面", cat: "staple", price: 1600, desc: "主食" },
  { id: "501", name: "番茄蛋汤", cat: "soup", price: 1800, desc: "汤羹" },
  { id: "601", name: "桂花乌龙", cat: "drink", price: 1200, desc: "按杯", stock: 8 },
  { id: "701", name: "双人套餐", cat: "set", price: 12800, desc: "套餐 · 特价", tags: ["特价"] },
];
export const HOT = ["101", "301", "201", "302", "401", "601", "701", "102"];

export const TABLES: Table[] = [
  { id: "A01", zone: "hall", seats: 2, status: "free" },
  { id: "A02", zone: "hall", seats: 2, status: "dining", guests: 2, minutes: 52, amount: 9600 },
  { id: "A03", zone: "hall", seats: 4, status: "dining", guests: 4, minutes: 26, amount: 20350 },
  { id: "A04", zone: "hall", seats: 4, status: "free" },
  { id: "A05", zone: "hall", seats: 6, status: "dining", guests: 6, minutes: 41, amount: 27600 },
  { id: "A06", zone: "hall", seats: 4, status: "free" },
  { id: "A07", zone: "hall", seats: 4, status: "free" },
  { id: "A08", zone: "hall", seats: 4, status: "billing", guests: 3, minutes: 86, amount: 36800 },
  { id: "A09", zone: "hall", seats: 6, status: "reserved", note: "19:00 · 陈先生" },
  { id: "A10", zone: "hall", seats: 4, status: "free" },
  { id: "B01", zone: "room", seats: 10, status: "dining", guests: 8, minutes: 33, amount: 68800 },
  { id: "B02", zone: "room", seats: 10, status: "free" },
  { id: "B03", zone: "room", seats: 10, status: "billing", guests: 6, minutes: 95, amount: 51200 },
  { id: "T31", zone: "take", seats: 0, status: "dining", minutes: 6, amount: 2600, note: "外带 #1031" },
];

export const ORDER_LIST = [
  { no: "#1024", label: "A03", status: "进行中", items: 6, amount: 20350, time: "18:06" },
  { no: "#1021", label: "A02", status: "进行中", items: 4, amount: 9600, time: "17:40" },
  { no: "#1031", label: "外带", status: "挂起", items: 2, amount: 2600, time: "18:44" },
  { no: "#1018", label: "A08", status: "待结账", items: 7, amount: 36800, time: "17:06" },
  { no: "#1019", label: "A03", status: "已结账", items: 3, amount: 3720, time: "18:30" },
];

export function yuan(fen: number, keepZero = true): string {
  const v = fen / 100;
  return "¥" + (keepZero || fen % 100 ? v.toFixed(2) : String(v));
}
