<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";

onLaunch(() => {
  // 骨架无需启动逻辑；布局与风格设置在 stores/ui.ts 里读取本地存储
});
</script>

<style>
/* 全局只放依赖 CSS 变量、UnoCSS 不方便表达的几条；其余样式都写在模板的 UnoCSS 类里 */
page { background: #ecedef; }
.pos-root { font-family: var(--pos-font); font-size: var(--pos-fs); color: var(--sh-ink); background: var(--sh-bg); line-height: 1.4; }
/* 功能区标注（设置里「标注功能区」） */
.zone { position: relative; }
.zones-on .zone { outline: 1.5px dashed var(--sh-primary); outline-offset: -1.5px; }
.zones-on .zone::after {
  content: attr(data-zone); position: absolute; top: 4px; right: 4px; z-index: 5; padding: 1px 6px; border-radius: 4px;
  background: var(--sh-primary); color: var(--sh-on-primary); font-size: 10px; font-weight: 600; line-height: 16px; pointer-events: none;
}
</style>
