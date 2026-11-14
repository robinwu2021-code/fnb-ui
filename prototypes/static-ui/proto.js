// 说明栏点一项 → 画面上对应标注闪一下；「隐藏标注」开关
document.querySelectorAll('.notes li[data-n]').forEach(li => li.addEventListener('click', () => {
  document.querySelectorAll(`.device .mk[data-n="${li.dataset.n}"]`).forEach(el => {
    el.classList.remove('flash'); void el.offsetWidth; el.classList.add('flash');
    el.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  });
}));
document.getElementById('toggle')?.addEventListener('click', e => {
  document.body.classList.toggle('hide');
  e.target.textContent = document.body.classList.contains('hide') ? '显示标注' : '隐藏标注';
});

// 布局切换：分散（现状）/ 中间竖条
document.querySelectorAll('.layout span').forEach(s => s.addEventListener('click', () => {
  document.querySelectorAll('.layout span').forEach(x => x.classList.toggle('on', x === s));
  document.body.classList.toggle('rail-on', s.dataset.l === 'rail');
  document.body.classList.toggle('hyb-on', s.dataset.l === 'hybrid');
  try { localStorage.setItem('proto-layout', s.dataset.l); } catch (e) {}
}));
try { const l = localStorage.getItem('proto-layout'); if (l && l !== 'scatter') document.querySelector(`.layout span[data-l="${l}"]`)?.click(); } catch (e) {}

// 账单在右（App 现状）/ 在左（参考实机）
document.querySelectorAll('.sidebtn span').forEach(s => s.addEventListener('click', () => {
  document.querySelectorAll('.sidebtn span').forEach(x => x.classList.toggle('on', x === s));
  document.body.classList.toggle('left-bill', s.dataset.s === 'left');
  try { localStorage.setItem('proto-side', s.dataset.s); } catch (e) {}
}));
try { if (localStorage.getItem('proto-side') === 'left') document.querySelector('.sidebtn span[data-s="left"]')?.click(); } catch (e) {}

// 竖条底「更多 ›」：点开 / 点别处收起
document.querySelectorAll('.rail2 .moreb').forEach(b => b.addEventListener('click', e => { e.stopPropagation(); b.closest('.rail2').classList.toggle('open'); }));
document.addEventListener('click', () => document.querySelectorAll('.rail2.open').forEach(r => r.classList.remove('open')));
