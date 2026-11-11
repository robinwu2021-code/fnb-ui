// 领域事件订阅（架构文档 §二 依赖规则 3）：各业务服务在这里订阅别的模块发的事件，改自己的数据。
// 桌台服务只在餐饮装载：零售结账也发 order.paid，但没有 table，这里不处理。
import { on } from '@/domain/events';
import { setTable } from '@/domain/table';
import type { PosState } from './index';

export function wireDomainEvents(get: () => PosState, set: (p: Partial<PosState>) => void) {
  /* 桌台服务：结账完成 → 该桌待清台 */
  on('order.paid', (e) => { if (e.biz === 'fnb' && e.table) set({ tables: setTable(get().tables, e.table, 'to_clear') }); });
}
