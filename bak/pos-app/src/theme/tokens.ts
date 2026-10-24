/**
 * 设计令牌。三类可切换的东西都在这里定义，互相独立：
 * - 布局（LAYOUTS）：导航区与详情区的位置，决定三栏网格怎么排；
 * - 组件风格（STYLES）：圆角、描边、阴影、字体，以及选中态 / 按钮 / 顶栏用哪种颜色处理；
 * - 主题色（SKINS）与深浅：底色固定黑白灰（ai-shop 纯白面感），只换主题色。
 */

export type NavPos = "left" | "top" | "right";
export type DetailPos = "left" | "right";
export type LayoutId = "L1" | "L2" | "L3" | "L4" | "L5" | "L6";

export const LAYOUTS: Record<LayoutId, { nav: NavPos; detail: DetailPos; note: string }> = {
  L1: { nav: "left", detail: "right", note: "导航左 · 详情右" },
  L2: { nav: "top", detail: "right", note: "导航上 · 详情右" },
  L3: { nav: "top", detail: "left", note: "导航上 · 详情左" },
  L4: { nav: "right", detail: "left", note: "导航右 · 详情左" },
  L5: { nav: "left", detail: "left", note: "导航左 · 详情左" },
  L6: { nav: "right", detail: "right", note: "导航右 · 详情右" },
};

/** ai-shop packages/shared/src/design/tokens.ts 的纯白面感（pure） */
export const NEUTRAL = {
  light: { bg: "#ECEDEF", surface: "#FFFFFF", elev: "#FFFFFF", ink: "#17181A", sub: "#63676E", faint: "#E4E5E8", line: "#DCDEE2" },
  dark: { bg: "#000000", surface: "#161719", elev: "#1D2023", ink: "#F3F4F6", sub: "#9AA1AC", faint: "#23262B", line: "#2E3036" },
};

/** ai-shop SKIN_HEX 的主题色：浅色 / 深色主色、主色文字色（deep）、主色上的前景色 */
export type SkinId = "mono" | "brand" | "blue" | "fresh" | "teal" | "amber" | "violet";
export const SKINS: Record<SkinId, { name: string; light: string; dark: string; text: [string, string]; on: [string, string] }> = {
  mono: { name: "墨黑", light: "#18181B", dark: "#F2F3F5", text: ["#18181B", "#F2F3F5"], on: ["#FFFFFF", "#14161A"] },
  brand: { name: "虹选红", light: "#E1251B", dark: "#FF5A4D", text: ["#B31710", "#FF5A4D"], on: ["#FFFFFF", "#14161A"] },
  blue: { name: "经典蓝", light: "#2C69FF", dark: "#5B8CFF", text: ["#2860E8", "#5B8CFF"], on: ["#FFFFFF", "#14161A"] },
  fresh: { name: "微信绿", light: "#00B578", dark: "#00B578", text: ["#007A51", "#00B578"], on: ["#14161A", "#14161A"] },
  teal: { name: "青碧", light: "#008484", dark: "#00A3A3", text: ["#007777", "#00A3A3"], on: ["#FFFFFF", "#14161A"] },
  amber: { name: "琥珀", light: "#A46911", dark: "#E8961B", text: ["#945F0F", "#E8961B"], on: ["#FFFFFF", "#14161A"] },
  violet: { name: "紫", light: "#7A4CE0", dark: "#9A78E8", text: ["#7A4CE0", "#9A78E8"], on: ["#FFFFFF", "#14161A"] },
};

/**
 * 组件风格。每一项只描述「形状」和「颜色怎么用」，不带具体色值 —— 色值来自主题色与底色。
 * sel：已点菜品 / 当前选中的处理；active：导航与筛选的选中；send / pay / count：按钮与角标；bar：系统栏。
 */
export type StyleId = "bapp" | "mono" | "line" | "classic";
type Fill = "tint" | "ink" | "faint" | "brand" | "outline" | "slate" | "green" | "red" | "surface" | "dark";
export const STYLES: Record<StyleId, {
  name: string; note: string;
  radius: { panel: number; tile: number; ctl: number; btn: number; chip: number };
  border: boolean; shadow: boolean; font: string;
  sel: Fill; active: Fill; send: Fill; pay: Fill; count: Fill; bar: Fill; catTiles: boolean;
}> = {
  bapp: {
    name: "B 端色块", note: "ai-shop B 端：tint 色块分层、胶囊按钮、无描边",
    radius: { panel: 16, tile: 12, ctl: 12, btn: 999, chip: 999 }, border: false, shadow: false, font: "Inter",
    sel: "tint", active: "tint", send: "tint", pay: "brand", count: "brand", bar: "surface", catTiles: false,
  },
  mono: {
    name: "极简黑白", note: "UnoCSS 风格：灰阶色块、墨色选中、方圆角",
    radius: { panel: 10, tile: 8, ctl: 8, btn: 8, chip: 999 }, border: false, shadow: false, font: "DM Sans",
    sel: "faint", active: "ink", send: "ink", pay: "brand", count: "ink", bar: "surface", catTiles: false,
  },
  line: {
    name: "细线卡片", note: "Tailwind 风格：1px 细线、轻阴影、小圆角",
    radius: { panel: 8, tile: 6, ctl: 6, btn: 6, chip: 6 }, border: true, shadow: true, font: "Geist",
    sel: "outline", active: "ink", send: "faint", pay: "brand", count: "brand", bar: "surface", catTiles: false,
  },
  classic: {
    name: "经典收银", note: "主流 POS：深色顶栏、菜品按分类上色、绿色收款",
    radius: { panel: 6, tile: 6, ctl: 6, btn: 6, chip: 4 }, border: false, shadow: true, font: "Manrope",
    sel: "outline", active: "brand", send: "slate", pay: "green", count: "red", bar: "dark", catTiles: true,
  },
};

export type Density = "compact" | "standard";
export const DENSITY: Record<Density, { name: string; ctl: number; ctlS: number; tileH: number; gap: number; fs: number }> = {
  compact: { name: "紧凑", ctl: 44, ctlS: 36, tileH: 88, gap: 8, fs: 14 },
  standard: { name: "标准", ctl: 48, ctlS: 40, tileH: 104, gap: 12, fs: 15 },
};

/** 菜品分类色（只在「经典收银」风格下给菜品卡上色） */
export const CAT_COLORS: Record<string, string> = {
  hot: "#1F2937", sig: "#B91C1C", cold: "#0F766E", wok: "#C2410C", staple: "#A16207", soup: "#6D28D9", drink: "#0369A1", set: "#374151",
};
