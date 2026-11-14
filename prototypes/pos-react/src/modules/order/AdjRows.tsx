// 费用与优惠 · 紧凑 ← 原型 adjRows：一行 图标 · 名称 · 金额；加价墨色、减价用优惠色、不可用置灰
import { useStore } from '@/store';
import type { Totals } from '@/lib/pricing';
import { money } from '@/lib/money';
import { useT } from '@/i18n';
import { FeeRow } from '@/components/pos/fee-row';
import { Block } from '@/components/ui/panel';
import type { ReactNode } from 'react';

/** 原 Row：act 是动作名，转成 FeeRow 的 onClick */
function Row({ act, ...p }: { icon: string; name: ReactNode; amt?: ReactNode; neg?: boolean; off?: boolean; act?: string }) {
  const dispatch = useStore((s) => s.act);
  return <FeeRow {...p} onClick={act ? () => dispatch(act) : undefined} />;
}
export function AdjRows({ t: tot, head, extra }: { t: Totals; head?: ReactNode; extra?: ReactNode }) {
  const t = useT(); const wb = useStore((s) => s.wb), pvN = useStore((s) => s.pv.reduce((a, x) => a + x.n, 0)), mwhy = useStore((s) => s.mwhy);
  const c = tot.coupon, act = tot.act > 0;
  return (
    <Block title={t('order.fees')} right={head}>
      <div className="flex flex-col gap-0.5">
        <Row icon="receipt" name={t('order.svc')} amt={'+' + money(tot.svc)} />
        {act && <Row icon="badge-percent" name={t('order.act')} amt={'−' + money(tot.act)} neg act={wb === 'checkout' ? 'pact' : ''} />}
        {tot.pv > 0 && <Row icon="ticket-check" name={t('order.pv', { n: pvN })} amt={'−' + money(tot.pv)} neg act="ov:pvoucher" />}
        {c ? <Row icon="ticket" name={c.kind + ' · ' + c.name} amt={'−' + money(tot.cp)} neg act="ov:coupons" />
          : <Row icon="ticket" name={tot.pv ? t('order.coupon.off.pv') : act ? t('order.coupon.off.act') : t('order.coupon.idle')} off act={act || tot.pv ? '' : 'ov:coupons'} />}
        {!tot.pv && <Row icon="ticket-check" name={t('order.pvIdle')} off act="ov:pvoucher" />}
        {tot.manual > 0 && <Row icon="hand-coins" name={t('order.manual', { w: mwhy })} amt={'−' + money(tot.manual)} neg act="ov:manual" />}
        {tot.round > 0 && <Row icon="scissors" name={t('order.round')} amt={'−' + money(tot.round)} neg />}
        {extra}
      </div>
    </Block>
  );
}
