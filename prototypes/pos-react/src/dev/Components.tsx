// 组件展示页（替代 prototypes/bapp/pos-components.html）：列出全部组件与变体。地址 #components；页首开关对这页同样生效。
import { useState, type ReactNode } from 'react';
import { useStore } from '@/store';
import { useT, useLang } from '@/i18n';
import { Ico } from '@/lib/icon';
import { STATES, type StGroup } from '@/lib/states';
import { INITIAL_TABLES, PAYS, PVOUCHERS, QUICK, type Table as TableT } from '@/data/sample';
import { Button } from '@/components/ui/button';
import { Seg } from '@/components/ui/toggle-group';
import { Filter, FilterCount } from '@/components/ui/toggle';
import { OptionGroup, Option, OptionMulti } from '@/components/ui/radio-cards';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Switch } from '@/components/ui/switch';
import { Checkbox } from '@/components/ui/checkbox';
import { Input, Search, Textarea } from '@/components/ui/input';
import { Stepper } from '@/components/ui/stepper';
import { Steps } from '@/components/ui/steps';
import { StatusTag, StatusIcon, Chip, Badge, Qty } from '@/components/ui/badge';
import { StatusTile } from '@/components/ui/status-tile';
import { Panel, Block, DueFoot, Acts, Stat } from '@/components/ui/panel';
import { Cells, Cell } from '@/components/ui/description-list';
import { Table, Tr, Td, Th, Nm2 } from '@/components/ui/table';
import { Keypad, Pins } from '@/components/ui/keypad';
import { Empty, Note, Banner, Skeleton, Toaster } from '@/components/ui/feedback';
import { Result, Spinner, Amount } from '@/components/ui/result';
import { Kpi, Kpis, VBars, HBars, yuan } from '@/components/ui/chart';
import { PayGroup, PayItem } from '@/components/ui/pay-row';
import { PromoRow } from '@/components/ui/promo-row';
import { CouponCard, PlatMark } from '@/components/ui/coupon';
import { Dialog, DialogTitle, DialogDesc, DialogActs } from '@/components/ui/dialog';
import { Sheet } from '@/components/ui/sheet';
import { Menu, MenuTrigger, MenuContent, MenuItem, MenuSep } from '@/components/ui/dropdown-menu';
import { Rail } from '@/layout/rail';
import { Device } from '@/layout/device';
import { TableTile } from '@/modules/tables/TablesWB';
import { OrderLine } from '@/modules/order/OrderLine';
import { DishCard } from '@/modules/order/DishCard';
import { toast } from 'sonner';

function Sec({ id, title, desc, children, w2 }: { id: string; title: string; desc?: string; children: ReactNode; w2?: boolean }) {
  return <section id={id} className={w2 ? 'col-span-2' : ''}><h3 className="text-base font-700 m-0 mb-1">{title}</h3>{desc && <p className="text-xs text-sub m-0 mb-2">{desc}</p>}<div className="bg-canvas rounded-18px p-4 flex flex-col gap-gap items-start overflow-hidden">{children}</div></section>;
}
const Row = ({ children, className }: { children: ReactNode; className?: string }) => <div className={'flex flex-wrap gap-gap items-center ' + (className || '')}>{children}</div>;
const Lbl = ({ children }: { children: ReactNode }) => <span className="text-xs text-sub w-full tracking-[.02em]">{children}</span>;
const Sw = ({ v, name }: { v: string; name: string }) => <span className="flex flex-col items-start gap-1 text-xs text-sub"><i className="w-56px h-36px rounded-rk block" style={{ background: v }} />{name}</span>;

