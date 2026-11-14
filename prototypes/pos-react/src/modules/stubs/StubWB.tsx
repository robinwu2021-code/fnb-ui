import { Panel } from '@/components/ui/panel';
import { Empty } from '@/components/ui/feedback';
import { useStore } from '@/store';
import { MODULES } from '@/layout/modules';
import { useT } from '@/i18n';
export function StubWB() {
  const t = useT(); const wb = useStore((s) => s.wb); const m = MODULES[wb];
  const areas = m.layout === 'W1' ? ['nav', 'content', 'detail'] : m.layout === 'W2' || m.layout === 'W3' ? ['content', 'detail'] : ['nav', 'content'];
  return <>{areas.map((a) => <Panel key={a} area={a as 'nav'} bodyClass="flex flex-col">{a === 'content' ? <Empty fill title={t('stub.title', { m: t(m.name) })} desc={t('stub.desc')} /> : null}</Panel>)}</>;
}
