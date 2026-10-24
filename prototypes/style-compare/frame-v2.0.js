/* UI 2.0：pos-components-v2.0.html / pos-ui-v2.0.html 共用。把 themes-v2.0.css 注入 iframe 里的正式页，按 #风格 切换。 */
const STYLES={
  base:{n:'原版',c:'#18181B',spec:[['色彩','墨黑皮肤 · 中性灰四层'],['形状','面板 14 · 按钮 8'],['选中','主色 tint + 主色字'],['字体','Inter + 思源黑体']]},
  apple:{n:'Apple',c:'#007AFF',spec:[['色彩','系统蓝 #007AFF · 分组灰 #F2F2F7'],['形状','面板 16 · 卡内 12 · 按钮 12'],['选中','蓝 tint；分段 = 白色浮起滑块'],['控件','iOS 绿开关 · 圆形勾选 · 毛玻璃菜单'],['字体','SF Pro / 苹方']]},
  m3:{n:'Material 3',c:'#6750A4',spec:[['色彩','基线紫 #6750A4 · Surface container 色调分层'],['形状','面板 16 · 控件 8 · 按钮全圆 · 弹窗 28'],['选中','Secondary container 填色 + 勾'],['控件','Tonal / Outlined 按钮 · 描边 Chip · Filled 输入框'],['字体','Roboto + 思源黑体']]},
  uber:{n:'Uber Base',c:'#000',spec:[['色彩','纯黑为品牌色 · 两档灰 · 彩色只给状态'],['形状','面板 12 · 按钮 8 · 筛选与导航胶囊'],['选中','导航与筛选黑底白字；卡片类 2px 黑框'],['控件','黑色开关 / 勾选 · 白底投影菜单'],['字体','Helvetica Neue，金额加粗到 800']]},
  glass:{n:'玻璃态（白）',c:'linear-gradient(135deg,#FFFFFF,#C9D0DC)',spec:[['色彩','近白画布 + 淡冷色光斑 · 72% 白磨砂面 · 墨色主按钮'],['形状','面板 22 · 卡内 16 · 按钮 14'],['选中','纯白浮起 + 投影；卡片类墨色内圈'],['材质','24px 背景模糊 + 白描边 + 极淡灰投影'],['注意','低端设备掉帧；白底上分层很淡']]},
};
const q=new URLSearchParams(location.search), frame=document.getElementById('lib');
let cur=q.get('s')||location.hash.slice(1)||'apple'; if(!STYLES[cur])cur='apple';
const only=q.get('only')?q.get('only').split(','):null;
/* 版本：?v=1.5 改开 *-v1.5.html（叠加 pos-v15.css）；themes-v2.0.css 的「v1.5 兼容」段负责把 v1.5 的墨色选中还给皮肤 */
const ver=q.get('v')==='1.5'?'1.5':'1';
if(ver==='1.5'){
  frame.src=frame.getAttribute('src').replace(/\.html$/,'-v1.5.html');
  STYLES.base.spec=[['色彩','松绿皮肤 · 中性灰 · 选中白底墨圈'],['形状','面板 16 · 卡片 12 · 按钮 8 · 选择类胶囊'],['间距','面板内边距 16 · 间距 12'],['字体','Inter + 思源黑体']];
}
const bar=document.querySelector('.bar'), vers=document.createElement('nav');
vers.className='tabs';vers.setAttribute('aria-label','版本');
bar.insertBefore(vers,bar.querySelector('.alt'));
document.querySelectorAll('a.alt').forEach(a=>{if(ver==='1.5')a.href+='?v=1.5'});
if(only)document.body.classList.add('export');

function render(){
  document.getElementById('tabs').innerHTML=Object.entries(STYLES).map(([k,s])=>`<a href="#${k}" aria-current="${k===cur}"><i style="background:${s.c}"></i>${s.n}</a>`).join('');
  vers.innerHTML=[['1','基于 v1'],['1.5','基于 v1.5']].map(([v,n])=>`<a href="?${v==='1.5'?'v=1.5':''}#${cur}" aria-current="${v===ver}">${n}</a>`).join('');
  document.getElementById('spec').innerHTML=STYLES[cur].spec.map(([k,v])=>`<div><b>${k}</b>${v}</div>`).join('');
  const d=frame.contentDocument; if(!d||!d.documentElement)return;
  d.documentElement.dataset.style=cur;
}
function fit(){const d=frame.contentDocument;if(d&&d.documentElement){frame.style.height=d.documentElement.scrollHeight+'px';document.body.dataset.h=document.documentElement.scrollHeight}}
frame.addEventListener('load',()=>{
  const d=frame.contentDocument;
  if(!d.getElementById('style-themes')){const l=d.createElement('link');l.id='style-themes';l.rel='stylesheet';l.href='../style-compare/themes-v2.0.css';l.onload=fit;d.head.appendChild(l)}
  if(only){
    const st=d.createElement('style');
    st.textContent='header.top{display:none!important}.wrap{padding-top:24px!important;padding-bottom:8px!important}';
    d.head.appendChild(st);
    d.querySelectorAll('section').forEach(s=>{const n=s.querySelector('.sec-num');if(!n||!only.includes(n.textContent.trim()))s.remove()});
  }
  render();
  new ResizeObserver(fit).observe(d.body);
  fit();
});
addEventListener('hashchange',()=>{cur=location.hash.slice(1)||'apple';if(!STYLES[cur])cur='apple';render();fit()});
render();
