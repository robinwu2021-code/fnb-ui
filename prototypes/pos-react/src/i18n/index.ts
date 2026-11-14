import { DICT } from './dict';
import { useStore } from '@/store';
import { cur } from '@/lib/money';
import { useBiz, type BizKey } from '@/biz/store';
import { BIZ_DICT } from '@/biz/dict';
export type Lang = 'zh' | 'en';
/** biz：业态，先查业态覆盖词条（biz/dict.ts），查不到用主词表 */
export function tr(lang: Lang, key: string, params?: Record<string, string | number>, biz: BizKey = 'fnb'): string {
  const e = BIZ_DICT[biz]?.[key] ?? DICT[key];
  let s = cur(e ? e[lang === 'en' ? 1 : 0] : key); // 词条里的 ¥ 换成当前地区币种
  if (params) for (const k in params) s = s.split('{' + k + '}').join(String(params[k]));
  return s;
}
/** 组件里用：const t = useT(); t('tab.tables') */
export function useT() {
  const lang = useStore((s) => s.lang), biz = useBiz((s) => s.biz);
  return (key: string, params?: Record<string, string | number>) => tr(lang, key, params, biz);
}
export function useLang() { return useStore((s) => s.lang); }
