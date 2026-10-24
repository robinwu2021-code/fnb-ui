import { computed, reactive } from "vue";
import { DISHES, type Dish } from "@/mock/data";

/** 当前订单（骨架只演示一张：A03）。待下单可加减，送厨后转为已下单。 */
export interface Line { uid: number; dishId: string; name: string; spec: string; unit: number; qty: number; status: "new" | "sent"; rush?: boolean; served?: boolean }

let uid = 1;
const line = (dishId: string, qty: number, status: Line["status"], extra: Partial<Line> = {}): Line => {
  const d = DISHES.find((x) => x.id === dishId) as Dish;
  return { uid: uid++, dishId, name: d.name, spec: "", unit: d.price, qty, status, ...extra };
};

export const order = reactive({
  no: "#1024",
  table: "A03",
  zone: "大厅",
  guests: 4,
  minutes: 26,
  member: false,
  roundOff: false,
  lines: [
    line("101", 1, "new", { spec: "大份 · 少糖", unit: 11800 }),
    line("401", 4, "new"),
    line("301", 1, "sent", { spec: "微辣", served: true }),
    line("201", 1, "sent"),
  ] as Line[],
});

export const pending = computed(() => order.lines.filter((l) => l.status === "new"));
export const sent = computed(() => order.lines.filter((l) => l.status === "sent"));
export const qtyOf = (dishId: string) => order.lines.filter((l) => l.dishId === dishId).reduce((n, l) => n + l.qty, 0);
export const qtyInCat = (cat: string) => order.lines.filter((l) => DISHES.find((d) => d.id === l.dishId)?.cat === cat).reduce((n, l) => n + l.qty, 0);

export const totals = computed(() => {
  const sub = order.lines.reduce((s, l) => s + l.unit * l.qty, 0);
  const service = Math.round(sub * 0.1);
  const member = order.member ? Math.round(sub * 0.05) : 0;
  const gross = sub + service - member;
  const round = order.roundOff ? gross % 100 : 0;
  return { sub, service, member, round, due: gross - round, items: order.lines.reduce((n, l) => n + l.qty, 0) };
});

export function addDish(d: Dish) {
  if (d.soldOut) return false;
  const hit = order.lines.find((l) => l.status === "new" && l.dishId === d.id && !l.spec);
  if (hit) hit.qty += 1;
  else order.lines.unshift(line(d.id, 1, "new"));
  return true;
}
export function changeQty(l: Line, delta: number) {
  l.qty += delta;
  if (l.qty <= 0) order.lines.splice(order.lines.indexOf(l), 1);
}
export function sendToKitchen() {
  const n = pending.value.length;
  order.lines.forEach((l) => { if (l.status === "new") l.status = "sent"; });
  return n;
}
