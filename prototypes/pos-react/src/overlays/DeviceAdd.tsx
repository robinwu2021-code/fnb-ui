// 添加设备侧滑（方案 §1.2 / §1.5）：① 类型 → ② 连接 → ③ 命名与用途。硬件全是模拟（假延时），界面和失败分支照画。
// 打印机三路：蓝牙扫描取 IP（再按 IP 连）· 直接填 IP · 扫配置码（自动填 IP 并测试）
// 副 POS / 厨房屏两路：附近设备（局域网广播）· 扫码配对；客显选视频输出；秤 / 扫码枪选端口；叫号屏「未实现」，可先登记。
// 从设备详情「修改连接」进来时（daddEdit）直接到第 ② 步，保存后更新原设备。
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useStore } from '@/store';
import { notify } from '@/store/actions';
import { BLE_HITS, DEMO_QR, DISPLAY_OUTS, KDS_STATIONS, KINDS, PRINTER_USES, PROTOS, SCALE_PROTOS, SERIAL_PORTS, ipReachable, ipValid, kindIcon, parseQr, type BleHit, type Device, type DeviceKind, type Proto, type Via } from '@/data/devices';
import { useLang, useT } from '@/i18n';
import { cn } from '@/lib/cn';
import { Ico } from '@/lib/icon';
import { Sheet } from '@/components/ui/sheet';
import { Steps } from '@/components/ui/steps';
import { Seg } from '@/components/ui/toggle-group';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Filter } from '@/components/ui/toggle';
import { Switch } from '@/components/ui/switch';
import { StatusTag } from '@/components/ui/badge';
import { Note, Empty } from '@/components/ui/feedback';
import { Cells, Cell } from '@/components/ui/description-list';

type Draft = { ip?: string; port?: number; proto?: Proto; model?: string; conn?: string; via: Via; pairId?: string };
type Conn = 'idle' | 'busy' | 'ok' | 'fail';
const Gt = ({ children, right }: { children: ReactNode; right?: ReactNode }) => <span className="gt"><span>{children}</span>{right}</span>;
const Spin = () => <Ico name="loader-circle" size={16} className="animate-spin" />;

