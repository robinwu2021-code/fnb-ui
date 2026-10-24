/* 原型合规采集（docs/全线点餐POS-原型组件合规评审.md 用）：遍历 pos-ui.html 的 全部画面。
 * 在 pos-ui.html 的控制台里：eval(await (await fetch('tools/audit-ui.js')).text());
 *   await AUD.run()            → 11px 字、可点元素热区 < 36、无标签图标按钮、字符图标、违规行内样式、每屏实心主按钮数
 *   await AUD.diff(a.css, b.css) → 两份样式表下每个元素（含伪元素）的计算样式逐项比对，用于重排 proto.css */
window.AUD = (function () {
  var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
  function sw(k, v) { var b = document.querySelector('#sw [data-k="' + k + '"][data-v="' + v + '"]'); if (!b) throw new Error('no switch ' + k + '=' + v); b.click(); }
  function act(a) { var b = document.querySelector('#dev [data-act="' + a + '"]'); if (!b) throw new Error('no act ' + a); b.click(); }
  var MENU_GROUPS = ['ops', 'tables', 'pay', 'channel', 'staff', 'device', 'store', 'display', 'system'];
  function screens() {
    var L = [];
    function base(o) { return function () { sw('lang', o.lang || 'zh'); sw('banner', o.banner || 'off'); sw('load', o.load || 'off'); sw('pic', o.pic || 'img'); if ((o.pic || 'img') === 'img') sw('card', o.card || 'v'); sw('ov', 'none'); sw('wb', o.wb); }; }
    ['zh', 'en'].forEach(function (lang) {
      ['tables', 'order', 'checkout', 'orders', 'shift'].forEach(function (wb) {
        ['none', 'x1', 'x2', 'x3', 'menu', 'p3', 'p4', 'note', 'hold', 'share', 'reprice', 'coupons', 'manual', 'mnew', 'recharge', 's1'].forEach(function (ov) { L.push({ id: lang + '/' + wb + '/' + ov, run: function () { base({ wb: wb, lang: lang })(); if (ov !== 'none') sw('ov', ov); } }); });
        ['takeout', 'member', 'print', 'net', 'set'].forEach(function (sh) { L.push({ id: lang + '/' + wb + '/sheet:' + sh, run: function () { base({ wb: wb, lang: lang })(); sw('ov', 'sheet'); act('sheet:' + sh); } }); });
      });
      [['img', 'h'], ['off', 'v']].forEach(function (pc) { L.push({ id: lang + '/order/pic:' + pc.join('-'), run: base({ wb: 'order', lang: lang, pic: pc[0], card: pc[1] }) }); });
      ['wx', 'card', 'mix'].forEach(function (p) { L.push({ id: lang + '/checkout/' + p, run: function () { base({ wb: 'checkout', lang: lang })(); act('pay:' + p); } }); });
      L.push({ id: lang + '/checkout/sv', run: function () { base({ wb: 'checkout', lang: lang })(); document.querySelector('[data-scan="member"]').click(); act('pay:sv'); } });
      L.push({ id: lang + '/orders/card', run: function () { base({ wb: 'orders', lang: lang })(); var b = document.querySelector('#dev [data-k="oview"][data-v="card"]'); b.click(); } });
      L.push({ id: lang + '/orders/delivery-card', run: function () { base({ wb: 'orders', lang: lang })(); document.querySelector('#dev [data-k="oview"][data-v="card"]').click(); act('och:外卖'); } });
      L.push({ id: lang + '/orders/due-list', run: function () { base({ wb: 'orders', lang: lang })(); document.querySelector('#dev [data-k="oview"][data-v="list"]').click(); act('och:全部'); act('ost:due'); } });
      L.push({ id: lang + '/order/h', run: base({ wb: 'order', lang: lang, card: 'h' }) });
      [['menu', 'msub', ['菜品', '分类', '规格', '套餐', '同步']], ['members', 'msec', ['会员', '标签', '储值', '活动', '优惠券', '积分']], ['reports', 'rsec', ['概况', '时段', '菜品', '支付', '会员', '优惠', '退赠']]].forEach(function (w) {
        w[2].forEach(function (sec) { L.push({ id: lang + '/' + w[0] + '/' + sec, run: function () { base({ wb: w[0], lang: lang })(); act(w[1] + ':' + sec); } }); });
      });
      L.push({ id: lang + '/order/member', run: function () { base({ wb: 'order', lang: lang })(); document.querySelector('[data-scan="member"]').click(); } });
      L.push({ id: lang + '/checkout/member-coupon', run: function () { base({ wb: 'checkout', lang: lang })(); document.querySelector('[data-scan="member"]').click(); act('pact'); } });
      L.push({ id: lang + '/checkout/cash', run: function () { base({ wb: 'checkout', lang: lang })(); act('pay:cash'); } });
      L.push({ id: lang + '/order/banner', run: base({ wb: 'order', lang: lang, banner: 'on' }) });
      ['order', 'orders'].forEach(function (wb) { L.push({ id: lang + '/' + wb + '/load', run: base({ wb: wb, lang: lang, load: 'on' }) }); });
      L.push({ id: lang + '/order/batch-none', run: function () { base({ wb: 'order', lang: lang })(); act('batch:on'); } });
      L.push({ id: lang + '/order/batch-all', run: function () { base({ wb: 'order', lang: lang })(); act('batch:on'); act('batch:all'); } });
      MENU_GROUPS.forEach(function (g) {
        L.push({ id: lang + '/settings/' + g, run: function () { base({ wb: 'settings', lang: lang })(); act('sg:' + g); }, items: g });
      });
    });
    return L;
  }
  /* 设置组展开到每一项 */
  async function each(fn) {
    var out = [];
    for (var s of screens()) {
      s.run(); await sleep(30);
      if (s.items) {
        var ids = Array.prototype.map.call(document.querySelectorAll('#dev .ui-nav [data-act^="snav:' + s.items + '."]'), function (e) { return e.dataset.act; });
        for (var a of ids) { act(a); await sleep(20); out.push(await fn(s.id + '/' + a.split('.')[1])); }
      } else out.push(await fn(s.id));
    }
    return out;
  }
  function hitSize(el) {
    var r = el.getBoundingClientRect(), sc = r.width / el.offsetWidth || 1, w = el.offsetWidth, h = el.offsetHeight;
    var b = getComputedStyle(el, '::before');
    if (b.content !== 'none' && b.position === 'absolute') { w = Math.max(w, parseFloat(b.width) || 0); h = Math.max(h, parseFloat(b.height) || 0); }
    return [w, h];
  }
  function audit(id) {
    var dev = document.getElementById('dev'), r = { id: id, f11: 0, small: [], nolabel: [], glyph: [], inline: [] , solid: 0 };
    dev.querySelectorAll('*').forEach(function (el) {
      if (el.closest('svg') && el.tagName !== 'svg') return;
      var cs = getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden') return;
      var txt = Array.prototype.some.call(el.childNodes, function (n) { return n.nodeType === 3 && n.nodeValue.trim(); });
      if (txt && parseFloat(cs.fontSize) < 12) r.f11++;
      if (txt && /[⋯⏸✕⌕⌫]/.test(el.textContent)) r.glyph.push(el.className + ':' + el.textContent.trim().slice(0, 12));
      var st = el.getAttribute('style');
      if (st && !el.classList.contains('ui-body') && !/^(\s*--[\w-]+:[^;]+;?)+$/.test(st)) r.inline.push(el.className + ' → ' + st.slice(0, 60));
    });
    dev.querySelectorAll('[data-act],[data-k],.sh-btn').forEach(function (el) {
      if (!el.offsetWidth || el.classList.contains('sh-mask')) return;
      var s = hitSize(el); if (Math.min(s[0], s[1]) < 36) r.small.push((el.className || el.tagName) + ' ' + s.map(Math.round).join('×') + ' ' + el.textContent.trim().slice(0, 8));
      if (!el.textContent.trim() && el.querySelector('svg,i') && !el.getAttribute('aria-label') && !el.classList.contains('dn-swatch') && !el.classList.contains('dn-dish')) r.nolabel.push(el.className);
    });
    var layer = dev.querySelector('.sh-mask:not(.sh-mask--clear)') || dev;
    r.solid = Array.prototype.filter.call(layer.querySelectorAll('.sh-btn'), function (b) { return b.offsetWidth && !/--(soft|muted|danger|off)/.test(b.className) && !(layer === dev && b.closest('.sh-mask')); }).length;
    /* 描边：1px 内描边（选中桌 2px 内圈、危险按钮除外）和分隔线 */
    /* 中性灰底面积：每 8px 取一个点，看最上层有底色的元素是不是中性灰（不算菜品图位、浮层遮罩） */
    var R = dev.getBoundingClientRect(), bd = dev.querySelector('.ui-body').getBoundingClientRect(), st = 8 * R.width / 1280, n = 0, g = 0;
    for (var y = bd.top + 2; y < R.bottom - 2; y += st) for (var x = R.left + 2; x < R.right - 2; x += st) {
      var e = document.elementFromPoint(x, y); if (!e || !dev.contains(e) || e.closest('.pic,.sh-mask,.th,.dn-mdpic')) continue;
      while (e && e !== dev) { var b0 = getComputedStyle(e).backgroundColor.match(/[\d.]+/g); if (b0 && (b0.length < 4 || +b0[3] > 0)) break; e = e.parentElement; }
      if (!e || e === dev) continue; n++; var c = getComputedStyle(e).backgroundColor.match(/[\d.]+/g).map(Number);
      if (Math.max(c[0], c[1], c[2]) - Math.min(c[0], c[1], c[2]) < 8 && c[0] < 250 && c[0] > 150) g++;
    }
    r.gray = n ? Math.round(g / n * 100) : 0;
    r.rings = []; r.lines = 0;
    dev.querySelectorAll('*').forEach(function (el) {
      if (!el.offsetWidth) return; var cs = getComputedStyle(el);
      if (/0px 0px 0px 1px inset/.test(cs.boxShadow) && !el.classList.contains('sh-btn--danger')) r.rings.push(el.className);
      if (parseFloat(cs.borderTopWidth) > 0 || parseFloat(cs.borderBottomWidth) > 0) r.lines++;
    });
    return r;
  }
  /* 样式快照：设备里每个元素（含 ::before / ::after）的全部计算样式 */
  var SKIP = /^(transition|animation|will-change)/;
  function snap() {
    var dev = document.getElementById('dev'), out = [];
    dev.querySelectorAll('*').forEach(function (el, i) {
      var p = el.tagName + '.' + (typeof el.className === 'string' ? el.className : '') + '#' + i;
      [null, '::before', '::after'].forEach(function (ps) {
        var cs = getComputedStyle(el, ps), o = {};
        if (ps && cs.content === 'none') return;
        for (var j = 0; j < cs.length; j++) { var k = cs[j]; if (!SKIP.test(k)) o[k] = cs.getPropertyValue(k); }
        out.push([p + (ps || ''), o]);
      });
    });
    return out;
  }
  var LINK;   /* 第一次调用时记下 proto.css 的 <link>，之后换它的 href */
  function setCss(href) {
    LINK = LINK || document.querySelector('link[href*="proto.css"]');
    return new Promise(function (res) { LINK.onload = function () { setTimeout(res, 30); }; LINK.href = href; });
  }
  async function diff(a, b) {
    var res = { screens: 0, diffs: [] };
    await each(async function (id) {
      await setCss(a); var A = snap(); await setCss(b); var B = snap(); res.screens++;
      if (A.length !== B.length) { res.diffs.push([id, 'count', A.length, B.length]); return; }
      for (var i = 0; i < A.length; i++) {
        if (A[i][0] !== B[i][0]) { res.diffs.push([id, 'path', A[i][0], B[i][0]]); break; }
        for (var k in A[i][1]) if (A[i][1][k] !== B[i][1][k]) { res.diffs.push([id, A[i][0], k, A[i][1][k], B[i][1][k]]); if (res.diffs.length > 200) return; }
      }
    });
    return res;
  }
  async function run() {
    var rs = await each(async function (id) { return audit(id); });
    var sum = { screens: rs.length, f11: 0, small: {}, nolabel: {}, glyph: {}, inline: {}, rings: {}, solidMax: 0, linesMax: 0, grayMax: {} };
    rs.forEach(function (r) {
      sum.f11 += r.f11; sum.solidMax = Math.max(sum.solidMax, r.solid); sum.linesMax = Math.max(sum.linesMax, r.lines); var wbk = r.id.split('/')[1]; sum.grayMax[wbk] = Math.max(sum.grayMax[wbk] || 0, r.gray);
      ['small', 'nolabel', 'glyph', 'inline', 'rings'].forEach(function (k) { r[k].forEach(function (x) { sum[k][x] = (sum[k][x] || 0) + 1; }); });
    });
    return sum;
  }
  return { run: run, diff: diff, each: each, audit: audit };
})();
