// 领域事件（架构文档 §二 依赖规则 3）：业务模块之间不直接改对方的数据，发事件、各自订阅。
// 例：订单服务发「已结账」，桌台服务订阅后把桌台改成待清台；零售没有桌台服务，同一个事件没人改桌台。
export type DomainEvent =
  | { type: 'order.paid'; biz: 'fnb' | 'retail'; orderNo: string; due: number; table?: string; methods: string[] }
  | { type: 'order.held'; biz: 'fnb' | 'retail'; orderNo: string }
  /** delta：每个商品的变化量（销售为负、退货回库为正）；reason=loss 表示退货不回库，要生成报损单 */
  | { type: 'stock.changed'; ids: string[]; delta?: Record<string, number>; reason?: 'sale' | 'return' | 'loss'; ref?: string }
  /** 换货：退回部分已记到原单，抵扣额带到收银（零售收银订阅后在购物车里出抵扣行） */
  | { type: 'exchange.credit'; orderNo: string; amt: number; pcs: number; method: string };
type Of<T extends DomainEvent['type']> = Extract<DomainEvent, { type: T }>;
const subs: Record<string, ((e: DomainEvent) => void)[]> = {};
/** 订阅；返回取消函数 */
export function on<T extends DomainEvent['type']>(type: T, fn: (e: Of<T>) => void) { const f = fn as (e: DomainEvent) => void; (subs[type] ||= []).push(f); return () => { subs[type] = subs[type].filter((x) => x !== f); }; }
export function emit(e: DomainEvent) { (subs[e.type] || []).forEach((fn) => fn(e)); }
