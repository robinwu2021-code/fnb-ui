#!/usr/bin/env python3
# 生成 POS 端架构图两张 SVG：① 技术分层 × 业务模块（含多业态）② 多设备集群与在线 / 离线
# 用法：cd docs && python3 tools/gen-arch.py "全线点餐POS-端架构"   → 生成 -分层.svg、-集群.svg
import sys, html
OUT = sys.argv[1]
FONT = "PingFang SC, Noto Sans SC, Microsoft YaHei, Helvetica, Arial, sans-serif"
INK, SUB, LINE = '#1F2328', '#5F6670', '#C9CDD3'
WASH, WASH2, CARD = '#F2F3F5', '#E6E8EB', '#FFFFFF'
ACC, ACC_T = '#E8F0FE', '#1D4ED8'      # 业态配置
OFF, OFF_T = '#FFF1DC', '#8F5000'      # 离线 / 局域网
NET, NET_T = '#E3F5EC', '#0B6B3A'      # 互联网 / 云
DNG, DNG_T = '#FDE7E5', '#B42318'

def esc(s): return html.escape(s, quote=True)
class S:
    def __init__(self, w, h): self.w, self.h, self.o = w, h, []
    def rect(self, x, y, w, h, fill, r=8, stroke=None, dash=False):
        st = f' stroke="{stroke}" stroke-width="1.5"' + (' stroke-dasharray="6 4"' if dash else '') if stroke else ''
        self.o.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"{st}/>')
    def text(self, x, y, s, size=13, fill=INK, weight=400, anchor='start'):
        self.o.append(f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}" font-weight="{weight}" text-anchor="{anchor}">{esc(s)}</text>')
    def lines(self, x, y, arr, size=12, fill=INK, weight=400, anchor='middle', lh=None):
        lh = lh or size + 4; y0 = y - (len(arr) - 1) * lh / 2
        for i, s in enumerate(arr): self.text(x, y0 + i * lh + size * 0.35, s, size, fill, weight if i == 0 else 400, anchor)
    def box(self, x, y, w, h, title, sub=None, fill=CARD, tc=INK, r=8, size=13, stroke=None, dash=False):
        self.rect(x, y, w, h, fill, r, stroke, dash)
        arr = [title] + (sub if isinstance(sub, list) else [sub] if sub else [])
        self.lines(x + w / 2, y + h / 2, arr, size, tc, 600)
    def line(self, x1, y1, x2, y2, c=LINE, w=1.5, dash=False, arrow=False):
        d = ' stroke-dasharray="6 4"' if dash else ''
        m = ' marker-end="url(#ar)" marker-start="url(#ar)"' if arrow else ''
        self.o.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{c}" stroke-width="{w}"{d}{m}/>')
    def svg(self):
        head = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" viewBox="0 0 {self.w} {self.h}" font-family="{FONT}">'
                '<defs><marker id="ar" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#8A9099"/></marker></defs>'
                f'<rect width="100%" height="100%" fill="{CARD}"/>')
        return head + ''.join(self.o) + '</svg>'

# ---------------- 图 1：技术分层 × 业务模块 ----------------
W = 1320; s = S(W, 860)
s.text(24, 38, '全线点餐 POS 端架构 ① 技术分层 × 业务模块（多业态）', 20, INK, 700)
s.text(24, 60, '横向是技术分层（上层只依赖下层）；竖向是业务模块（同一模块从界面到数据一竖条）；业态配置贯穿界面与领域两层', 12, SUB)
LX, LW, X0 = 24, 150, 190; CW = W - X0 - 24
COLS = ['桌台', '点餐 · 收银', '结账 · 支付', '订单 · 售后', '菜品 · 商品 · 库存', '会员 · 营销', '班次 · 钱箱', '设置 · 设备 · 权限']
TAG = ['餐饮', '共用', '共用', '共用', '共用', '共用', '共用', '共用']
n = len(COLS); g = 8; cw = (CW - g * (n - 1)) / n
cx = [X0 + i * (cw + g) for i in range(n)]
y = 80
# 列头
for i, c in enumerate(COLS):
    s.rect(cx[i], y, cw, 30, WASH2, 6); s.text(cx[i] + cw / 2, y + 20, c, 13, INK, 700, 'middle')
s.text(LX, y + 20, '业务模块 →', 13, SUB, 600)
y += 40
def layer(label, sub, h, fill=WASH):
    global y
    s.rect(LX, y, LW, h, fill, 8); s.lines(LX + LW / 2, y + h / 2, [label] + sub, 13, INK, 700)
    top = y; y += h + 10; return top
