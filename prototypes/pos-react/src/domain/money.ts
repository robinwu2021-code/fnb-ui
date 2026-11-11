// 金额工具（领域层用；lib/money 转发这里）。领域层只引自己目录下的文件，不引 @/ 别名、界面和示例数据。
export const round2 = (v: number) => Math.round(v * 100) / 100;
export const money = (v: number) => '¥' + v.toFixed(2);