export function DeviceAddSheet({ open }: { open: boolean }) {
  const t = useT(), lang = useLang(); const s = useStore(), dispatch = s.act;
  const edit = s.devices.find((d) => d.id === s.daddEdit);
  const [step, setStep] = useState(1), [kind, setKind] = useState<DeviceKind>('printer');
  const [tab, setTab] = useState('ble'), [draft, setDraft] = useState<Draft | null>(null);
  const [name, setName] = useState(''), [use, setUse] = useState<string[]>([]), [drawer, setDrawer] = useState(false), [nameErr, setNameErr] = useState(false);
  const timers = useRef<number[]>([]);
  const later = (fn: () => void, ms: number) => { timers.current.push(window.setTimeout(fn, ms)); };
  const clearTimers = () => { timers.current.forEach(clearTimeout); timers.current = []; };
  // 每次打开重置；预选类型或修改连接时跳过第 ① 步
  useEffect(() => {
    if (!open) { clearTimers(); return; }
    const k = (edit?.kind || s.daddKind || 'printer') as DeviceKind;
    setKind(k); setStep(edit || s.daddKind ? 2 : 1); setTab(edit ? 'ip' : defaultTab(k)); setDraft(null);
    setName(edit ? (lang === 'en' ? edit.en : edit.name) : ''); setUse(edit?.use || (k === 'printer' ? ['kitchen'] : k === 'kds' ? ['hot'] : [])); setDrawer(!!edit?.drawer); setNameErr(false);
  }, [open, s.daddEdit, s.daddKind]);
  const pickKind = (k: DeviceKind) => { setKind(k); setTab(defaultTab(k)); setDraft(null); setUse(k === 'printer' ? ['kitchen'] : k === 'kds' ? ['hot'] : []); };
  const close = () => dispatch('ov:none');
  const finish = () => {
    if (!name.trim()) { setNameErr(true); return; }
    if (!draft && kind !== 'pickup') return;
    const base: Partial<Device> = { ...(draft ? { ip: draft.ip, port: draft.port, proto: draft.proto, conn: draft.conn, via: draft.via, model: draft.model } : { via: 'mdns' }), use: use.length ? use : undefined, drawer: kind === 'printer' ? drawer : undefined };
    const nm = lang === 'en' ? { en: name.trim() } : { name: name.trim() };
    if (edit) {
      s.set((st) => ({ devices: st.devices.map((d) => (d.id === edit.id ? { ...d, ...base, ...nm, st: 'online' } : d)), ov: 'none', dsel: edit.id }));
      notify(useStore.getState, 'dv.updated', { n: name.trim() }); return;
    }
    if (draft?.pairId) { // 附近设备配对：把那台「新发现」转成已添加
      s.set((st) => ({ devices: st.devices.map((d) => (d.id === draft.pairId ? { ...d, ...base, name: name.trim(), en: name.trim(), ...nm, st: 'online', addedAt: lang === 'en' ? 'Today' : '今天' } : d)), ov: 'none', dsel: draft.pairId, dkind: 'all' }));
    } else {
      const id = kind + '-' + Date.now().toString(36);
      const dev: Device = { id, kind, name: name.trim(), en: name.trim(), ...nm, ...base, st: kind === 'pickup' ? 'unplugged' : 'online', todo: kind === 'pickup' || undefined, addedAt: lang === 'en' ? 'Today' : '今天' } as Device;
      s.set((st) => ({ devices: [...st.devices.filter((d) => d.st !== 'found'), dev, ...st.devices.filter((d) => d.st === 'found')], ov: 'none', dsel: id, dkind: 'all' }));
    }
    notify(useStore.getState, 'dv.added', { n: name.trim() });
  };
  const canNext = step === 1 || (step === 2 && (!!draft || kind === 'pickup'));
  const title = edit ? t('dv.a.titleEdit', { n: lang === 'en' ? edit.en : edit.name }) : t('dv.a.title');
  return (
    <Sheet open={open} onClose={close} title={title} closeLabel={t('common.close')} className="w-520px">
      <Steps items={[t('dv.a.s1'), t('dv.a.s2'), t('dv.a.s3')]} current={step} />
      {step === 1 && (
        <div className="grid grid-cols-2 gap-gap">
          {KINDS.map(([k]) => (
            <button key={k} type="button" onClick={() => pickKind(k)} aria-pressed={kind === k}
              className={cn('flex items-center gap-3 p-3 rounded-rt text-left', kind === k ? 'sel' : 'bg-wash hover:bg-wash-strong')}>
              <span className={cn('inline-grid place-items-center w-10 h-10 rounded-rk flex-none', kind === k ? 'bg-surface' : 'bg-surface')}><Ico name={kindIcon(k)} size={20} /></span>
              <span className="flex flex-col leading-tight min-w-0"><b className="font-600">{t('dv.kind.' + k)}</b><small className="text-xs text-sub font-400">{t('dv.kd.' + k)}</small></span>
              {k === 'pickup' && <StatusTag ent="feature" k="todo" form="dot" className="ml-auto" />}
            </button>
          ))}
        </div>
      )}
      {step === 2 && (
        <ConnectStep kind={kind} tab={tab} setTab={setTab} draft={draft} setDraft={setDraft} later={later} clearTimers={clearTimers} edit={edit}
          onPaired={(d) => { setName(lang === 'en' ? d.en : d.name); }} />
      )}
      {step === 3 && (
        <div className="flex flex-col gap-3">
          {draft && <Cells className="bg-wash rounded-rk px-pad text-sm [&>div]:min-h-10"><Cell k={t('dv.a.summary')}><span className="inline-flex items-center gap-2"><span className="num">{draft.ip ? draft.ip + (draft.port ? ':' + draft.port : '') : draft.conn}</span><StatusTag ent="device" k="online" form="dot" /></span></Cell>{draft.proto && <Cell k={t('dv.d.proto')} v={t('dv.proto.' + draft.proto)} />}<Cell k={t('dv.d.via')} v={t('dv.via.' + draft.via)} />{draft.model && <Cell k={t('dv.d.model')} v={draft.model} />}</Cells>}
          {kind === 'pickup' && <Note>{t('dv.todoDesc')}</Note>}
          <label className="flex flex-col gap-1.5"><Gt>{t('dv.a.name')}</Gt><Input value={name} err={nameErr} onChange={(e) => { setName(e.target.value); setNameErr(false); }} placeholder={t('dv.a.namePh')} />{nameErr && <small className="text-xs text-danger-text">{t('dv.a.needName')}</small>}</label>
          {(kind === 'printer' || kind === 'kds') && (
            <div className="flex flex-col gap-1.5"><Gt>{kind === 'printer' ? t('dv.a.use') : t('dv.a.stations')}</Gt>
              <div className="flex flex-wrap gap-gap">{(kind === 'printer' ? PRINTER_USES : KDS_STATIONS).map((u) => <Filter key={u} pressed={use.includes(u)} onPressedChange={() => setUse((x) => (x.includes(u) ? x.filter((y) => y !== u) : [...x, u]))}>{t((kind === 'printer' ? 'dv.use.' : 'dv.stn.') + u)}</Filter>)}</div></div>
          )}
          {kind === 'printer' && <Cells><Cell k={t('dv.a.drawer')} strong><Switch checked={drawer} onCheckedChange={setDrawer} aria-label={t('dv.a.drawer')} /></Cell></Cells>}
        </div>
      )}
      <div className="mt-auto pt-3 flex gap-gap">
        {step > 1 && !(edit && step === 2) && <Button variant="muted" onClick={() => { clearTimers(); setStep(step - 1); }}>{t('dv.a.back')}</Button>}
        {step < 3 ? <Button block disabled={!canNext} onClick={() => { clearTimers(); setStep(step + 1); if (step === 2 && !name && draft?.model) setName(draft.model); }}>{t('dv.a.next')}</Button>
          : <Button block onClick={finish}>{edit ? t('dv.a.save') : t('dv.a.done')}</Button>}
      </div>
    </Sheet>
  );
}
const defaultTab = (k: DeviceKind) => (k === 'printer' ? 'ble' : k === 'pos' || k === 'kds' ? 'nearby' : 'port');

