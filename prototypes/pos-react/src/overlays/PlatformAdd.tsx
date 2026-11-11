// 添加平台侧滑（方案 §2.4）：① 选平台 → ② 授权 → ③ 分类映射 → ④ 首次同步。授权与推送都是模拟（假延时）。
// Uber Eats 走 OAuth 跳转；Keeta、talabat 输入凭据并验证；未实现的平台只能录账号，不走 ③④。
import { useEffect, useRef, useState } from 'react';
import { useStore } from '@/store';
import { notify } from '@/store/actions';
import { DISHES } from '@/data/sample';
import { AUTO_MAP, firstSync, platDef, platsOf, REGIONS, type PlatKey } from '@/data/platforms';
import { useLang, useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Sheet } from '@/components/ui/sheet';
import { Steps } from '@/components/ui/steps';
import { Seg } from '@/components/ui/toggle-group';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { StatusTag } from '@/components/ui/badge';
import { OptionGroup, Option } from '@/components/ui/radio-cards';
import { Cells, Cell } from '@/components/ui/description-list';
import { Note } from '@/components/ui/feedback';
import { PlatLogo } from '@/components/ui/plat-logo';
import { MapTable } from '@/modules/settings/PlatformsPage';

const Spin = () => <Ico name="loader-circle" size={16} className="animate-spin" />;
type Auth = 'idle' | 'wait' | 'busy' | 'ok' | 'bad';
const STORE_IDS: Partial<Record<PlatKey, string>> = { uber: 'ue-7f3a9c21', keeta: 'KT-88213', talabat: '612094' };

