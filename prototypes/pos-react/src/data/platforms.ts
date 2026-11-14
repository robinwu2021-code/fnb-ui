// 外送平台（方案 §2）：本期只做台湾、阿联酋。台湾首要 Uber Eats；阿联酋 Keeta + talabat。其余平台卡片可见，标「未实现」，可先录账号。
// 品牌色只用在徽标一个字的小方块上（规范 §3.4 例外），不染面板。
import { DISHES } from './sample';

export type Region = 'tw' | 'uae';
export const REGIONS: Region[] = ['tw', 'uae'];
export type PlatKey = 'uber' | 'panda' | 'keeta' | 'talabat' | 'deliveroo' | 'noon' | 'careem';
export interface PlatDef {
  key: PlatKey; name: string; region: Region; ch: string; bg: string; fg: string; impl: boolean;
  auth: 'oauth' | 'cred'; cred: [string, string][]; // [词表 key, 占位]
  menuMode: 'full' | 'delta'; pause: 'until' | 'reason' | 'plain';
  rules: [string, string][];
}
export const PLAT_DEFS: PlatDef[] = [
  { key: 'uber', name: 'Uber Eats', region: 'tw', ch: 'U', bg: '#06C167', fg: '#000000', impl: true, auth: 'oauth', cred: [], menuMode: 'full', pause: 'until',
    rules: [['菜名必填英文', 'English name required'], ['每道菜要有图片，≥ 1200×800', 'Every dish needs an image ≥ 1200×800'], ['菜单整份覆盖推送，推一次替换全部', 'Each push replaces the whole menu'], ['暂停接单可设到某个时间自动恢复', 'Pauses can resume automatically at a set time']] },
  { key: 'panda', name: 'foodpanda', region: 'tw', ch: 'p', bg: '#D70F64', fg: '#FFFFFF', impl: false, auth: 'cred', cred: [['pa.f.vendor', 'e.g. t7xk'], ['pa.f.apiKey', '••••••••']], menuMode: 'delta', pause: 'plain', rules: [] },
  { key: 'keeta', name: 'Keeta', region: 'uae', ch: 'K', bg: '#FFD100', fg: '#111111', impl: true, auth: 'cred', cred: [['pa.f.storeId', 'e.g. KT-88213'], ['pa.f.apiKey', '••••••••']], menuMode: 'delta', pause: 'plain',
    rules: [['菜名需要英文 + 阿拉伯文', 'English and Arabic names required'], ['价格按含 5% VAT 填写', 'Prices include 5% VAT'], ['按菜品增量推送', 'Pushed per dish']] },
  { key: 'talabat', name: 'talabat', region: 'uae', ch: 't', bg: '#FF5A00', fg: '#FFFFFF', impl: true, auth: 'cred', cred: [['pa.f.vendor', 'e.g. 612094'], ['pa.f.chainKey', '••••••••']], menuMode: 'delta', pause: 'reason',
    rules: [['不支持称重菜', 'Weighed items not supported'], ['套餐要在 talabat 后台建成组合商品', 'Sets must be built as combos in talabat'], ['暂停接单必须填原因', 'Pausing requires a reason'], ['价格不能低于平台最低售价', 'Prices must meet the platform minimum']] },
  { key: 'deliveroo', name: 'Deliveroo', region: 'uae', ch: 'd', bg: '#00CCBC', fg: '#FFFFFF', impl: false, auth: 'cred', cred: [['pa.f.storeId', ''], ['pa.f.apiKey', '••••••••']], menuMode: 'delta', pause: 'plain', rules: [] },
  { key: 'noon', name: 'Noon Food', region: 'uae', ch: 'n', bg: '#FEEE00', fg: '#111111', impl: false, auth: 'cred', cred: [['pa.f.storeId', ''], ['pa.f.apiKey', '••••••••']], menuMode: 'delta', pause: 'plain', rules: [] },
  { key: 'careem', name: 'Careem Food', region: 'uae', ch: 'c', bg: '#4BB543', fg: '#FFFFFF', impl: false, auth: 'cred', cred: [['pa.f.storeId', ''], ['pa.f.apiKey', '••••••••']], menuMode: 'delta', pause: 'plain', rules: [] },
];
export const platDef = (k: string) => PLAT_DEFS.find((p) => p.key === k);
export const platsOf = (r: Region) => PLAT_DEFS.filter((p) => p.region === r);

