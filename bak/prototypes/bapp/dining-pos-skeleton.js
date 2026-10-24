/* 全线点餐 POS · UI 骨架：四个开关（布局、主题色、形状、深浅）和功能区标注，都写在 <html> 的属性上；本地记住选择。 */
(function () {
  var root = document.documentElement, KEY = 'dining-skeleton';
  var state = { nav: 'left', detail: 'right', skin: 'mono', shape: 'native', mode: 'light', zones: 'off' };
  try { var saved = JSON.parse(localStorage.getItem(KEY) || '{}'); for (var k in saved) if (k in state) state[k] = saved[k]; } catch (e) {}
  function press(sel, test) { Array.prototype.forEach.call(document.querySelectorAll(sel), function (b) { b.setAttribute('aria-pressed', String(test(b))); }); }
  function apply() {
    root.setAttribute('data-nav', state.nav); root.setAttribute('data-detail', state.detail); root.setAttribute('data-skin', state.skin);
    root.setAttribute('data-shape', state.shape); root.setAttribute('data-mode', state.mode); root.setAttribute('data-zones', state.zones);
    press('.pd-switch [data-layout]', function (b) { return b.dataset.layout === state.nav + '-' + state.detail; });
    press('.pd-switch [data-skin]', function (b) { return b.dataset.skin === state.skin; });
    press('.pd-switch [data-shape]', function (b) { return b.dataset.shape === state.shape; });
    press('.pd-switch [data-mode]', function (b) { return b.dataset.mode === state.mode; });
    press('.pd-switch [data-zones-toggle]', function () { return state.zones === 'on'; });
    try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {}
  }
  document.addEventListener('click', function (e) {
    var b = e.target.closest('.pd-switch button'); if (!b) return;
    if (b.dataset.layout) { var p = b.dataset.layout.split('-'); state.nav = p[0]; state.detail = p[1]; }
    else if (b.dataset.skin) state.skin = b.dataset.skin;
    else if (b.dataset.shape) state.shape = b.dataset.shape;
    else if (b.dataset.mode) state.mode = b.dataset.mode;
    else if (b.hasAttribute('data-zones-toggle')) state.zones = state.zones === 'on' ? 'off' : 'on';
    apply();
  });
  apply();
})();
