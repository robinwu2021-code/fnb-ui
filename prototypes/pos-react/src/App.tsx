import { useEffect, useState } from 'react';
import { ComponentsPage } from '@/dev/Components';
import { useStore } from '@/store';
import { Device } from '@/layout/device';
import { Workbench } from '@/layout/workbench';
import { DevBar } from '@/dev/DevBar';
import { useBiz } from '@/biz/store';

/* 皮肤、色系、形状、密度、语言都挂在 <html> 上（和原型同一套 data-* 属性），令牌 CSS 据此换值 */
function useHtmlAttrs() {
  const skin = useStore((s) => s.skin), tone = useStore((s) => s.tone), frame = useStore((s) => s.frame), btn = useStore((s) => s.btn), density = useStore((s) => s.density), lang = useStore((s) => s.lang);
  useEffect(() => {
    const r = document.documentElement;
    r.setAttribute('data-skin', skin); r.setAttribute('data-tone', tone); r.setAttribute('data-frame', frame); r.setAttribute('data-btn', btn); r.setAttribute('data-density', density);
    r.setAttribute('lang', lang === 'en' ? 'en' : 'zh-CN'); document.title = lang === 'en' ? 'Dining POS · React' : '全线点餐 POS · React';
  }, [skin, tone, frame, btn, density, lang]);
}
function useHash() { const [h, setH] = useState(location.hash); useEffect(() => { const f = () => setH(location.hash); addEventListener('hashchange', f); return () => removeEventListener('hashchange', f); }, []); return h; }
export default function App() {
  useHtmlAttrs(); const hash = useHash(); const region = useStore((s) => s.region); // 换地区 = 换币种，重挂一次让金额全部重新格式化
  /* 旧链接 #retail：切到零售业态的收银页 */
  useEffect(() => { if (hash === '#retail') { useBiz.getState().set({ biz: 'retail', rwb: 'pos' }); history.replaceState(null, '', location.pathname); } }, [hash]);
  return (
    <div className="max-w-1400px mx-auto p-5">
      <DevBar page={hash === '#components' ? 'components' : 'app'} />
      {hash === '#components' ? <ComponentsPage key={region} /> : <Device key={region}><Workbench /></Device>}
    </div>
  );
}