export type PlatSt = 'connected' | 'paused' | 'disconnected';
export interface PlatState { hours?: string; st: PlatSt; storeId?: string; prep: number; markup: number; lastSync?: string; pausedUntil?: string; pauseWhy?: string; map: Record<string, string>; credSaved?: boolean; menuPush: Record<string, string> }
/** 平台类目（平台侧的名字，按平台语言显示英文） */
export const PLAT_CATS = ['Popular', 'Signature', 'Appetizers', 'Mains', 'Rice & Noodles', 'Soups', 'Drinks', 'Desserts', 'Combos'];
/** 按名称自动匹配：本店分类 → 平台类目；套餐默认不匹配（要在平台后台建组合） */
export const AUTO_MAP: Record<string, string> = { 招牌: 'Signature', 凉菜: 'Appetizers', 热菜: 'Mains', 主食: 'Rice & Noodles', 汤羹: 'Soups', 饮品: 'Drinks', 甜品: 'Desserts' };
export const MENUS: [string, string, string, string][] = [['all', '全天菜单', 'All day', '10:00–22:00'], ['lunch', '午市套餐', 'Lunch sets', '11:00–14:00'], ['dinner', '晚市', 'Dinner', '17:00–22:00']];

const blank = (): PlatState => ({ st: 'disconnected', prep: 15, markup: 0, map: {}, menuPush: {} });
export const INITIAL_PLATS: Record<PlatKey, PlatState> = {
  uber: { st: 'connected', storeId: 'ue-7f3a9c21', prep: 15, markup: 15, lastSync: '12:28', map: { ...AUTO_MAP, 主食: '' }, menuPush: { all: '12:28', lunch: '10:05' } },
  panda: blank(),
  keeta: { st: 'connected', hours: '10:00–21:30', storeId: 'KT-88213', prep: 20, markup: 10, lastSync: '12:26', map: { ...AUTO_MAP }, menuPush: { all: '12:26', dinner: 'yesterday' } },
  talabat: blank(), deliveroo: blank(), noon: blank(), careem: blank(),
};

/** 菜品 × 渠道同步状态。dine / mini 是本店渠道，其余键 = 平台 key（只在连接后出现） */
export type SyncMap = Record<string, Record<string, string>>;
export const INITIAL_SYNC: SyncMap = {};
DISHES.forEach((d, i) => {
  INITIAL_SYNC[d.n] = {
    dine: 'synced', mini: 'synced',
    uber: d.c === '套餐' ? 'unlisted' : i % 5 === 3 ? 'failed' : i % 4 === 1 ? 'pending' : 'synced',
    keeta: d.c === '套餐' ? 'unlisted' : i === 9 ? 'failed' : i % 6 === 2 ? 'pending' : 'synced',
  };
});
/** 首次连接后某平台的初始同步结果（模拟） */
export function firstSync(plat: PlatKey, scope: 'all' | 'listed' | 'none'): Record<string, string> {
  const r: Record<string, string> = {};
  DISHES.forEach((d) => { r[d.n] = scope === 'none' ? 'pending' : d.c === '套餐' ? 'unlisted' : SYNCWHY[plat]?.[d.n] ? 'failed' : scope === 'listed' && d.s === 0 ? 'unlisted' : 'synced'; });
  return r;
}
/** 失败原因：按平台 × 菜品 */
export const SYNCWHY: Partial<Record<PlatKey, Record<string, [string, string]>>> = {
  uber: { 蒜蓉西兰花: ['没有图片：Uber Eats 要求每道菜有图，≥ 1200×800', 'No image: Uber Eats needs one ≥ 1200×800'], 米饭: ['分类「主食」没有映射到 Uber Eats 类目', 'Category "Staples" is not mapped'], 鲜榨橙汁: ['没有图片：Uber Eats 要求每道菜有图', 'No image: Uber Eats needs one'] },
  keeta: { 担担面: ['缺少阿拉伯文菜名', 'Arabic name missing'] },
  talabat: { 担担面: ['低于平台最低售价 25，当前 22', 'Below platform minimum 25, now 22'], 拍黄瓜: ['图片小于 600×450', 'Image smaller than 600×450'] },
};
export const STORE_ROWS = ['status', 'hours', 'prep'] as const;
