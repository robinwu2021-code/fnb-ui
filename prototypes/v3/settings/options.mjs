const allGroups=[
['外观','Appearance',[
['skin','主题皮肤','Skin',[['pine','松绿','Pine'],['mono','墨黑','Ink'],['red','虹选红','Red'],['blue','经典蓝','Blue'],['green','微信绿','Green'],['teal','青碧','Teal'],['amber','琥珀','Amber'],['violet','紫','Violet']]],
['style','组件风格','Component style',[['quiet','Quiet','Quiet'],['base','原版适配','Classic'],['apple','Apple 启发','Apple inspired'],['material','Material 启发','Material inspired'],['uber','Uber 启发','Uber inspired'],['glass','白玻璃','White glass']]],
['frame','容器形状','Container shape',[['square','直角容器','Square panels'],['sharp','微圆容器','Sharp panels'],['soft','轻圆容器','Soft panels'],['round','柔圆容器','Round panels']]],
['button','按钮形状','Button shape',[['square','直角按钮','Square buttons'],['small','微圆按钮','Sharp buttons'],['standard','轻圆按钮','Soft buttons'],['soft','柔圆按钮','Round buttons'],['pill','胶囊按钮','Pill buttons']]],
['density','间距密度','Spacing',[['compact','紧凑','Compact'],['standard','标准','Standard'],['airy','舒展','Airy']]]]],
['布局','Layout',[
['layout','分类 / 账单位置','Categories / order', [['L1','L1 左分类 · 右账单','L1 Categories left · order right'],['L2','L2 上分类 · 右账单','L2 Categories top · order right'],['L3','L3 上分类 · 左账单','L3 Categories top · order left'],['L4','L4 右分类 · 左账单','L4 Categories right · order left'],['L5','L5 下分类 · 右账单','L5 Categories bottom · order right'],['L6','L6 下分类 · 左账单','L6 Categories bottom · order left']]],
['navigation','工作台导航','Workbench navigation',[['top','顶部','Top'],['bottom','底部','Bottom']]],
['actions','账单金额与操作','Order actions',[['bottom','底部固定','Fixed bottom'],['top','顶部固定','Fixed top']]]]],
['菜单呈现','Menu presentation',[
['photos','菜品图片','Dish photos',[['on','有图片','With photos'],['off','无图片','Without photos']]],
]],
['文字与语言','Typography & language',[
['font','Google 字体','Google font',[['manrope','Manrope · 柔和几何','Manrope · Soft geometry'],['inter','Inter · 清晰中性（推荐）','Inter · Neutral (recommended)'],['roboto','Roboto · 终端易读','Roboto · Terminal clarity'],['noto','Noto Sans · 多语言','Noto Sans · Multilingual'],['plex','IBM Plex Sans · 数字辨识','IBM Plex Sans · Distinct numerals'],['outfit','Outfit 标题 + Inter 正文','Outfit headings + Inter body']]],
['scale','字号','Text size',[['standard','标准','Standard'],['large','大字','Large'],['xlarge','特大','Extra large']]],
['names','菜品名称','Dish names',[['one','单语','Single language'],['bi','中英双语','Bilingual']]]]]];

// Keep one entry per preference; toolbar and header own placement and language.
export const groups=[
 allGroups[0],
 ["布局","Layout",[...allGroups[1][2],allGroups[2][2][0]]],
 ["文字","Typography",allGroups[3][2].filter(([key])=>key!=="lang")]
];