/* ── 第 ② 步：按类型分流 ── */
function ConnectStep({ kind, tab, setTab, draft, setDraft, later, clearTimers, edit, onPaired }: { kind: DeviceKind; tab: string; setTab: (v: string) => void; draft: Draft | null; setDraft: (d: Draft | null) => void; later: (fn: () => void, ms: number) => void; clearTimers: () => void; edit?: Device; onPaired: (d: Device) => void }) {
  const t = useT();
  const switchTab = (v: string) => { clearTimers(); setDraft(null); setTab(v); };
  if (kind === 'printer') return (
    <div className="flex flex-col gap-3">
      <Seg value={tab} onChange={switchTab} items={[{ v: 'ble', label: <><Ico name="bluetooth" size={15} />{t('dv.a.ble')}</> }, { v: 'ip', label: <><Ico name="network" size={15} />{t('dv.a.ip')}</> }, { v: 'qr', label: <><Ico name="scan-qr-code" size={15} />{t('dv.a.qr')}</> }]} />
      {tab === 'ble' && <BleTab later={later} setDraft={setDraft} toIp={() => switchTab('ip')} />}
      {tab === 'ip' && <IpTab later={later} setDraft={setDraft} init={edit ? { ip: edit.ip || '', port: edit.port || 9100, proto: edit.proto || 'escpos' } : undefined} />}
      {tab === 'qr' && <QrTab later={later} setDraft={setDraft} />}
    </div>
  );
  if (kind === 'pos' || kind === 'kds') return (
    <div className="flex flex-col gap-3">
      <Seg value={tab} onChange={switchTab} items={[{ v: 'nearby', label: <><Ico name="radar" size={15} />{t('dv.a.nearby')}</> }, { v: 'pairqr', label: <><Ico name="scan-qr-code" size={15} />{t('dv.a.pairQr')}</> }]} />
      {tab === 'nearby' ? <NearbyTab kind={kind} later={later} draft={draft} setDraft={setDraft} onPaired={onPaired} /> : <PairQrTab kind={kind} later={later} draft={draft} setDraft={setDraft} />}
    </div>
  );
  if (kind === 'cds') return <PortList title={t('dv.a.outs')} items={DISPLAY_OUTS.map((o) => ({ id: o.id, label: o.label, sub: o.model }))} via="hdmi" draft={draft} setDraft={setDraft} />;
  if (kind === 'scale' || kind === 'scanner') return <PortList title={t('dv.a.ports')} items={SERIAL_PORTS.filter((p) => (kind === 'scale' ? !p.id.startsWith('USB') : p.id.startsWith('USB'))).map((p) => ({ id: p.id, label: p.label }))} via={kind === 'scale' ? 'serial' : 'usb'} draft={draft} setDraft={setDraft} scale={kind === 'scale'} />;
  return <div className="rounded-rt bg-wash"><Empty icon={<Ico name="construction" size={40} className="text-sub" />} title={t('dv.a.todo')} desc={t('dv.todoDesc')} /></div>;
}

