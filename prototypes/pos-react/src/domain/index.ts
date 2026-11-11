// 领域服务层（架构文档 §二）：纯 TS，不依赖 React / zustand / 示例数据 / 界面词表；主 POS 和副机跑同一份。
// 界面和 store 只调这里的函数，业态差异通过参数（计价规则 FNB_RULES / RETAIL_RULES）传入。
export * as pricing from './pricing';
export * as order from './order';
export * as cart from './cart';
export * as payment from './payment';
export * as catalog from './catalog';
export * as table from './table';
export * as member from './member';
export * as aftersale from './aftersale';
export * as shift from './shift';
export * as stock from './stock';
export * as events from './events';
export { round2, money } from './money';
