// 把原型里由 tools/gen-colors.py 生成的两份产物同步进 React 工程：
//   prototypes/bapp/pos-colors.css → src/styles/pos-colors.css（去掉只给 .pc 原型用的第四层接入段）
//   prototypes/bapp/pos-states.js  → src/data/pos-states.json
// 改颜色、改状态先改 gen-colors.py，再跑 `npm run sync:tokens`，不要手改这两份产物。
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
const here = dirname(fileURLToPath(import.meta.url));
const bapp = resolve(here, '../../bapp');
let css = readFileSync(resolve(bapp, 'pos-colors.css'), 'utf8');
// 第四层「接入旧令牌」是原型 .pc 作用域专用，React 工程直接用 --c-* 角色，不需要
css = css.split('/* ── 第四层')[0].replace(/^\[data-tone="quiet"\] \.pc .*$/m, '');
css = '/* 由 scripts/sync-tokens.mjs 从 prototypes/bapp/pos-colors.css 同步，不要手改。 */\n' + css;
writeFileSync(resolve(here, '../src/styles/pos-colors.css'), css);
const js = readFileSync(resolve(bapp, 'pos-states.js'), 'utf8');
const w = {};
new Function('window', js)(w);
writeFileSync(resolve(here, '../src/data/pos-states.json'), JSON.stringify(w.POS_STATES, null, 0));
console.log('synced pos-colors.css + pos-states.json');