# L1 应用壳（全宽）
t = layer('应用壳', ['React · UnoCSS'], 46)
s.box(X0, t, CW, 46, '业态壳：餐饮 Workbench / 零售 RetailWorkbench · 系统栏 SysBarShell · 布局 W1–W4（L1–L6）· 浮层 Dialog / Sheet · 可拖布局 · i18n · 皮肤令牌', fill=WASH, size=13)
# L2 工作台（矩阵）
t = layer('工作台界面', ['modules/*'], 62)
UI = [['桌台宫格', '平面 / 2.5D'], ['点餐 OrderWB', '收银 RetailPos'], ['结账 CheckoutWB', '收款态 PayPane'], ['订单 OrdersWB', '退货 / 换货'], ['菜品 MenuWB', '商品 · 库存作业'], ['会员 MembersWB', '储值 · 券 · 活动'], ['班次 ShiftWB', '交班 · 钱箱实盘'], ['设置 SettingsWB', '设备 · 平台对接']]
for i, a in enumerate(UI): s.box(cx[i], t, cw, 62, a[0], a[1], fill=WASH, size=12)
# L3 共用组件
t = layer('组件', ['两业态共用'], 46)
hw = (CW - g) / 2
s.box(X0, t, hw, 46, '业务组件 components/pos', 'LineRow · FeeRow · QuickBar · ItemTile · PayHead · CashPad · WaitBlock', fill=WASH, size=12)
s.box(X0 + hw + g, t, hw, 46, '基础组件 components/ui（Radix）', 'Button · Seg · Panel · Dialog · Sheet · Keypad · StatusTag · Table', fill=WASH, size=12)
# L4 业态配置（强调带）
t = layer('业态配置', ['biz/'], 70, ACC)
s.rect(X0, t, CW, 70, ACC, 8)
s.text(X0 + 14, t + 24, 'BIZ[ fnb | retail ]  页签 · 布局 · 设置菜单 · 快捷操作 · 费用行 · 计价规则 · 词条覆盖 · 状态字典扩展', 13, ACC_T, 700)
pw = (CW - 28 - g * 2) / 3
for i, (a, b) in enumerate([('餐饮插件', '桌台 · KDS 出品 · 服务费 · 套餐'), ('零售插件', '条码称重 · SKU · 退换货 · 进销存'), ('混合 both', '一台机两种模式，按模式切页签')]):
    s.box(X0 + 14 + i * (pw + g), t + 34, pw, 28, a + '：' + b, fill=CARD, tc=ACC_T, size=12, r=6)