export function PlatformAddSheet({ open }: { open: boolean }) {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const [step, setStep] = useState(1), [key, setKey] = useState<PlatKey | ''>('');
  const [auth, setAuth] = useState<Auth>('idle'), [cred, setCred] = useState<string[]>([]), [storeId, setStoreId] = useState('');
  const [map, setMap] = useState<Record<string, string>>({}), [scope, setScope] = useState<'all' | 'listed' | 'none'>('all');
  const [prog, setProg] = useState(-1); // -1 未开始 · 0..N 推送中 · N+ 完成
  const timers = useRef<number[]>([]);
  const later = (fn: () => void, ms: number) => { timers.current.push(window.setTimeout(fn, ms)); };
  const clear = () => { timers.current.forEach(clearTimeout); timers.current = []; };
  const p = key ? platDef(key) : undefined;
  const pick = (k: PlatKey) => { const d = platDef(k)!; setKey(k); setAuth('idle'); setCred(d.cred.map(() => '')); setStoreId(''); setMap({ ...AUTO_MAP }); setScope('all'); setProg(-1); };
  useEffect(() => {
    if (!open) { clear(); return; }
    const pre = s.paddKey && s.plats[s.paddKey].st === 'disconnected' ? s.paddKey : '';
    setStep(pre ? 2 : 1); if (pre) pick(pre); else { setKey(''); setProg(-1); }
  }, [open, s.paddKey]);
  const N = DISHES.length;
  const result = key ? firstSync(key, scope) : {};
  const cnt = (st: string) => Object.values(result).filter((x) => x === st).length;
  const startSync = () => { setProg(0); for (let i = 1; i <= N; i++) later(() => setProg(i), i * 110); };
  const finish = () => {
    if (!p || !key) return;
    const sync = { ...s.sync }; Object.keys(sync).forEach((n) => { sync[n] = { ...sync[n], [key]: result[n] || 'pending' }; });
    s.set({ sync, ov: 'none', psel: key, plats: { ...s.plats, [key]: { ...s.plats[key], st: 'connected', storeId, map, prep: 15, markup: 0, lastSync: scope === 'none' ? undefined : '12:30', menuPush: scope === 'none' ? {} : { all: '12:30' } } } });
    notify(useStore.getState, 'pa.connectedToast', { n: p.name });
  };
  const canNext = step === 1 ? !!p : step === 2 ? auth === 'ok' : true;
  const defs = platsOf(s.region);
  return (
    <Sheet open={open} onClose={() => dispatch('ov:none')} title={p && step > 1 ? t('pa.titleOf', { n: p.name }) : t('pa.title')} closeLabel={t('common.close')} className="w-540px">
      <Steps items={[t('pa.s1'), t('pa.s2'), t('pa.s3'), t('pa.s4')]} current={step} />
      {step === 1 && (
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-3"><span className="text-sm text-sub">{t('pf.region')}</span><Seg value={s.region} onChange={(v) => { dispatch('plat:region:' + v); setKey(''); }} items={REGIONS.map((r) => ({ v: r, label: t('pf.region.' + r) }))} /></div>
          <div className="flex flex-col gap-1.5">
            {defs.map((d) => { const st = s.plats[d.key].st, on = key === d.key, done = st !== 'disconnected'; return (
              <button key={d.key} type="button" disabled={done} onClick={() => pick(d.key)} aria-pressed={on}
                className={cn('flex items-center gap-3 px-3 py-2.5 rounded-rt text-left', on ? 'sel' : 'bg-wash', done ? 'opacity-60 cursor-default' : !on && 'hover:bg-wash-strong')}>
                <PlatLogo k={d.key} size={32} className={cn(!d.impl && 'opacity-70')} />
                <span className="flex flex-col leading-tight flex-1 min-w-0"><b className="font-600">{d.name}</b><small className="text-xs text-sub font-400">{d.impl ? t(d.auth === 'oauth' ? 'pf.authOauth' : 'pf.authCred') : t('pa.todoShort')}</small></span>
                {done ? <StatusTag ent="device" k="online" label={t('md.connected')} form="dot" /> : !d.impl ? <StatusTag ent="feature" k="todo" form="dot" /> : on ? <Ico name="circle-check" size={18} /> : null}
              </button>); })}
          </div>
        </div>
      )}
      {step === 2 && p && (!p.impl ? (
        <div className="flex flex-col gap-3"><Note>{t('pf.todoDesc', { n: p.name })}</Note>
          {p.cred.map(([lk, ph], i) => <label key={lk} className="flex flex-col gap-1.5"><span className="gt"><span>{t(lk)}</span></span><Input value={cred[i] || ''} placeholder={ph} onChange={(e) => setCred((x) => x.map((y, j) => (j === i ? e.target.value : y)))} /></label>)}
        </div>
      ) : p.auth === 'oauth' ? (
        <div className="flex flex-col gap-3">
          <span className="help">{t('pa.oauthHint', { n: p.name })}</span>
          <div className="rounded-rt bg-wash p-4 flex flex-col items-center gap-3 text-center">
            <PlatLogo k={p.key} size={48} />
            {auth === 'idle' && <Button onClick={() => setAuth('wait')}><Ico name="external-link" size={16} />{t('pa.openAuth', { n: p.name })}</Button>}
            {auth === 'wait' && <><span className="inline-flex items-center gap-1.5 text-sm text-sub"><Spin />{t('pa.waiting')}</span><div className="flex gap-gap"><Button variant="soft" onClick={() => { setAuth('ok'); setStoreId(STORE_IDS[p.key] || ''); }}>{t('pa.simAuth')}</Button><Button variant="muted" onClick={() => setAuth('idle')}>{t('common.cancel')}</Button></div></>}
            {auth === 'ok' && <StatusTag ent="device" k="online" label={t('pa.authed', { id: STORE_IDS[p.key] || '' })} />}
          </div>
          {auth === 'ok' && <Cells className="bg-wash rounded-rk px-pad text-sm [&>div]:min-h-10"><Cell k={t('pa.storeName')} v={t('pa.storeNameV')} /><Cell k={t('pf.storeId')} v={<span className="num">{storeId}</span>} /><Cell k={t('pa.scopes')} v={t('pa.scopesV')} /></Cells>}
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <span className="help">{t('pa.credHint', { n: p.name })}</span>
          {p.cred.map(([lk, ph], i) => <label key={lk} className="flex flex-col gap-1.5"><span className="gt"><span>{t(lk)}</span></span><Input value={cred[i] || ''} placeholder={ph} err={auth === 'bad' && !cred[i]?.trim()} onChange={(e) => { setCred((x) => x.map((y, j) => (j === i ? e.target.value : y))); if (auth !== 'busy') setAuth('idle'); }} /></label>)}
          <div className="flex items-center gap-3 flex-wrap">
            <Button variant={auth === 'ok' ? 'muted' : 'soft'} disabled={auth === 'busy'} onClick={() => { if (cred.some((x) => !x.trim())) { setAuth('bad'); return; } setAuth('busy'); later(() => { setAuth('ok'); setStoreId(cred[0].trim()); }, 1000); }}>{auth === 'busy' ? <Spin /> : <Ico name="shield-check" size={16} />}{t('pa.verify')}</Button>
            {auth === 'busy' && <span className="text-sm text-sub">{t('pa.verifying')}</span>}
            {auth === 'ok' && <StatusTag ent="device" k="online" label={t('pa.credOk', { id: storeId })} />}
            {auth === 'bad' && <span className="text-sm text-danger-text">{t('pa.credBad')}</span>}
          </div>
        </div>
      ))}
      {step === 3 && p && (
        <div className="flex flex-col gap-3">
          <span className="help">{t('pa.mapHint')}</span>
          <MapTable k={p.key} map={map} auto={AUTO_MAP} onPick={(c, v) => setMap((m) => ({ ...m, [c]: v }))} />
          {p.rules.length > 0 && <Note>{p.rules.map((r) => (lang === 'en' ? r[1] : r[0])).join('；')}</Note>}
        </div>
      )}
      {step === 4 && p && (
        <div className="flex flex-col gap-3">
          {prog < 0 && <OptionGroup value={scope} onChange={setScope} className="flex-col items-stretch">
            {(['all', 'listed', 'none'] as const).map((v) => <Option key={v} value={v} label={t('pa.scope.' + v)} sub={t('pa.scopeD.' + v)} className="w-full" />)}
          </OptionGroup>}
          {prog >= 0 && <div className="flex flex-col gap-2">
            <div className="h-2 rounded-full bg-wash-strong overflow-hidden"><i className="block h-full bg-primary transition-[width] duration-100" style={{ width: (prog / N) * 100 + '%' }} /></div>
            {prog < N ? <span className="inline-flex items-center gap-1.5 text-sm text-sub"><Spin />{t('pa.pushing', { a: prog, b: N, n: p.name })}</span>
              : <><span className="flex flex-wrap gap-2"><StatusTag ent="sync" k="synced" label={t('md.syncedN', { n: cnt('synced') })} /><StatusTag ent="sync" k="failed" label={t('md.failedN', { n: cnt('failed') })} /><StatusTag ent="sync" k="unlisted" label={t('pa.unlistedN', { n: cnt('unlisted') })} /></span>
                {cnt('failed') > 0 && <Note tone="warn">{t('pa.failNote')}</Note>}</>}
          </div>}
        </div>
      )}
      <div className="mt-auto pt-3 flex gap-gap">
        {step > 1 && prog < 0 && <Button variant="muted" onClick={() => { clear(); setStep(step - 1); }}>{t('dv.a.back')}</Button>}
        {step === 2 && p && !p.impl ? <Button block disabled={cred.some((x) => !x.trim())} onClick={() => { dispatch('plat:cred:' + p.key); dispatch('ov:none'); }}>{t('pf.saveCred')}</Button>
          : step < 4 ? <Button block disabled={!canNext} onClick={() => setStep(step + 1)}>{t('dv.a.next')}</Button>
          : scope === 'none' || prog >= N ? <Button block onClick={finish}>{t('pa.finish')}</Button>
          : <Button block disabled={prog >= 0} onClick={startSync}>{prog >= 0 ? <Spin /> : <Ico name="upload" size={16} />}{t('pa.start')}</Button>}
      </div>
    </Sheet>
  );
}
