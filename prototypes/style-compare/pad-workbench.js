/* HTML 2.0 tablet workbenches. Loaded only by the 2.0 prototype viewer. */
(function () {
  if (window.PAD2) return;
  window.PAD2 = true;
  var oldRender = render, oldDock = dock, oldFit = fit;
  var drafts = {}, contexts = {}, orderFields = ['note','member','cpn','manual','pAct','paid','pv','pay','got'];
  var clone = function (x) { return JSON.parse(JSON.stringify(x)); };
  var defaults = {}; orderFields.forEach(function (k) { defaults[k] = clone(S[k] === undefined ? null : S[k]); });
  var active = S.table;
  drafts[active] = LINES;
  TABLES.forEach(function (t) { t.area = t.id[0] === 'V' ? '包间' : '大厅'; });
  S.padArea = '全部'; S.padStatus = 'all'; S.padTableQuery = ''; S.padQuery = ''; S.padFilter = 'all';
  Object.assign(EN, {'待清台':'To clear','桌台总览':'Tables overview','找到桌台，开始服务':'Find a table and start service','全部桌台':'All tables','全部桌区':'All areas','搜索桌号':'Search table number','全部状态':'All statuses','上菜超时':'Dishes overdue','预订':'Reservations','本桌菜品':'Table items','继续点餐':'Continue ordering','开台并点餐':'Open table and order','到店开台':'Seat reservation','启用桌台':'Enable table','清台完成':'Mark as cleared','各桌草稿独立保存':'Drafts saved per table','开台人数按座位数初始化':'Guest count starts from capacity','搜索菜名或英文名':'Search dishes or English names','辣味':'Spicy','返回桌台':'Back to tables','试试其他筛选或关键词':'Try another filter or keyword','没有符合条件的桌台':'No matching tables','没有找到菜品':'No dishes found','座位':'Seats','用餐人数':'Guests','用餐时长':'Dining time','设计调试与其他工作台':'Design settings and other workspaces','此桌提供状态示例；进入点餐可创建本桌草稿。':'Sample table. Open ordering to create a draft.'});
  RULES.unshift([/^为 (.+) 点餐$/, 'Order for $1'],[/^(\d+) 人桌$/, '$1 seats'],[/^(\d+) 位客人$/, '$1 guests'],[/^(\d+) 张桌台$/, '$1 tables'],[/^(\d+) 道菜$/, '$1 dishes'],[/^(\d+) 位$/, '$1 guests'],[/^(\d+) 人$/, '$1 guests'],[/^(\d+) 分钟$/, '$1 min'],[/^(\d+) 项$/, '$1 items']);
  var baseSysbar=sysbar;
  sysbar=function(){return baseSysbar().replace('</header>','<button type="button" class="sh-btn sh-btn--muted pad-lang" data-k="lang" data-v="'+(S.lang==='en'?'zh':'en')+'" aria-label="'+(S.lang==='en'?'Switch to Chinese':'Switch to English')+'">'+(S.lang==='en'?'中文':'English')+'</button></header>');};
  S.names='one';
  S.nav = 'left'; S.detail = 'right'; S.density = 'standard'; S.wb = new URLSearchParams(parent.location.search).get('wb') === 'order' ? 'order' : 'tables';
  function escape(s) { return String(s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];}); }
  function sync() { drafts[active] = LINES; contexts[active] = {}; orderFields.forEach(function (k) { contexts[active][k] = clone(S[k] === undefined ? null : S[k]); }); }
  function select(id) { sync(); active = S.table = id; LINES = drafts[id] || (drafts[id] = []); var context = contexts[id] || defaults; orderFields.forEach(function(k){ S[k] = clone(context[k]); }); S.batch = false; S.sel = []; S.ov = 'none'; S.lact = -1; }
  function current() { return TABLES.find(function(t){ return t.id === S.table; }); }
  function input(key, placeholder) { return '<label class="sh-search pad-search">'+ico('search')+'<input aria-label="'+placeholder+'" data-pad-input="'+key+'" value="'+escape(S[key] || '')+'" placeholder="'+(S.lang==='en'?(tr(placeholder)||placeholder):placeholder)+'"></label>'; }
  function filters(items,value,action) { return '<div class="pad-filters">'+items.map(function(i){return '<button type="button" class="sh-filter '+(i[0]===value?'is-on':'')+'" data-act="'+action+':'+i[0]+'" aria-pressed="'+(i[0]===value)+'">'+i[1]+'</button>';}).join('')+'</div>'; }
  function heading(title,sub) { return '<div class="pad-heading"><div><h2>'+title+'</h2><p>'+sub+'</p></div></div>'; }
  function empty(text) { return '<div class="sh-empty"><b>'+text+'</b><span>试试其他筛选或关键词</span></div>'; }
  function rail2(items,value,action) { return rail(items.map(function(i){return {v:i[0],label:i[1],em:i[2]};}),value,action); }
  function tableAmount(t) { if (t.id===active) return totals().due; if(drafts[t.id]) { var save=LINES, previous={}; orderFields.forEach(function(k){previous[k]=S[k];S[k]=(contexts[t.id]||defaults)[k];}); LINES=drafts[t.id]; var due=totals().due; LINES=save; orderFields.forEach(function(k){S[k]=previous[k];}); return due; } return t.amt; }
  function tile(t) { var busy=busyTable(t.st), alert=['late','due','partial'].indexOf(t.st)>-1;
    return '<button type="button" class="dn-table '+(t.id===S.table?'is-cur ':'')+(alert?'is-alert ':'')+'" style="'+stv(stGroup('table',t.st))+'" data-act="table:'+t.id+'" aria-pressed="'+(t.id===S.table)+'"><div class="pad-tile-top"><b>'+t.id+'</b><small>'+t.seats+' 人桌</small></div>'+stx('table',t.st)+'<div class="pad-tile-bottom"><small>'+(busy?t.ppl+' 人 · '+t.min+' 分钟':t.area)+'</small>'+(busy?'<strong>'+money(tableAmount(t))+'</strong>':'')+'</div></button>';
  }
  function tables2() {
    var area=TABLES.filter(function(t){return S.padArea==='全部'||t.area===S.padArea;});
    var groups={all:function(){return true;},free:function(t){return t.st==='free';},dining:function(t){return ['dining','seated','late','shared'].indexOf(t.st)>-1;},due:function(t){return ['due','partial'].indexOf(t.st)>-1;}};
    var list=area.filter(function(t){return (groups[S.padStatus]?groups[S.padStatus](t):t.st===S.padStatus)&&t.id.toLowerCase().includes(S.padTableQuery.toLowerCase());});
    var stats=[['all','全部桌台'],['free','空闲'],['dining','用餐中'],['due','待结账']].map(function(i){return '<button class="pad-stat '+(S.padStatus===i[0]?'is-on':'')+'" data-act="padstatus:'+i[0]+'"><small>'+i[1]+'</small><b>'+area.filter(groups[i[0]]).length+'</b></button>';}).join('');
    var nav=rail2([['全部','全部桌区',TABLES.length],['大厅','大厅',12],['包间','包间',6]],S.padArea,'padarea');
    var head=heading('桌台总览','找到桌台，开始服务')+'<div class="pad-stats">'+stats+'</div><div class="pad-toolbar">'+input('padTableQuery','搜索桌号')+filters([['all','全部状态'],['late','上菜超时'],['to_clear','待清台'],['booked','预订']],S.padStatus,'padstatus')+'</div>';
    var main=panel(head,'<div class="pad-caption">'+S.padArea+' · '+list.length+' 张桌台</div><div class="ui-tables">'+(list.map(tile).join('')||empty('没有符合条件的桌台'))+'</div>','','pad-main');
    var t=current(), busy=busyTable(t.st), known=Object.prototype.hasOwnProperty.call(drafts,t.id);
    var body='<div class="sh-cells"><div class="sh-cell"><span class="k">桌区</span><span>'+t.area+'</span></div><div class="sh-cell"><span class="k">'+(busy?'用餐人数':'座位')+'</span><span>'+(busy?t.ppl:t.seats)+' 位</span></div>'+(busy?'<div class="sh-cell"><span class="k">用餐时长</span><span>'+t.min+' 分钟</span></div>':'')+'</div>';
    if(busy) body+='<div class="dn-block"><div class="dn-sec">本桌菜品 · '+(known?LINES.length:0)+' 项</div>'+(known?LINES.map(function(l,i){return oline(l,i,false);}).join(''):'<p class="sh-help">此桌提供状态示例；进入点餐可创建本桌草稿。</p>')+'</div>';
    var action=t.st==='disabled'?btn('启用桌台','sh-btn--block sh-btn--soft','padenable'):t.st==='to_clear'?btn('清台完成','sh-btn--block','cleartable'):busy?btn('继续点餐','sh-btn--block','goorder'):btn(t.st==='booked'?'到店开台':'开台并点餐','sh-btn--block','seat');
    return nav+main+ledger('<div class="pad-detail-title"><b>'+t.id+'</b>'+stx('table',t.st)+'</div>',body,dueFoot(busy?'未结金额':'',busy?money(tableAmount(t)):'',busy?'各桌草稿独立保存':'开台人数按座位数初始化',action));
  }
  function order2() {
    var nav=rail2(CATS.map(function(c){return [c[0],c[0]];}),S.cat,'cat');
    var query=S.padQuery.trim().toLowerCase();
    var list=DISHES.filter(function(d){return (query?true:S.cat==='热销'?d.hot:d.c===S.cat)&&(!query||[d.n,d.en].join(' ').toLowerCase().includes(query))&&(S.padFilter==='all'||(d.tg||[]).includes(S.padFilter==='spicy'?'辣':'素'));});
    var t=current();
    var head=heading('为 '+S.table+' 点餐',t.area+' · '+t.ppl+' 位客人 · 堂食')+input('padQuery','搜索菜名或英文名')+'<div class="pad-toolbar">'+filters([['all','全部'],['spicy','辣味'],['veg','素食']],S.padFilter,'padfilter')+'<span class="sh-help">'+list.length+' 道菜</span><button class="sh-btn sh-btn--muted sh-btn--sm" data-k="wb" data-v="tables">返回桌台</button></div>';
    return nav+panel(head,'<div class="dn-grid">'+(list.map(dishCard).join('')||empty('没有找到菜品'))+'</div>','','ui-pic pad-main')+dock();
  }
  dock=function(){var html=oldDock();var quick=quickBar();html=html.replace(quick,'<details class="pad-quick-disclosure"><summary>'+(S.lang==='en'?'Actions':'快捷操作')+'</summary>'+quick+'</details>');var t=current();html=html.replace('4 人 · 48 分钟 · 堂食 · #1024',t.ppl+' 人 · '+t.min+' 分钟 · 堂食');if(newCount()){html=html.replace('sh-btn sh-btn--soft" data-act="send"','sh-btn sh-btn--block" data-act="send"').replace('sh-btn sh-btn--block" data-act="gopay"','sh-btn sh-btn--muted" data-act="gopay"');}return html;};
  MODULES.tables.render=tables2; MODULES.order.render=order2;
  ACT.table=function(id){select(id);}; ACT.padarea=function(v){S.padArea=v;}; ACT.padstatus=function(v){S.padStatus=v;}; ACT.padfilter=function(v){S.padFilter=v;};
  ACT.seat=function(){var t=current();if(t.st!=='free'&&t.st!=='booked'){toast('此桌不能重复开台');return;}LINES=[];drafts[t.id]=LINES;t.st='seated';t.ppl=t.seats;t.min=0;t.amt=0;S.wb='order';toast(t.id+' 已开台');};
  ACT.cleartable=function(){var t=current();if(t.st!=='to_clear'){toast('仅待清台桌台可清台');return;}t.st='free';t.amt=0;LINES=[];sync();toast(t.id+' 已清台');};
  ACT.padenable=function(){var t=current();t.st='free';t.amt=0;toast(t.id+' 已启用');};
  render=function(){sync();oldRender();dev.dataset.padWb=S.wb;fit();};
  window.padOriginalSwitches = switches;
  switches=function(){return '<details class="pad-debug"><summary>设计调试与其他工作台</summary>'+window.padOriginalSwitches()+'</details>';};
  fit=function(){if(S.wb==='tables'||S.wb==='order'){dev.style.transform='none';stage.style.height=Math.max(480,parent.innerHeight-parent.document.querySelector('header').offsetHeight-stage.getBoundingClientRect().top-window.scrollY-24)+'px';dev.style.width='100%';dev.style.height='100%';}else{dev.style.width='1280px';dev.style.height='800px';oldFit();}};
  document.addEventListener('input',function(e){var key=e.target.dataset.padInput;if(!key||e.isComposing)return;var pos=e.target.selectionStart;S[key]=e.target.value;render();var el=dev.querySelector('[data-pad-input="'+key+'"]');el.focus();el.setSelectionRange(pos,pos);});
  document.addEventListener('compositionstart',function(){window.padComposing=true;});
  document.addEventListener('compositionend',function(e){window.padComposing=false;if(e.target.dataset.padInput)e.target.dispatchEvent(new Event('input',{bubbles:true}));});
  var resize=new ResizeObserver(function(){fit();});resize.observe(document.body);
  document.documentElement.classList.add('pad-v2');render();fit();
})();