export function ComponentsPage() {
  const t = useT(), lang = useLang(); const s = useStore();
  const [seg, setSeg] = useState('a'), [f, setF] = useState(['a']), [opt, setOpt] = useState('l'), [multi, setMulti] = useState(false), [pay, setPay] = useState('wx'), [ck, setCk] = useState(true), [sw, setSw] = useState(true), [q, setQ] = useState(2), [dlg, setDlg] = useState(false), [sheet, setSheet] = useState(false), [rail, setRail] = useState('a');
  const groups = Object.keys(STATES.groups) as StGroup[];
  const ents = ['order', 'item', 'table', 'delivery', 'payment', 'device', 'stock', 'sync', 'coupon', 'campaign', 'member', 'recharge', 'shift', 'print', 'ticket', 'booking', 'approval', 'invoice', 'points'];
  const pv = PVOUCHERS[0];
  const tb = (st: TableT['st']): TableT => ({ ...INITIAL_TABLES[0], id: 'A03', st });
  return (
    <div className="grid grid-cols-[repeat(auto-fill,minmax(380px,1fr))] gap-x-6 gap-y-9">
      <Sec id="tokens" title="00 令牌" desc="颜色只用角色（--c-*）；圆角两套 rc/rt · rb/rk；控件高 36/44/52；字号 7 档" w2>
        <Lbl>角色色（随皮肤 · 色系）</Lbl>
        <Row>{[['canvas', '画布'], ['surface', '面板'], ['wash', '浅块'], ['wash-strong', '深一档'], ['pick', '已点'], ['sel-bg', '选中'], ['ink', '墨'], ['sub', '次要'], ['placeholder', '占位'], ['primary', '主色'], ['primary-tint', '主色浅底'], ['saving', '优惠'], ['warning-tint', '待处理底'], ['warning-text', '待处理字'], ['danger-tint', '异常底'], ['danger-text', '异常字'], ['success-text', '成功']].map(([k, n]) => <Sw key={k} v={`var(--c-${k})`} name={`${n} ${k}`} />)}</Row>
        <Lbl>圆角：框架 rc / rt · 按钮 rb / rk</Lbl>
        <Row>{['rc', 'rt', 'rb', 'rk'].map((r) => <span key={r} className="flex flex-col items-center gap-1 text-xs text-sub"><i className="w-72px h-36px bg-primary block" style={{ borderRadius: `var(--${r})` }} />--{r}</span>)}</Row>
        <Lbl>控件高：sm / md / lg（紧凑 36 / 44 / 52 · 标准 40 / 48 / 56）· 字号 xs 12 · sm 13 · base 14 · md 15 · lg 18 · xl 22 · 2xl 32</Lbl>
        <Row><Button size="sm">sm</Button><Button>md</Button><Button size="lg">lg</Button><span className="text-xs">xs</span><span className="text-sm">sm</span><span className="text-base">base</span><span className="text-md">md</span><span className="text-lg">lg</span><span className="text-xl">xl</span><span className="text-2xl">2xl</span></Row>
      </Sec>
      <Sec id="button" title="Button ← sh-btn" desc="default 墨实心（一屏一个）· soft · muted · danger 浅红底红字 · danger-solid 只在最终确认 · link · disabled">
        <Row><Button>主按钮</Button><Button variant="soft">soft</Button><Button variant="muted">muted</Button><Button variant="danger">danger</Button><Button variant="danger-solid">danger-solid</Button><Button variant="link">link</Button><Button disabled>disabled</Button></Row>
        <Row><Button size="sm"><Ico name="plus" />sm 带图标</Button><Button size="icon" variant="muted" aria-label="更多"><Ico name="ellipsis" size={18} /></Button><Button size="icon-sm" variant="muted" aria-label="关闭"><Ico name="x" size={16} /></Button><Button size="lg">lg</Button></Row>
        <Acts className="w-full"><Button variant="soft">送厨 · 5</Button><Button block>结账</Button></Acts>
      </Sec>
      <Sec id="seg" title="Seg ← sh-seg · Filter ← sh-filter · Tabs ← sh-tabs" desc="分段单选：浅灰轨道 + 当前段 gray-200；筛选可多开，计数红字 = 要处理；页签 2px 底线">
        <Seg value={seg} onChange={setSeg} items={[{ v: 'a', label: '列表' }, { v: 'b', label: '卡片' }, { v: 'c', label: '禁用', disabled: true }]} />
        <Row>{[['a', '全部', 18, false], ['b', '待结账', 2, true], ['c', '已结账', 12, false]].map(([k, l, n, al]) => <Filter key={String(k)} pressed={f.includes(String(k))} onPressedChange={() => setF((x) => (x.includes(String(k)) ? x.filter((y) => y !== k) : [...x, String(k)]))}>{l as string}<FilterCount n={n as number} alert={al as boolean} /></Filter>)}<Filter disabled pressed={false}><PlatMark k="ks" />快手<FilterCount n="未授权" /></Filter></Row>
        <Tabs defaultValue="a"><TabsList><TabsTrigger value="a">外卖</TabsTrigger><TabsTrigger value="b">会员</TabsTrigger><TabsTrigger value="c">打印</TabsTrigger></TabsList></Tabs>
      </Sec>
      <Sec id="option" title="选项块一族 ← sh-option / dn-pay / dn-plan / dn-coupon / dn-layout" desc="共用一条规则：未选 wash · 当前 gray-200 + 墨字加粗 · 禁用置灰写原因" w2>
        <Lbl>Option（RadioGroup）· OptionMulti（多选）</Lbl>
        <Row><OptionGroup value={opt} onChange={setOpt}><Option value="l" label="大份" sub="¥48" /><Option value="s" label="小份" sub="¥34" /><Option value="x" label="超大份" sub="暂不供应" disabled /></OptionGroup><OptionMulti on={multi} onToggle={() => setMulti(!multi)} label="加花生" sub="+¥3" /></Row>
        <Lbl>PayItem（收款方式，品牌色图标是 §3.4 例外）</Lbl>
        <div className="bg-surface rounded-rc p-2 w-full"><PayGroup value={pay} onChange={setPay}>{PAYS.map((p) => <PayItem key={p.key} value={p.key} color={p.color} ch={p.ch} name={p.name} desc={p.key === 'sv' ? '先识别会员' : p.desc} disabled={p.key === 'sv'} />)}</PayGroup></div>
        <Lbl>CouponCard（本店券 · 平台券 · 核销记录）· PlatMark</Lbl>
        <div className="grid grid-cols-2 gap-gap w-full"><CouponCard value="¥15" title="满 200 减 15" lines={['满减券 · 有效期至 2026-12-31']} on right={<Ico name="circle-check" />} /><CouponCard value="¥30" title="满 300 减 30" lines={['满减券 · 还差 ¥56.00 可用']} off /><CouponCard value={'¥' + pv.face} title={<><PlatMark k="mt" />美团·点评 · {pv.name}</>} lines={['代金券 · 售价 ¥88 · 不找零 · 至 2026-12-31', <span className="num">券码 {pv.code} · 剩 2 次</span>]} right={<Button variant="link" className="text-sm">移除</Button>} /><Row>{['mt', 'dy', 'ks', 'xhs'].map((k) => <span key={k} className="inline-flex items-center text-sm"><PlatMark k={k} />{k}</span>)}</Row></div>
        <Lbl>PromoRow（结账优惠行：✓ 已用 · ○ 可选 · ⊘ 置灰写原因）</Lbl>
        <div className="bg-surface rounded-rc p-2 w-full flex flex-col gap-0.5"><PromoRow on icon="receipt" title="服务费 5%" desc="堂食收取" amt="+¥12.20" /><PromoRow on title="非高峰 8 折 · 活动" desc="自动匹配 · 最多减 ¥20" amt="−¥20.00" onClick={() => {}} /><PromoRow title="优惠券" desc="可用 2 张" onClick={() => {}} /><PromoRow title="整单折扣 / 免单" why="已用活动或券，不能再人工打折" /></div>
      </Sec>
      <Sec id="form" title="Switch · Checkbox · Input · Search · Textarea · Stepper" desc="关闭态 gray-500 轨道 / gray-150 实心块；输入框浅灰块无描边；步进器 36">
        <Row><Switch checked={sw} onCheckedChange={setSw} aria-label="开关" /><Switch checked={false} aria-label="关" /><Checkbox checked={ck} onCheckedChange={(v) => setCk(v === true)} aria-label="勾选" /><Checkbox checked="indeterminate" aria-label="部分" /><Stepper q={q} onDec={() => setQ(Math.max(1, q - 1))} onInc={() => setQ(q + 1)} /><Stepper q={1} size="md" /></Row>
        <Input placeholder="例如「先上凉菜」" /><Input num defaultValue="¥ 500.00" /><Input err defaultValue="格式不对" /><Search placeholder="搜索菜名 / 拼音首字母 / 编号" extra="扫码" /><Textarea placeholder="选填" />
      </Sec>
      <Sec id="steps" title="Steps ← dn-steps · Stat · Kpi · Amount" desc="已过打勾、当前 gray-200、步间 8px 短线；金额 32/700 等宽">
        <Steps items={['账单', '收款方式', '收款', '完成']} current={2} /><Steps items={['账单', '收款方式', '收款', '完成']} current={4} />
        <div className="bg-surface rounded-rc w-full"><Stat items={[{ n: 4, l: <><StatusIcon ent="table" k="free" />空闲</> }, { n: 8, l: <><StatusIcon ent="table" k="dining" />用餐中</> }, { n: 3, l: <><StatusIcon ent="table" k="due" />待结账</> }]} /></div>
        <div className="bg-surface rounded-rc p-pad w-full"><Kpis><Kpi label="营业额" value="¥8,642" note="+6.2%" st="up" /><Kpi label="翻台率" value="1.6" note="−0.1" st="down" /><Kpi label="单数" value="46" note="+3" /></Kpis><Amount label="应收" value="¥236.00" /><Amount sm short label="还差" value="¥36.00" /></div>
      </Sec>
      <Sec id="status" title="StatusTag ← st-tag · StatusIcon ← st-ic" desc="19 实体 112 状态，形式按组：待处理 / 异常 / 预约 / 暂存 = chip，其余 dot；颜色只给待处理（琥珀）和异常（红）" w2>
        {groups.map((g) => <div key={g} className="w-full"><Lbl>{STATES.groups[g].zh} · {g} · {STATES.groups[g].form}</Lbl><Row>{ents.flatMap((e) => Object.keys(STATES.states[e] || {}).filter((k) => STATES.states[e][k].group === g).map((k) => <StatusTag key={e + k} ent={e} k={k} />))}</Row></div>)}
        <Lbl>Chip · Badge · Qty</Lbl><Row><Chip>熟客</Chip><Chip act>会员价</Chip><Chip onCard>已沽清</Chip><Badge n={2} /><Qty n={3} /></Row>
      </Sec>
      <Sec id="tile" title="StatusTile ← dn-table / dn-ocard" desc="整块状态底：常态 30% · 要处理 60% · 空闲 / 已关闭浅灰 · 当前 gray-200；不画色条、不描边" w2>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(124px,1fr))] gap-gap w-full">{(['free', 'booked', 'seated', 'dining', 'late', 'due', 'partial', 'to_clear', 'shared', 'disabled'] as TableT['st'][]).map((st) => <TableTile key={st} t={tb(st)} current={st === 'dining'} onClick={() => {}} />)}</div>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-gap w-full">{[['#1025', 'due', true, false], ['#1024', 'open', false, false], ['#1018', 'cancelled', false, true], ['#1020', 'paid', false, false]].map(([no, st, al, qt]) => <StatusTile key={String(no)} group={STATES.states.order[st as string].group} alert={al as boolean} quiet={qt as boolean} current={no === '#1024'} onClick={() => {}} className="py-3 px-3.5 gap-0.5 min-h-124px"><span className="flex items-center justify-between gap-2"><b className="text-lg font-700 num">{no as string}</b><StatusTag ent="order" k={st as string} /></span><span className="help">A05 · 堂食 · 2 人</span><span className="help">3 件 · 11:58</span><span className="a mt-auto pt-1.5 text-xl font-700 num">¥96.00</span></StatusTile>)}</div>
      </Sec>
      <Sec id="panel" title="Panel ← sh-panel（P1 工具条 · P2 块色清单 + 白色窄底 · P3 详情表单）" desc="头固定 · 主体单独滚动 · 底固定；P2 的清单是唯一的灰色区域" w2>
        <div className="grid grid-cols-3 gap-gap w-full h-420px">
          <Panel head={<div className="flex flex-wrap gap-gap [&>label]:flex-[1_1_120px] [&>label]:w-auto"><Search placeholder="搜索" /><Filter pressed>全部</Filter></div>}><div className="grid grid-cols-2 gap-gap"><DishCard d={s.dishes[4]} /><DishCard d={s.dishes[3]} /></div></Panel>
          <Panel ledger head={<div className="row-between"><b className="text-xl font-700 num">A03</b><StatusTag ent="order" k="open" /></div>} foot={<DueFoot label="应收" amt="¥236.00" note="6 道 · 已优惠 ¥20.20"><Acts><Button variant="soft">送厨 · 5</Button><Button block>结账</Button></Acts></DueFoot>}><Block title="待下单 · 5 份"><div className="bg-surface rounded-rk px-pad">{s.lines.filter((l) => l.st === 'new').map((l, i) => <OrderLine key={i} l={l} i={i} edit />)}</div></Block><Block title="已下单 · 4 道"><div>{s.lines.filter((l) => l.st !== 'new').map((l, i) => <OrderLine key={i} l={l} i={i + 2} edit={false} />)}</div></Block></Panel>
          <Panel head={<><b className="h-title">宫保鸡丁</b><span className="help">Kung Pao Chicken · 热菜</span></>} foot={<Acts><Button variant="muted">下架</Button><Button block>保存</Button></Acts>}><Cells><Cell k="中文名" v="宫保鸡丁" go /><Cell k="价格" v="¥48 起" go /><Cell k="沽清"><Switch checked={false} aria-label="沽清" /></Cell></Cells><Block title="标记"><Row><Filter pressed><Ico name="award" size={15} />招牌</Filter><Filter pressed={false}><Ico name="flame" size={15} />辣</Filter></Row></Block></Panel>
        </div>
      </Sec>
      <Sec id="table" title="Table ← sh-table · Cells ← sh-cells" desc="列宽用 grid 模板；不画行线；当前行 gray-100">
        <div className="bg-surface rounded-rc p-pad w-full"><Table cols="44px minmax(0,1.6fr) 72px 72px" head={['', '菜品', '分类', <span className="block text-right">价格</span>]}>{s.dishes.slice(0, 3).map((d, i) => <Tr key={d.n} on={i === 1} onClick={() => {}}><Td><Th img={d.img} text={d.n[0]} k="red" /></Td><Td><Nm2 a={d.n} b={d.en} /></Td><Td>{d.c}</Td><Td r>¥{d.p}</Td></Tr>)}</Table></div>
        <div className="bg-surface rounded-rc px-pad w-full"><Cells><Cell k="人数" v="4 / 4" num /><Cell k="服务员" v="王芳" go /><Cell k="交班需清点钱箱" strong><Switch checked aria-label="开关" /></Cell></Cells></div>
      </Sec>
      <Sec id="keys" title="Keypad ← dn-keys · Pins ← dn-pins" desc="数字键块色 · 功能键深一档 · 确认键主色实心跨两行">
        <div className="w-full max-w-360px"><Pins n={2} /><Keypad cols={4} keys={['7', '8', '9', 'back', '4', '5', '6', 'clr', '1', '2', '3', 'ok', '00', '0', '.'].map((k) => (k === 'ok' ? { k, ok: true, label: <>确认收款<b className="text-lg font-700">¥236.00</b></> } : { k, fn: k === 'back' || k === 'clr', label: k === 'clr' ? '清空' : undefined, aria: k === 'back' ? '退格' : undefined }))} onKey={() => {}} /></div>
      </Sec>
      <Sec id="feedback" title="Toast · Banner · Note · Empty · Skeleton · Spinner · Result" desc="Toast 墨底白字；横幅只在离线 / 异常时出现；状态一律图标 + 文字">
        <Row><Button variant="muted" onClick={() => toast('已送厨 5 道菜')}>弹 Toast</Button></Row>
        <Banner action="查看"><StatusIcon ent="device" k="offline" />网络已断开，3 单待同步</Banner><Banner bad><StatusIcon ent="print" k="failed" />打印失败 · 热菜档口</Banner>
        <Note>离线时仍可点菜、送厨。</Note><Note tone="warn">低于 8 折需要主管授权。</Note><Note tone="bad">券已使用，不能重复使用。</Note>
        <div className="bg-surface rounded-rc w-full"><Empty title="还没有点菜" desc="从菜单里点一道菜" /></div>
        <Row className="w-full"><Skeleton w="l" /><Skeleton w="m" /><Skeleton w="s" /><Spinner /></Row>
        <div className="bg-surface rounded-rc p-pad w-full grid grid-cols-2"><Result title="收款成功 ¥236.00" /><Result bad title="支付失败" /></div>
      </Sec>
      <Sec id="chart" title="VBars · HBars ← dn-vbars / dn-hbars" desc="单一系列只用主色；不画网格和灰底轨道；条间 2px，数据端 4px 圆角">
        <div className="bg-surface rounded-rc p-pad w-full"><VBars data={[['10', 320], ['11', 980], ['12', 2860], ['13', 2410], ['14', 620]]} fmt={yuan} label="营业额" /><HBars data={[['微信', 4102], ['支付宝', 2340], ['现金', 1280]]} fmt={yuan} /></div>
      </Sec>
      <Sec id="overlay" title="Dialog ← sh-dialog · Sheet ← sh-sheet · Menu ← sh-menu" desc="弹窗 ≤560（wide 760），侧滑右侧 480，菜单反色面；都 Portal 到设备容器，遮罩只盖设备">
        <Device>
          <div className="p-gap flex gap-gap items-start" style={{ gridRow: 'span 3' }}>
            <Button variant="muted" onClick={() => setDlg(true)}>打开弹窗</Button><Button variant="muted" onClick={() => setSheet(true)}>打开侧滑</Button>
            <Menu><MenuTrigger asChild><Button variant="muted" size="icon" aria-label="更多"><Ico name="ellipsis" size={18} /></Button></MenuTrigger><MenuContent align="start"><MenuItem>改人数</MenuItem><MenuItem>整单备注</MenuItem><MenuSep /><MenuItem hint="需授权">赠送</MenuItem><MenuItem danger hint="需授权">退菜</MenuItem><MenuItem disabled hint="有未结账菜品">清台</MenuItem></MenuContent></Menu>
            <Dialog open={dlg} onClose={() => setDlg(false)} ariaLabel="还不能清台"><DialogTitle>还不能清台</DialogTitle><DialogDesc>这桌还有未结账的菜品。先结账或退菜，再清台。</DialogDesc><DialogActs><Button variant="muted" onClick={() => setDlg(false)}>知道了</Button><Button onClick={() => setDlg(false)}>去结账</Button></DialogActs></Dialog>
            <Sheet open={sheet} onClose={() => setSheet(false)} title="平台券 · 验券" closeLabel="关闭"><Note tone="warn">离线不能验券。</Note><span className="help">一单最多 3 张。</span></Sheet>
          </div>
          <Toaster />
        </Device>
      </Sec>
      <Sec id="nav" title="Rail ← dn-rail · QuickBar ← dn-quick" desc="导航当前项 gray-200 + 墨字加粗；分组小标题；快捷操作图标 + 两字">
        <div className="w-128px h-260px flex"><Rail items={[{ cap: '桌区' }, { v: 'a', label: '全部', em: 18 }, { v: 'b', label: '大厅', em: 12 }, { cap: '状态' }, { v: 'c', label: '空闲', icon: 'circle', em: 4 }, { v: 'd', label: '待结账', icon: 'receipt', em: 2 }]} cur={rail} onPick={setRail} /></div>
        <div className="bg-surface rounded-rc p-2 w-full flex gap-1">{s.quick.map((k) => <button key={k} type="button" className="flex-1 inline-flex items-center justify-center gap-1 min-h-ctl-sm px-1.5 rounded-rk text-sm text-ink hover:bg-wash"><Ico name={QUICK[k][1]} size={16} />{t(QUICK[k][0])}</button>)}</div>
        <span className="help">{lang === 'en' ? 'Page-top switches apply here too.' : '页首开关对本页同样生效（皮肤、色系、圆角、密度、语言）。'}</span>
      </Sec>
    </div>
  );
}
