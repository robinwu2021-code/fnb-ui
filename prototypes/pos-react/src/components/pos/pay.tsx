// 收款区（餐饮结账工作台右栏、零售收银收款态共用）：
// PayHead 步骤条 + 待收 / 应付 / 已收 + 收款方式 · CashPad 现金（实收 · 找零 · 快捷金额 · 键盘）· WaitBlock 等待扫码 / 刷卡 / 推送
import { PAYS, PAY_EN } from '@/data/sample';
import { money } from '@/lib/money';
import { cashResult, quickAmounts } from '@/domain/payment';
import { cn } from '@/lib/cn';
import { useLang, useT } from '@/i18n';
import { Steps } from '@/components/ui/steps';
import { PayGroup, PayItem } from '@/components/ui/pay-row';
import { StatusTag } from '@/components/ui/badge';
import { Empty } from '@/components/ui/feedback';
import { Keypad } from '@/components/ui/keypad';
import { Filter } from '@/components/ui/toggle';
import { Amount, Spinner } from '@/components/ui/result';
import type { ReactNode } from 'react';

export const payLabel = (k: string, lang: 'zh' | 'en') => (lang === 'en' ? PAY_EN[k]?.[0] : PAYS.find((p) => p.key === k)?.name) || k;
export const payDesc = (k: string, lang: 'zh' | 'en') => (lang === 'en' ? PAY_EN[k]?.[1] : PAYS.find((p) => p.key === k)?.desc) || '';

/** methods：每种方式的说明或不可用原因（why 非空即置灰）；不传的方式用默认说明 */
export function PayHead({ step, rest, due, paid, settled, value, onChange, methods = {}, steps, pickHint }: {
  step: number; rest: number; due: number; paid: number; settled?: boolean; value: string; onChange: (v: string) => void;
  methods?: Record<string, { desc?: ReactNode; why?: string }>; /** 第一步的名称：餐饮「账单」、零售「购物车」 */ steps?: ReactNode[]; pickHint?: boolean;
}) {
  const t = useT(), lang = useLang();
  return (
    <>
      <Steps items={steps ?? [t('co.step1'), t('co.step2'), t('co.step3'), t('co.step4')]} current={step} />
      <div className="grid grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)] gap-3 items-end pt-1 pb-2">
        <div><span className="block text-xs text-sub">{t('co.rest')}</span><b className={cn('text-2xl font-700 tracking-[-.02em] leading-[1.1] num', settled && 'text-success-text')}>{money(rest)}</b></div>
        <div><span className="block text-xs text-sub">{t('co.due')}</span><b className="text-lg font-600 num">{money(due)}</b></div>
        <div><span className="block text-xs text-sub">{t('co.paid')}</span><b className="text-lg font-600 num">{money(paid)}</b></div>
      </div>
      <span className="gt"><span>{t('co.method')}{pickHint ? t('co.methodPick') : ''}</span></span>
      <PayGroup value={value} onChange={onChange} ariaLabel={t('co.method')}>
        {PAYS.map((p) => { const m = methods[p.key] || {};
          return <PayItem key={p.key} value={p.key} color={p.color} ch={p.ch} name={payLabel(p.key, lang)} desc={m.why || m.desc || payDesc(p.key, lang)} disabled={!!m.why} />; })}
      </PayGroup>
    </>
  );
}

/** 现金：实收大字 · 找零或还差 · 快捷金额 | 4 列键盘（确认键右下跨两行） */
export function CashPad({ due, got, onKey, onQuick, onOk, okLabel }: { due: number; got: number; onKey: (k: string) => void; onQuick: (v: number) => void; onOk: () => void; okLabel?: ReactNode }) {
  const t = useT();
  const r = cashResult(due, got), short = !r.ok, quick = quickAmounts(due); /* 规则在 domain/payment */
  const keys = ['7', '8', '9', 'back', '4', '5', '6', 'clr', '1', '2', '3', 'ok', '00', '0', '.'].map((k) => k === 'ok'
    ? { k, ok: true, off: short, label: short ? <>{t('co.short')}<b className="text-md font-700 text-danger-text">{money(r.short)}</b></> : <>{okLabel ?? t('co.confirmCash')}<b className="text-lg font-700">{money(due)}</b></> }
    : { k, fn: k === 'back' || k === 'clr', label: k === 'clr' ? t('co.clr') : undefined, aria: k === 'back' ? t('auth.back') : undefined });
  return (
    <div className="grid grid-cols-2 gap-[calc(var(--gap)*2)] flex-1 min-h-0">
      <div className="flex flex-col gap-gap w-full">
        <div className="flex justify-between items-center rounded-rk bg-wash py-pad px-px"><span className="help">{t('co.got')}</span><b className="text-2xl font-700 tracking-[-.02em] num leading-[1.1]">{money(got)}</b></div>
        {short ? <Amount sm short label={t('co.short')} value={money(r.short)} /> : <Amount sm label={t('co.change')} value={money(r.change)} />}
        <span className="gt"><span>{t('co.quick')}</span></span>
        <div className="flex flex-wrap gap-gap">{quick.map((v) => <Filter key={v} pressed={got === v} onPressedChange={() => onQuick(v)}>¥{v % 1 ? v.toFixed(2) : v}</Filter>)}</div>
      </div>
      <Keypad cols={4} keys={keys} onKey={(k) => (k === 'ok' ? (short ? null : onOk()) : onKey(k))} />
    </div>
  );
}

export function WaitBlock({ title, hint, dev = 'online', devLabel }: { title: ReactNode; hint: ReactNode; dev?: string; devLabel: ReactNode }) {
  return <Empty fill icon={<Spinner />} title={title} desc={<><span>{hint}</span><br /><StatusTag ent="device" k={dev} label={devLabel} /></>} />;
}
