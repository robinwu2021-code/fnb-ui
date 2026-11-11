// 金额工具（领域层用；lib/money 转发这里）。领域层只引自己目录下的文件，不引 @/ 别名、界面和示例数据。
// 币种由界面按门店地区设置（setCurrency）；领域层只负责按当前币种格式化，不做汇率换算。
export interface Currency { code: string; sym: string; dp: number; glyph: string }
let CUR: Currency = { code: 'CNY', sym: '¥', dp: 2, glyph: '¥' };
export const setCurrency = (c: Currency) => { CUR = c; };
export const currency = () => CUR;
export const round2 = (v: number) => Math.round(v * 100) / 100;
export const money = (v: number) => CUR.sym + v.toFixed(CUR.dp);
