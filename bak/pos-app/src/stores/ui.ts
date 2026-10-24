import { computed, reactive, watch } from "vue";
import { LAYOUTS, type Density, type LayoutId, type SkinId, type StyleId } from "@/theme/tokens";
import { cssVars, styleString } from "@/theme/vars";

/** 本机显示设置：布局、组件风格、主题色、深浅、密度、功能区标注。持久化到 uni 存储。 */
export interface UiSettings {
  layout: LayoutId; style: StyleId; skin: SkinId; mode: "light" | "dark"; density: Density; zones: boolean; view: "grid" | "list";
}

const KEY = "pos-ui";
const DEFAULTS: UiSettings = { layout: "L1", style: "bapp", skin: "mono", mode: "light", density: "compact", zones: false, view: "grid" };

function load(): UiSettings {
  try {
    const saved = uni.getStorageSync(KEY);
    return { ...DEFAULTS, ...(saved && typeof saved === "object" ? saved : {}) };
  } catch {
    return { ...DEFAULTS };
  }
}

export const ui = reactive<UiSettings>(load());
watch(ui, (v) => { try { uni.setStorageSync(KEY, { ...v }); } catch { /* 存储不可用时只在本次会话生效 */ } }, { deep: true });

/** 应用宽度（< 1000 视为竖屏 / 窄屏：导航强制横排，详情区收成底部抽屉） */
export const viewport = reactive({ width: 1280 });
try { viewport.width = uni.getSystemInfoSync().windowWidth; } catch { /* 非 uni 环境 */ }
try { uni.onWindowResize((r) => { viewport.width = r.size.windowWidth; }); } catch { /* 小程序部分端不支持 */ }

export const narrow = computed(() => viewport.width < 1000);
export const layout = computed(() => {
  const l = LAYOUTS[ui.layout];
  return narrow.value ? { nav: "top" as const, detail: l.detail } : l;
});
export const rootStyle = computed(() =>
  styleString(cssVars({ style: ui.style, skin: ui.skin, dark: ui.mode === "dark", density: ui.density })),
);
export const settingsOpen = reactive({ value: false });