# L5 领域服务（矩阵）
t = layer('领域服务', ['纯 TS，不依赖界面'], 78)
DOM = [['桌台服务', '开台 · 并台 · 拼桌', '清台 · 预订'], ['订单服务', '行状态机 · 挂单', '计价引擎（公共流水线', '+ 业态规则）'], ['支付服务', '组合支付 · 结果未知', '退款 · 平台券核销'], ['订单中心', '反结账 · 退菜', '退货分摊 · 换货'], ['商品服务', '规格 / SKU · 条码', '称重 · 沽清 / 库存'], ['会员营销', '会员 · 储值 · 积分', '券 · 活动 · 平台券'], ['班次服务', '开班 · 交班', '钱箱 · 盘差'], ['权限与设备', '角色 · 授权码 · 审批', '设备注册 · 渠道同步']]
for i, a in enumerate(DOM): s.box(cx[i], t, cw, 78, a[0], a[1:], fill=CARD, stroke=LINE, size=12)
# L6 本地数据
t = layer('本地数据', ['仓储层'], 50)
s.box(X0, t, CW, 50, '本地库 SQLite（收银机 / 平板）· IndexedDB（浏览器原型）', '主数据缓存（菜品商品、会员、配置）· 交易单据（订单、支付、退款、班次）· 事件 Outbox（待同步）· 打印队列 · 操作审计', fill=WASH, size=12)
# L7 同步
t = layer('同步引擎', ['在线 / 离线'], 62, OFF)
tw = (CW - g * 2) / 3
SY = [('局域网同步（主机权威）', '副机经主机读写 · 单据归属开单设备', OFF, OFF_T), ('云同步（Outbox 补传）', '交易事件上行 · 主数据增量下行 · 版本号判冲突', NET, NET_T), ('离线策略', '能力降级矩阵 · 单号 = 门店 + 设备 + 序号（幂等）', OFF, OFF_T)]
for i, (a, b, f, c) in enumerate(SY): s.box(X0 + i * (tw + g), t, tw, 62, a, b, fill=f, tc=c, size=12)
# L8 设备适配
t = layer('设备适配 HAL', ['驱动与发现'], 56)
DV = [('打印', 'ESC/POS 三路接入'), ('钱箱', '随打印机'), ('扫码枪', 'HID 键盘'), ('电子秤', '串口 / 蓝牙'), ('支付终端', '刷卡 / 扫码盒'), ('客显 · 叫号', '第二屏 / 网口'), ('KDS', 'mDNS 配对'), ('设备发现', 'mDNS · BLE · 二维码')]
for i, (a, b) in enumerate(DV): s.box(cx[i], t, cw, 56, a, b, fill=WASH, size=12)
# L9 通信
t = layer('通信', ['LAN / WAN'], 50)
s.box(X0, t, hw, 50, '局域网 LAN', 'mDNS 发现 · WebSocket 主从总线 · 打印机直连 9100', fill=OFF, tc=OFF_T, size=12)
s.box(X0 + hw + g, t, hw, 50, '互联网 WAN', 'HTTPS API · 推送（MQTT / WebSocket）· 支付网关 · 平台 Webhook 经云中转', fill=NET, tc=NET_T, size=12)
# L10 运行时
t = layer('运行时', ['同一份前端'], 44)
RT = ['Android 收银机 / 平板（WebView 容器）', 'Windows 收银机（Electron）', 'iPad（WKWebView）', '浏览器（原型 · 管理后台）']
rw = (CW - g * 3) / 4
for i, a in enumerate(RT): s.box(X0 + i * (rw + g), t, rw, 44, a, fill=WASH, size=12)
# 图例
y += 4
for i, (f, c, l) in enumerate([(ACC, ACC_T, '业态差异只在这一层和插件里'), (OFF, OFF_T, '局域网 / 离线相关'), (NET, NET_T, '互联网 / 云相关'), (CARD, INK, '领域服务（白底描边）')]):
    x = X0 + i * 270; s.rect(x, y, 18, 18, f, 4, LINE if f == CARD else None); s.text(x + 26, y + 14, l, 12, SUB)
s.h = y + 40
open(OUT + '-分层.svg', 'w').write(s.svg().replace('height="860"', f'height="{s.h}"').replace('0 0 1320 860', f'0 0 1320 {s.h}'))

# ---------------- 图 2：多设备集群与在线 / 离线 ----------------
W = 1320; s = S(W, 900)
s.text(24, 38, '全线点餐 POS 端架构 ② 多设备集群 · 在线 / 离线（局域网 / 互联网）', 20, INK, 700)
s.text(24, 60, '一个门店一个局域网；主 POS 是门店的「本地服务器」（Store Hub），副机、KDS、客显、打印都挂在它上面；云是全店和多门店的真相源', 12, SUB)
# 云
s.rect(24, 80, W - 48, 150, NET, 12)
s.text(44, 106, '云端 SaaS（互联网）', 15, NET_T, 700)
CL = [('主数据', '菜品 / 商品 · 价格 · 配置'), ('订单中心', '全渠道订单 · 对账'), ('支付网关', '聚合支付 · 退款'), ('渠道聚合', '外卖 / 即时零售 / 团购券'), ('会员营销', '储值 · 券 · 积分'), ('报表 · 管理后台', '多门店')]
cw2 = (W - 48 - 40 - 10 * 5) / 6
for i, (a, b) in enumerate(CL): s.box(44 + i * (cw2 + 10), 120, cw2, 54, a, b, fill=CARD, tc=NET_T, size=12)
s.text(44, 205, '第三方：支付机构 · Uber Eats / Keeta / Talabat · 美团 / 抖音团购 —— 都只和云对接，门店设备不直连', 12, NET_T)
# 互联网连线
s.line(330, 230, 330, 300, '#8A9099', 1.5, arrow=True); s.text(342, 270, '互联网：HTTPS + 推送 · Outbox 补传 · 主数据增量', 12, NET_T, 600)
s.line(1000, 230, 1000, 300, '#8A9099', 1.5, dash=True, arrow=True); s.text(1012, 262, '副机直连云（可选）', 12, NET_T, 600); s.text(1012, 280, '只用于扫码支付等在线能力', 12, SUB)
# 门店局域网
s.rect(24, 300, W - 48, 330, OFF, 12, '#E5B97A', True)
s.text(44, 326, '门店局域网 LAN（WebSocket 总线 · mDNS 发现 · 二维码配对）', 15, OFF_T, 700)
# 主机
s.rect(44, 345, 380, 265, CARD, 10, INK)
s.text(64, 372, '主 POS · Store Hub', 15, INK, 700); s.text(64, 392, '收银台那台；门店局域网里的唯一权威', 12, SUB)
HUB = ['本地服务（领域服务同一套代码）', '本地库 SQLite · 事件 Outbox', '消息总线（推订单 / 出品 / 状态）', '同步引擎（对云）· 打印调度', '设备注册表 · 授权与审计']
for i, a in enumerate(HUB): s.box(64, 405 + i * 40, 340, 32, a, fill=WASH, size=12, r=6)
# 设备
DEV = [
    ('副 POS', ['第二收银台 · 备主', '主机故障时接管'], 470, 345),
    ('点菜平板 × N', ['服务员 / 导购', '经主机下单'], 690, 345),
    ('KDS 厨房屏 × 2', ['出品路由', '仅餐饮'], 910, 345),
    ('客显 · 叫号屏', ['客显随收银机', '叫号屏挂主机'], 1110, 345),
    ('打印机', ['前台 · 档口 · 标签', 'ESC/POS 网口直连'], 470, 485),
    ('扫码枪 · 电子秤', ['USB / 串口', '接在本机，经 HAL'], 690, 485),
    ('支付终端', ['刷卡 · 扫码盒', '交易走云 / 收单行'], 910, 485),
    ('自助机 · 扫码点餐', ['顾客手机经云', '下行到主机'], 1110, 485),
]
for a, b, x, yy in DEV:
    s.box(x, yy, 190 if x < 1100 else 166, 110, a, b, fill=CARD, size=13, stroke=LINE)
