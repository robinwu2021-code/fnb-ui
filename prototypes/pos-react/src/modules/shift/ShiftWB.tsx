// 班次（W3）：当班数据 · 钱箱实盘（P2 块色清单 + 白色窄底）
import { useStore } from '@/store';
import { DENOMS, drawerTotal, expectedCash, variance } from '@/domain/shift';
import { useT } from '@/i18n';
import { Panel, Block, DueFoot, Acts, Stat } from '@/components/ui/panel';
import { Button } from '@/components/ui/button';
import { Cells, Cell } from '@/components/ui/description-list';
import { Chip, StatusTag, CatMark } from '@/components/ui/badge';
export function ShiftWB() {
  const t = useT(); const dispatch = useStore((s) => s.act);
  const pays: [string, string, string][] = [[t('pay.cash'), '¥1,280', 'yellow'], [t('pay.wx'), '¥4,102', 'green'], [t('pay.ali'), '¥2,340', 'blue'], [t('pay.card'), '¥620', 'indigo'], [t('sh.sv'), '¥300', 'purple']];
  /* 钱箱实盘：张数 → 实盘金额；应有 = 备用金 ¥500 + 现金收入 ¥1,280；盘差由 domain/shift 算 */
  const notes = DENOMS, counts = [15, 3, 4, 3, 1, 5];
  const counted = drawerTotal(counts), expected = expectedCash({ float: 500, cashIn: 1280 }), v = variance(expected, counted);
  const yuan = (n: number) => '¥' + n.toLocaleString('en-US', { minimumFractionDigits: 2 });
  return (
    <>
      <Panel area="content" head={<><div className="row-between"><b className="h-title">{t('sh.title')}</b><span className="help">{t('sh.meta')}</span></div><Stat items={[{ n: '¥8,642', l: t('rp.revenue') }, { n: '46', l: t('rp.orders') }, { n: '¥187.9', l: t('rp.avg') }]} /></>}>
        <Block title={t('sh.byPay')}><Cells>{pays.map((p) => <Cell key={p[0]} k={<span className="inline-flex items-center gap-1.5"><CatMark k={p[2]} />{p[0]}</span>} v={p[1]} num />)}</Cells></Block>
        <Block title={t('sh.abnormal')}><div className="flex flex-wrap gap-gap"><StatusTag ent="item" k="voided" label={t('sh.voids')} form="chip" /><StatusTag ent="item" k="comp" label={t('sh.comps')} form="chip" /><StatusTag ent="item" k="repriced" label={t('sh.discs')} form="chip" /><Chip>{t('sh.drawer')}</Chip></div></Block>
      </Panel>
      <Panel area="detail" ledger head={<><b className="h-title">{t('sh.count')}</b><span className="help">{t('sh.countHint')}</span></>}
        foot={<DueFoot label={t('sh.diff')} amt={(v.diff < 0 ? '−' : v.diff > 0 ? '+' : '') + yuan(Math.abs(v.diff))} note={t('sh.expected', { a: yuan(expected), b: yuan(counted) })} short={v.st === 'short'}><Acts><Button variant="muted" onClick={() => dispatch('toast:sh.handedOver')}>{t('sh.printReport')}</Button><Button block onClick={() => dispatch('ov:confirm-shift')}>{t('sh.handover')}</Button></Acts></DueFoot>}>
        <div className="sec"><span>{t('sh.denom')}</span></div>
        <div className="flex flex-col gap-gap">{notes.map((n, i) => <div key={n} className="grid grid-cols-[56px_minmax(0,1fr)] gap-gap items-center"><span className="help">{t('sh.yuan', { n })}</span><div className="flex items-center justify-end h-ctl px-pad rounded-rk bg-surface num font-600">{t('sh.notes', { n: counts[i] })}</div></div>)}</div>
      </Panel>
    </>
  );
}
