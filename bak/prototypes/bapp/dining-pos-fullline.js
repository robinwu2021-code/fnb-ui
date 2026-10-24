/* 全线点餐 POS 原型：只做主题色切换（底色固定黑白灰）。本地记住选择，读写失败时用默认墨黑。 */
(function () {
  var root = document.documentElement, buttons = document.querySelectorAll('[data-skin]');
  function apply(skin) {
    root.setAttribute('data-skin', skin);
    Array.prototype.forEach.call(document.querySelectorAll('.pd-skins button'), function (b) { b.setAttribute('aria-pressed', String(b.dataset.skin === skin)); });
  }
  try { var saved = localStorage.getItem('dining-fullline-skin'); if (saved) apply(saved); } catch (e) {}
  Array.prototype.forEach.call(document.querySelectorAll('.pd-skins button'), function (b) {
    b.addEventListener('click', function () { apply(b.dataset.skin); try { localStorage.setItem('dining-fullline-skin', b.dataset.skin); } catch (e) {} });
  });
})();