BUS = '#B8873A'
s.line(424, 470, 1276, 470, BUS, 3)   # 局域网总线：两排设备中间
s.text(1270, 465, 'LAN 总线', 11, OFF_T, 600, 'end')
for x in [470, 690, 910, 1110]:
    w_ = 190 if x < 1100 else 166; mx = x + w_ / 2
    s.line(mx, 455, mx, 470, BUS, 2); s.line(mx, 470, mx, 485, BUS, 2)
s.text(440, 620, '设备都挂在主机的局域网总线上（WebSocket / ESC/POS）。所有终端跑同一份前端，按「设备角色」加载不同工作台（收银 / 点菜 / KDS / 客显）。', 12, OFF_T)
# 模式表
y = 650
s.text(24, y + 20, '在线 / 离线能力矩阵', 15, INK, 700)
y += 34
HDR = ['', '① 在线', '② 断互联网（局域网通）', '③ 断局域网（单机）', '④ 主机故障']
ROWS = [
    ['点单 · 送厨 · 打印', '正常', '正常（主机权威）', '本机开单、本机打印；KDS 收不到', '副 POS 升为主，或各机单机'],
    ['现金 · 刷卡', '正常', '正常', '正常', '正常'],
    ['扫码支付 · 平台券', '正常', '暂停（需联网确认）', '暂停', '副机直连云可用'],
    ['会员储值 · 券', '实时', '本地缓存余额，限额内可用', '仅查询缓存', '同 ②'],
    ['外卖 / 线上单', '实时推到主机', '收不到；平台侧自动暂停接单', '收不到', '推到新主机'],
    ['数据', '实时上云', '进 Outbox，恢复后补传', '本机 Outbox，回到局域网先并入主机', '新主机从副本恢复'],
]
cws = [180, 190, 270, 300, 300]; xs = [24]
for w_ in cws[:-1]: xs.append(xs[-1] + w_ + 6)
rh = 30
fills = [WASH2, NET, OFF, OFF, DNG]; tcs = [INK, NET_T, OFF_T, OFF_T, DNG_T]
for j, h_ in enumerate(HDR): s.rect(xs[j], y, cws[j], rh, fills[j], 6); s.text(xs[j] + 10, y + 20, h_, 13, tcs[j], 700)
for i, r in enumerate(ROWS):
    yy = y + (i + 1) * (rh + 4)
    for j, c in enumerate(r):
        s.rect(xs[j], yy, cws[j], rh, WASH if j == 0 else CARD, 6, None if j == 0 else WASH2)
        s.text(xs[j] + 10, yy + 20, c, 12, INK if j == 0 else SUB, 600 if j == 0 else 400)
s.h = y + (len(ROWS) + 1) * (rh + 4) + 20
open(OUT + '-集群.svg', 'w').write(s.svg().replace('height="900"', f'height="{s.h}"').replace('0 0 1320 900', f'0 0 1320 {s.h}'))
print('ok', OUT)