/* 打印机 A：蓝牙扫描 → 选中即通过蓝牙读 IP → 用 IP 连接 */
function BleTab({ later, setDraft, toIp }: { later: (fn: () => void, ms: number) => void; setDraft: (d: Draft | null) => void; toIp: () => void }) {
  const t = useT();
  const [phase, setPhase] = useState<'idle' | 'scan' | 'done'>('idle'), [hits, setHits] = useState<BleHit[]>([]);
  const [sel, setSel] = useState(''), [read, setRead] = useState<'idle' | 'busy' | 'got' | 'none'>('idle'), [conn, setConn] = useState<Conn>('idle');
  const scan = () => { setPhase('scan'); setHits([]); setSel(''); setRead('idle'); setConn('idle'); setDraft(null); BLE_HITS.forEach((h, i) => later(() => setHits((x) => (x.some((y) => y.mac === h.mac) ? x : [...x, h])), 400 + i * 350)); later(() => setPhase('done'), 400 + BLE_HITS.length * 350); };
  useEffect(() => { scan(); }, []);
  const pick = (h: BleHit) => { setSel(h.mac); setConn('idle'); setDraft(null); setRead('busy'); later(() => setRead(h.ip ? 'got' : 'none'), 900); };
  const hit = hits.find((h) => h.mac === sel);
  const connect = () => { if (!hit?.ip) return; setConn('busy'); later(() => { const ok = hit.net !== false && ipReachable(hit.ip!); setConn(ok ? 'ok' : 'fail'); if (ok) setDraft({ ip: hit.ip, port: 9100, proto: hit.proto, model: hit.model, via: 'ble' }); }, 1200); };
  const bars = (r: number) => (r > -55 ? 'signal-high' : r > -70 ? 'signal-medium' : 'signal-low');
  return (
    <div className="flex flex-col gap-2.5">
      <span className="help">{t('dv.a.bleHint')}</span>
      <Gt right={<Button variant="link" className="hit text-sm" disabled={phase === 'scan'} onClick={scan}>{t('dv.a.rescan')}</Button>}>
        <span className="inline-flex items-center gap-1.5">{phase === 'scan' ? <><Spin />{t('dv.a.scanning')}</> : t('dv.a.found', { n: hits.length })}</span>
      </Gt>
      <div className="flex flex-col gap-1">
        {hits.map((h) => (
          <div key={h.mac} className={cn('rounded-rt', sel === h.mac ? 'bg-sel-bg' : 'bg-wash')}>
            <button type="button" onClick={() => pick(h)} className="w-full flex items-center gap-3 px-3 py-2 text-left anim-fade">
              <Ico name="printer" size={18} className="text-sub" />
              <span className="flex flex-col leading-tight flex-1 min-w-0"><b className="font-600">{h.name}</b><small className="text-xs text-sub num">{h.mac}</small></span>
              <span className="inline-flex items-center gap-1 text-xs text-sub num"><Ico name={bars(h.rssi)} size={16} />{h.rssi} dBm</span>
            </button>
            {sel === h.mac && (
              <div className="px-3 pb-3 flex flex-col gap-2 text-sm">
                {read === 'busy' && <span className="inline-flex items-center gap-1.5 text-sub"><Spin />{t('dv.a.reading')}</span>}
                {read === 'none' && <><Note tone="warn">{t('dv.a.noIp')}</Note><div className="flex gap-gap"><Button variant="muted" size="sm" onClick={() => pick(h)}>{t('dv.a.readIp')}</Button><Button variant="link" className="hit text-sm" onClick={toIp}>{t('dv.a.useIp')}</Button></div></>}
                {read === 'got' && <>
                  <span className="inline-flex items-center gap-1.5"><Ico name="circle-check" size={16} className="text-success-text" /><span className="num">{t('dv.a.gotIp', { ip: h.ip!, p: t('dv.proto.' + (h.proto || 'escpos')), port: 9100 })}</span></span>
                  {conn === 'idle' && <div><Button size="sm" onClick={connect}><Ico name="plug-zap" size={16} />{t('dv.a.connect')}</Button></div>}
                  {conn === 'busy' && <span className="inline-flex items-center gap-1.5 text-sub"><Spin />{t('dv.a.connecting', { ip: h.ip! })}</span>}
                  {conn === 'ok' && <StatusTag ent="device" k="online" label={t('dv.a.connected', { m: h.model || h.name })} />}
                  {conn === 'fail' && <><Note tone="bad">{t('dv.a.timeout', { ip: h.ip! })}</Note><div className="flex gap-gap"><Button variant="muted" size="sm" onClick={connect}>{t('dv.a.connect')}</Button><Button variant="link" className="hit text-sm" onClick={toIp}>{t('dv.a.useIp')}</Button></div></>}
                </>}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

/* 打印机 B：直接填 IP · 端口 · 协议 → 测试连接 */
function IpTab({ later, setDraft, init, auto }: { later: (fn: () => void, ms: number) => void; setDraft: (d: Draft | null) => void; init?: { ip: string; port: number; proto: Proto; via?: Via; model?: string }; auto?: boolean }) {
  const t = useT();
  const [ip, setIp] = useState(init?.ip || ''), [port, setPort] = useState(String(init?.port || 9100)), [proto, setProto] = useState<Proto>(init?.proto || 'escpos');
  const [conn, setConn] = useState<Conn>('idle'), [bad, setBad] = useState(false), [okModel, setOkModel] = useState('');
  const tok = useRef(0); // 测试中改了输入：旧的结果作废
  const test = (v = ip) => {
    if (!ipValid(v)) { setBad(true); return; }
    setBad(false); setConn('busy'); setDraft(null); const my = ++tok.current;
    const model = init?.model || (proto === 'tspl' ? 'XP-365B' : 'XP-80C');
    later(() => { if (my !== tok.current) return; const ok = ipReachable(v); setConn(ok ? 'ok' : 'fail'); setOkModel(model); if (ok) setDraft({ ip: v, port: Number(port) || 9100, proto, model, via: init?.via || 'ip' }); }, 1200);
  };
  useEffect(() => { if (auto && init) test(init.ip); }, []);
  const edit = () => { tok.current++; setConn('idle'); setDraft(null); };
  return (
    <div className="flex flex-col gap-3">
      <span className="help">{t('dv.a.ipHint')}</span>
      <div className="grid grid-cols-[minmax(0,1fr)_96px] gap-gap">
        <label className="flex flex-col gap-1.5"><Gt>{t('dv.a.ipLabel')}</Gt><Input value={ip} err={bad} inputMode="decimal" placeholder="192.168.1.50" className="num" onChange={(e) => { setIp(e.target.value); setBad(false); edit(); }} onKeyDown={(e) => e.key === 'Enter' && test()} /></label>
        <label className="flex flex-col gap-1.5"><Gt>{t('dv.a.port')}</Gt><Input value={port} inputMode="numeric" className="num" onChange={(e) => { setPort(e.target.value.replace(/\D/g, '').slice(0, 5)); edit(); }} /></label>
      </div>
      {bad && <small className="text-xs text-danger-text -mt-1.5">{t('dv.a.ipBad')}</small>}
      <div className="flex flex-col gap-1.5"><Gt>{t('dv.a.proto')}</Gt><Seg value={proto} onChange={(v) => { setProto(v); edit(); }} items={PROTOS.map((p) => ({ v: p, label: t('dv.proto.' + p) }))} /></div>
      <div className="flex items-center gap-3 flex-wrap">
        <Button variant={conn === 'ok' ? 'muted' : 'soft'} disabled={conn === 'busy' || !ip} onClick={() => test()}>{conn === 'busy' ? <Spin /> : <Ico name="plug-zap" size={16} />}{t('dv.a.test')}</Button>
        {conn === 'busy' && <span className="text-sm text-sub">{t('dv.a.connecting', { ip })}</span>}
        {conn === 'ok' && <StatusTag ent="device" k="online" label={t('dv.a.connected', { m: okModel })} />}
      </div>
      {conn === 'fail' && <Note tone="bad">{t('dv.a.timeout', { ip })}</Note>}
    </div>
  );
}

/* 打印机 C：扫配置码 → 解析出 IP / 端口 / 协议，转到「填 IP」并自动测试 */
function QrTab({ later, setDraft }: { later: (fn: () => void, ms: number) => void; setDraft: (d: Draft | null) => void }) {
  const t = useT(); const [got, setGot] = useState<ReturnType<typeof parseQr>>(null);
  const scan = (code: string) => { const r = parseQr(code); if (!r) { notify(useStore.getState, 'dv.a.qrBad'); return; } setGot(r); };
  if (got) return <IpTab later={later} setDraft={setDraft} init={{ ip: got.ip, port: got.port, proto: got.proto, via: 'qr', model: got.name }} auto />;
  return (
    <div className="flex flex-col gap-3 items-stretch">
      <span className="help">{t('dv.a.qrHint')}</span>
      <Viewfinder label={t('dv.a.qrAim')} />
      <div className="flex gap-gap"><Button variant="soft" className="flex-1" onClick={() => scan(DEMO_QR)}><Ico name="scan-qr-code" size={16} />{t('dv.a.qrSim')}</Button><Button variant="muted" onClick={() => scan('https://example.com/menu')}>{t('dv.a.qrOther')}</Button></div>
      <small className="text-xs text-sub num break-all">{DEMO_QR}</small>
    </div>
  );
}
export function Viewfinder({ label }: { label: string }) {
  return (
    <div className="relative h-48 rounded-rt bg-inverse overflow-hidden grid place-items-center text-on-inverse">
      <div className="relative w-36 h-36">
        {['top-0 left-0 border-t-3 border-l-3', 'top-0 right-0 border-t-3 border-r-3', 'bottom-0 left-0 border-b-3 border-l-3', 'bottom-0 right-0 border-b-3 border-r-3'].map((c) => <i key={c} className={cn('absolute w-6 h-6 border-solid border-on-inverse rounded-1', c)} />)}
        <i className="absolute left-2 right-2 h-0.5 rounded-full qr-line" />
      </div>
      <span className="absolute bottom-2 text-xs opacity-75">{label}</span>
    </div>
  );
}

/* 副 POS / 厨房屏：附近设备（局域网广播，选中即配对）· 扫码配对 */
function NearbyTab({ kind, later, draft, setDraft, onPaired }: { kind: DeviceKind; later: (fn: () => void, ms: number) => void; draft: Draft | null; setDraft: (d: Draft | null) => void; onPaired: (d: Device) => void }) {
  const t = useT(), lang = useLang(); const found = useStore((s) => s.devices).filter((d) => d.kind === kind && d.st === 'found');
  const [busy, setBusy] = useState('');
  const pair = (d: Device) => { setBusy(d.id); setDraft(null); later(() => { setBusy(''); setDraft({ ip: d.ip, model: d.model, via: 'mdns', pairId: d.id }); onPaired(d); }, 1200); };
  return (
    <div className="flex flex-col gap-2">
      <span className="help">{t('dv.a.nearbyHint')}</span>
      {found.length === 0 && <div className="rounded-rt bg-wash"><Empty title={t('dv.a.nearbyNone')} /></div>}
      {found.map((d) => { const ok = draft?.pairId === d.id; return (
        <button key={d.id} type="button" onClick={() => pair(d)} disabled={!!busy} className={cn('flex items-center gap-3 px-3 py-2.5 rounded-rt text-left', ok ? 'bg-sel-bg' : 'bg-wash hover:bg-wash-strong')}>
          <Ico name={kindIcon(kind)} size={18} className="text-sub" />
          <span className="flex flex-col leading-tight flex-1 min-w-0"><b className="font-600">{lang === 'en' ? d.en : d.name}</b><small className="text-xs text-sub num">{d.ip} · {d.model} · {d.fw}</small></span>
          {busy === d.id ? <span className="inline-flex items-center gap-1 text-sm text-sub"><Spin />{t('dv.a.connect')}</span> : ok ? <StatusTag ent="device" k="online" label={t('dv.a.paired', { m: d.model || '' })} /> : <StatusTag ent="device" k="found" />}
        </button>); })}
    </div>
  );
}
function PairQrTab({ kind, later, draft, setDraft }: { kind: DeviceKind; later: (fn: () => void, ms: number) => void; draft: Draft | null; setDraft: (d: Draft | null) => void }) {
  const t = useT(); const [busy, setBusy] = useState(false);
  const model = kind === 'kds' ? 'KDS-15' : 'iPad 10', ip = kind === 'kds' ? '192.168.1.44' : '192.168.1.19';
  return (
    <div className="flex flex-col gap-3">
      <span className="help">{t('dv.a.pairQrHint')}</span>
      <Viewfinder label={t('dv.a.qrAim')} />
      {draft ? <StatusTag ent="device" k="online" label={t('dv.a.paired', { m: model + ' · ' + ip })} />
        : <Button variant="soft" disabled={busy} onClick={() => { setBusy(true); later(() => { setBusy(false); setDraft({ ip, model, via: 'qr' }); }, 1000); }}>{busy ? <Spin /> : <Ico name="scan-qr-code" size={16} />}{t('dv.a.pairSim')}</Button>}
    </div>
  );
}

/* 客显（视频输出）/ 秤、扫码枪（端口）：已被占用的置灰；蓝牙标「未实现」 */
function PortList({ title, items, via, draft, setDraft, scale }: { title: string; items: { id: string; label: string; sub?: string }[]; via: Via; draft: Draft | null; setDraft: (d: Draft | null) => void; scale?: boolean }) {
  const t = useT(), lang = useLang(); const devices = useStore((s) => s.devices); const [sp, setSp] = useState('dahua');
  return (
    <div className="flex flex-col gap-2">
      <Gt>{title}</Gt>
      {items.map((p) => { const used = devices.find((d) => d.conn === p.id && d.st !== 'found'); const on = draft?.conn === p.id; return (
        <button key={p.id} type="button" disabled={!!used} onClick={() => setDraft({ conn: p.id, model: p.sub, via })}
          className={cn('flex items-center gap-3 px-3 py-2.5 rounded-rt text-left', on ? 'bg-sel-bg' : 'bg-wash', used ? 'opacity-60 cursor-default' : !on && 'hover:bg-wash-strong')}>
          <Ico name={via === 'hdmi' ? 'monitor' : 'cable'} size={18} className="text-sub" />
          <span className="flex flex-col leading-tight flex-1"><b className="font-600 num">{p.label}</b>{p.sub && <small className="text-xs text-sub">{p.sub}</small>}</span>
          {used ? <span className="text-xs text-sub">{t('dv.a.inUse', { n: lang === 'en' ? used.en : used.name })}</span> : on ? <Ico name="circle-check" size={18} className="text-success-text" /> : null}
        </button>); })}
      <div className="flex items-center gap-3 px-3 py-2.5 rounded-rt bg-wash opacity-70"><Ico name="bluetooth" size={18} className="text-sub" /><span className="flex-1">{t('dv.a.bt')}</span><StatusTag ent="feature" k="todo" form="dot" /></div>
      {scale && <div className="flex flex-col gap-1.5 mt-1"><Gt>{t('dv.a.scaleProto')}</Gt><Seg value={sp} onChange={setSp} items={SCALE_PROTOS.map((p) => ({ v: p, label: t('dv.a.sp.' + p) }))} /></div>}
    </div>
  );
}
