import { DENSITY, NEUTRAL, SKINS, STYLES, type Density, type SkinId, type StyleId } from "./tokens";

function rgb(hex: string): [number, number, number] {
  const h = hex.replace("#", "");
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)) as [number, number, number];
}
/** a 与 b 按 t 混合（t=0 取 a） */
export function mix(a: string, b: string, t: number): string {
  const x = rgb(a), y = rgb(b);
  return "#" + x.map((v, i) => Math.round(v + (y[i] - v) * t).toString(16).padStart(2, "0")).join("");
}

export interface LookInput { style: StyleId; skin: SkinId; dark: boolean; density: Density }

/** 把「组件风格 × 主题色 × 深浅 × 密度」算成一组 CSS 变量，挂在页面根节点上 */
export function cssVars({ style, skin, dark, density }: LookInput): Record<string, string> {
  const n = dark ? NEUTRAL.dark : NEUTRAL.light;
  const s = SKINS[skin];
  const st = STYLES[style];
  const d = DENSITY[density];
  const i = dark ? 1 : 0;
  const primary = dark ? s.dark : s.light;
  const tint = skin === "mono" ? n.faint : mix(n.surface, primary, dark ? 0.2 : 0.12);
  const ink = { bg: n.ink, fg: n.surface };

  const fill = (f: string): [string, string] => {
    switch (f) {
      case "tint": return [tint, s.text[i]];
      case "ink": return [ink.bg, ink.fg];
      case "faint": return [n.faint, n.ink];
      case "brand": return [primary, s.on[i]];
      case "outline": return [n.surface, n.ink];
      case "slate": return [dark ? "#475569" : "#334155", "#FFFFFF"];
      case "green": return dark ? ["#22C55E", "#052E16"] : ["#15803D", "#FFFFFF"];
      case "red": return ["#DC2626", "#FFFFFF"];
      case "dark": return [dark ? "#05070A" : "#111827", "#F9FAFB"];
      default: return [n.surface, n.ink];
    }
  };
  const [selBg, selFg] = fill(st.sel);
  const [onBg, onFg] = fill(st.active);
  const [sendBg, sendFg] = fill(st.send);
  const [payBg, payFg] = fill(st.pay);
  const [countBg, countFg] = fill(st.count);
  const barDark = st.bar === "dark";
  const [barBg, barFg] = fill(st.bar);

  return {
    "--sh-primary": primary, "--sh-on-primary": s.on[i], "--sh-primary-tint": tint, "--sh-primary-text": s.text[i],
    "--sh-bg": n.bg, "--sh-surface": n.surface, "--sh-elev": n.elev, "--sh-ink": n.ink, "--sh-sub": n.sub, "--sh-faint": n.faint, "--sh-line": n.line,
    "--sh-success": "#12B76A", "--sh-success-tint": "rgba(18,183,106,.14)", "--sh-success-text": dark ? "#47CD89" : "#067647",
    "--sh-warning": "#F79009", "--sh-warning-tint": "rgba(247,144,9,.14)", "--sh-warning-text": dark ? "#FDB022" : "#B54708",
    "--sh-danger": "#F04438", "--sh-danger-tint": "rgba(240,68,56,.14)", "--sh-danger-text": dark ? "#F97066" : "#B42318",
    "--pos-sel-bg": selBg, "--pos-sel-fg": selFg, "--pos-sel-ring": st.sel === "outline" ? primary : st.sel === "faint" ? n.ink : "transparent",
    "--pos-on-bg": onBg, "--pos-on-fg": onFg,
    "--pos-send-bg": sendBg, "--pos-send-fg": sendFg, "--pos-pay-bg": payBg, "--pos-pay-fg": payFg,
    "--pos-count-bg": countBg, "--pos-count-fg": countFg,
    "--pos-bar-bg": barBg, "--pos-bar-fg": barFg, "--pos-bar-sub": barDark ? "#A3ABB8" : n.sub, "--pos-bar-hover": barDark ? "#1F2937" : n.faint,
    "--pos-bw": st.border ? "1px" : "0px", "--pos-line": st.border ? n.line : "transparent",
    "--pos-shadow": st.shadow ? (dark ? "0 1px 2px rgba(0,0,0,.5)" : "0 1px 2px rgba(17,24,39,.08)") : "0 0 0 transparent",
    "--r-panel": px(st.radius.panel), "--r-tile": px(st.radius.tile), "--r-ctl": px(st.radius.ctl), "--r-btn": px(st.radius.btn), "--r-chip": px(st.radius.chip),
    "--pos-ctl": px(d.ctl), "--pos-ctl-s": px(d.ctlS), "--pos-tile-h": px(d.tileH), "--pos-gap": px(d.gap), "--pos-fs": px(d.fs),
    "--pos-font": `"${st.font}", "Noto Sans SC", "PingFang SC", system-ui, sans-serif`,
  };
}

function px(n: number): string { return `${n}px`; }

/** 小程序端 :style 只认字符串，H5 两种都认 —— 统一输出字符串 */
export function styleString(vars: Record<string, string>): string {
  return Object.entries(vars).map(([k, v]) => `${k}:${v}`).join(";");
}
