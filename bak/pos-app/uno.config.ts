// UnoCSS：presetApplet 让同一套类名在 H5 / App / 小程序都可用（与 ai-shop 一致）。
// 颜色、圆角、尺寸全部映射到 CSS 变量 —— 切换布局、组件风格、主题色、深浅时只改变量，不重编译。
import { defineConfig, definePreset, transformerDirectives } from "unocss";
import { presetApplet, transformerApplet } from "unocss-applet";

const v = (name: string) => `var(--${name})`;

// uni-h5 按窗口宽度改写 html 的 font-size（820 宽时约 35px），rem 间距会跟着屏宽放大。
// POS 平板要求控件尺寸固定，所以把预设产出的 rem 一律换成 px（1rem = 16px）。
const remToPx = definePreset(() => ({
  name: "pos-rem-to-px",
  postprocess: (util) => {
    util.entries.forEach((e) => {
      if (typeof e[1] === "string") e[1] = e[1].replace(/(-?[.\d]+)rem/g, (_, n) => `${+n * 16}px`);
    });
  },
}));

export default defineConfig({
  presets: [presetApplet(), remToPx()],
  // presetApplet 会把含 [ ] ( ) 等字符的类名改写成 _a_ 形式，必须配 transformerApplet 同步改模板，否则 h-[56px] 之类不生效。
  // 不开 variant-group：它会把 grid-cols-[repeat(auto-fill,minmax(…))] 里的括号当成分组语法改写
  transformers: [transformerApplet(), transformerDirectives()],
  theme: {
    colors: {
      primary: v("sh-primary"), "on-primary": v("sh-on-primary"), "primary-tint": v("sh-primary-tint"), "primary-text": v("sh-primary-text"),
      bg: v("sh-bg"), surface: v("sh-surface"), elev: v("sh-elev"), ink: v("sh-ink"), sub: v("sh-sub"), faint: v("sh-faint"), line: v("sh-line"),
      success: v("sh-success"), "success-tint": v("sh-success-tint"), "success-text": v("sh-success-text"),
      warning: v("sh-warning"), "warning-tint": v("sh-warning-tint"), "warning-text": v("sh-warning-text"),
      danger: v("sh-danger"), "danger-tint": v("sh-danger-tint"), "danger-text": v("sh-danger-text"),
      // POS 语义色：由「组件风格」决定取哪一种（见 src/theme/vars.ts）
      "sel-bg": v("pos-sel-bg"), "sel-fg": v("pos-sel-fg"), "on-bg": v("pos-on-bg"), "on-fg": v("pos-on-fg"),
      "send-bg": v("pos-send-bg"), "send-fg": v("pos-send-fg"), "pay-bg": v("pos-pay-bg"), "pay-fg": v("pos-pay-fg"),
      "count-bg": v("pos-count-bg"), "count-fg": v("pos-count-fg"),
      "bar-bg": v("pos-bar-bg"), "bar-fg": v("pos-bar-fg"), "bar-sub": v("pos-bar-sub"), "bar-hover": v("pos-bar-hover"),
    },
    borderRadius: {
      panel: v("r-panel"), tile: v("r-tile"), ctl: v("r-ctl"), btn: v("r-btn"), chip: v("r-chip"), full: "9999px",
    },
  },
  // 描边 / 选中环都用 inset 阴影，不占盒模型；pos-sel 排在后面，同时出现时覆盖 pos-frame
  rules: [
    ["pos-frame", { "box-shadow": "inset 0 0 0 var(--pos-bw) var(--pos-line), var(--pos-shadow)" }],
    ["pos-sel", { "box-shadow": "inset 0 0 0 2px var(--pos-sel-ring)" }],
  ],
  shortcuts: {
    // 按钮：形状由组件风格的 --r-btn 决定（胶囊 / 圆角 / 方角）
    "pos-btn": "flex items-center justify-center gap-1.5 h-[var(--pos-ctl)] px-4 rounded-btn text-[var(--pos-fs)] font-600 whitespace-nowrap",
    "pos-btn-sm": "flex items-center justify-center gap-1 h-[var(--pos-ctl-s)] px-3 rounded-btn text-[13px] font-500 whitespace-nowrap",
    "pos-chip": "inline-flex items-center gap-1 h-[22px] px-2 rounded-chip text-[12px] font-500 whitespace-nowrap bg-faint text-sub",
    "pos-panel": "bg-surface rounded-panel pos-frame",
    "pos-num": "tabular-nums",
  },
});
