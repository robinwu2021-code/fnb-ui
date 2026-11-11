// UnoCSS 主题：只把 pos-colors.css 的角色令牌（--c-*）和形状 / 密度 / 字号变量接进来，组件里不写色值。
import { defineConfig, presetWind, presetIcons, transformerVariantGroup } from 'unocss';

const v = (name: string) => `var(--c-${name})`;
const stateGroup = (g: string) => ({ dot: v(`${g}-dot`), text: v(`${g}-text`), tint: v(`${g}-tint`), border: v(`${g}-border`), strong: v(`${g}-strong`) });

export default defineConfig({
  presets: [
    presetWind({ preflight: false }),
    presetIcons({ scale: 1, extraProperties: { display: 'inline-block', 'vertical-align': 'middle' } }),
  ],
  transformers: [transformerVariantGroup()],
  theme: {
    colors: {
      // 第二层 基本色角色
      canvas: v('canvas'), surface: v('surface'), faint: v('faint'), edge: v('edge'), line: v('line'), strong: v('strong'),
      ink: v('ink'), sub: v('sub'), hint: { DEFAULT: v('hint'), bg: v('hint-bg'), fg: v('hint-fg') }, placeholder: v('placeholder'),
      'control-off': v('control-off'), 'disabled-bg': v('disabled-bg'), 'slot-bg': v('slot-bg'), inverse: v('inverse'), 'on-inverse': v('on-inverse'),
      scrim: v('scrim'), 'on-solid': v('on-solid'), saving: v('saving'), 'check-off': v('check-off'), pick: v('pick'),
      wash: { DEFAULT: v('wash'), strong: v('wash-strong') },
      sel: { bg: v('sel-bg'), fg: v('sel-fg') },
      // 第三层 主题色角色（随 data-skin）
      primary: { DEFAULT: v('primary'), hover: v('primary-hover'), pressed: v('primary-pressed'), tint: v('primary-tint'), 'tint-hover': v('primary-tint-hover'), border: v('primary-border'), text: v('primary-text'), on: v('on-primary') },
      focus: v('focus'),
      mark: { bg: v('mark-bg'), text: v('mark-text') }, due: { bg: v('due-bg'), text: v('due-text') },
      // 状态色角色
      success: stateGroup('success'), warning: stateGroup('warning'), danger: stateGroup('danger'), info: stateGroup('info'),
      // 业务状态：由 StatusTag / StatusTile 用 style 写 --d --t --b --bk --bks（取 --c-st-<组>-*）
      st: { d: 'var(--d)', t: 'var(--t)', b: 'var(--b)', bk: 'var(--bk,var(--c-wash))', bks: 'var(--bks,var(--c-wash))' },
      pay: { DEFAULT: 'var(--pay,var(--c-sub))', fg: 'var(--pay-fg,var(--c-on-solid))' },
    },
    borderRadius: { rc: 'var(--rc)', rt: 'var(--rt)', rb: 'var(--rb)', rk: 'var(--rk)', 'rt-in': 'max(calc(var(--rt) - 2px),2px)', 'rt-in2': 'max(calc(var(--rt) - 4px),2px)', 'rk-in': 'max(calc(var(--rk) - 2px),1px)', chip: 'min(var(--rb),11px)' },
    fontSize: { xs: ['var(--fs-xs)', '1.4'], sm: ['var(--fs-sm)', '1.4'], base: ['var(--fs)', '1.4'], md: ['var(--fs-md)', '1.4'], lg: ['var(--fs-lg)', '1.3'], xl: ['var(--fs-xl)', '1.2'], '2xl': ['var(--fs-2xl)', '1.1'] },
    fontFamily: { sans: 'Inter, "Noto Sans SC", "PingFang SC", system-ui, sans-serif' },
  },
  shortcuts: {
    // 控件高：紧凑 36 / 44 / 52，标准 40 / 48 / 56
    'h-ctl-sm': 'h-$h-sm', 'h-ctl': 'h-$h', 'h-ctl-lg': 'h-$h-lg', 'min-h-ctl-sm': 'min-h-$h-sm', 'min-h-ctl': 'min-h-$h', 'w-ctl-sm': 'w-$h-sm', 'w-ctl': 'w-$h',
    // 间距令牌
    'gap-gap': 'gap-$gap', 'p-pad': 'p-$pad', 'px-pad': 'px-$pad', 'px-px': 'px-$px',
    // 形态：选中 = gray-200 块 + 墨字加粗；已点 = gray-100
    'sel': 'bg-sel-bg text-sel-fg font-600',
    'num': 'tabular-nums',
    'row-between': 'flex items-center justify-between gap-3',
    'ellipsis': 'whitespace-nowrap overflow-hidden text-ellipsis',
    'no-bar': '[scrollbar-width:none]',
    // 字段标签 / 区块标题 / 帮助字
    'gt': 'flex justify-between items-baseline w-full text-xs text-sub tracking-[.02em]',
    'sec': 'flex justify-between items-baseline text-xs font-600 text-sub tracking-[.02em] mb-1.5',
    'help': 'text-xs text-sub',
    'h-title': 'text-lg font-600 tracking-[-.01em]',
  },
  safelist: [],
});
