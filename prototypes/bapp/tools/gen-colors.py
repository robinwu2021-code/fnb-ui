#!/usr/bin/env python3
"""全线点餐 POS · 色彩体系生成脚本。

由品牌原色和规则算出全部色阶与角色，生成三个文件（不要手改它们）：
  ../pos-colors.css    色彩令牌：原色阶 · 基本色角色 · 主题色角色 · 状态色 · 避让规则 · 接入层
  ../pos-colors.json   同一份数据加实测对比度，供文档引用
  ../pos-colors.html   色板预览页（样式在 ../proto.css「色彩体系预览」一段）

用法：python3 -I prototypes/bapp/tools/gen-colors.py
新增主题色：在 THEMES 里加一行（键、中文名、英文名、品牌原色），重跑即可。
规则见 docs/全线点餐POS-色彩体系.md；评审结论见 docs/全线点餐POS-色彩体系评审.md。
"""
import json
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.dirname(HERE)

# ── 颜色换算：sRGB ↔ OKLab / OKLCH，WCAG 对比度 ──────────────────────────────
def _lin(c): return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
def _delin(c): return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
def _rgb(h): h = h.lstrip('#'); return [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
def _hex(r): return '#' + ''.join('%02X' % round(max(0, min(1, x)) * 255) for x in r)

def luminance(h):
    r, g, b = [_lin(x) for x in _rgb(h)]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def contrast(a, b):
    la, lb = sorted([luminance(a), luminance(b)], reverse=True)
    return round((la + 0.05) / (lb + 0.05), 2)

def oklch_to_hex(L, C, H):
    """OKLCH → hex；超出 sRGB 时逐步降彩度。"""
    while True:
        a, b = C * math.cos(math.radians(H)), C * math.sin(math.radians(H))
        l_ = L + 0.3963377774 * a + 0.2158037573 * b
        m_ = L - 0.1055613458 * a - 0.0638541728 * b
        s_ = L - 0.0894841775 * a - 1.2914855480 * b
        l, m, s = l_ ** 3, m_ ** 3, s_ ** 3
        rgb = [4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
               -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
               -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s]
        if all(-0.002 <= x <= 1.002 for x in rgb) or C <= 0:
            return _hex([_delin(max(0, min(1, x))) for x in rgb])
        C -= 0.002

def hex_to_oklch(h):
    r, g, b = [_lin(x) for x in _rgb(h)]
    l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b) ** (1 / 3)
    m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b) ** (1 / 3)
    s = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b) ** (1 / 3)
    L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return L, math.hypot(a, b), math.degrees(math.atan2(b, a)) % 360

def delta_e(x, y):
    """OKLab 色差 × 100：小于 5 很难分，小于 2 基本分不出。"""
    (L1, C1, H1), (L2, C2, H2) = hex_to_oklch(x), hex_to_oklch(y)
    a1, b1 = C1 * math.cos(math.radians(H1)), C1 * math.sin(math.radians(H1))
    a2, b2 = C2 * math.cos(math.radians(H2)), C2 * math.sin(math.radians(H2))
    return round(math.sqrt((L1 - L2) ** 2 + (a1 - a2) ** 2 + (b1 - b2) ** 2) * 100, 1)

# ── 输入 ─────────────────────────────────────────────────────────────────────
WHITE, INK = '#FFFFFF', None
GRAY_HUE, GRAY_CHROMA = 258, 0.0045          # 微冷灰；评审 P2-6：900 档的色相也统一到 258
GRAY_L = [('0', 1.0), ('50', .972), ('100', .952), ('150', .928), ('200', .9), ('300', .83), ('400', .72),
          ('500', .62), ('600', .52), ('700', .42), ('800', .32), ('900', .215), ('950', .15)]   # 评审 P2-2：删掉 25 档
STEPS = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900', '950']
STEP_L = [.975, .945, .89, .82, .735, .65, .57, .495, .415, .345, .275]       # 每档明度，所有色系一致
STEP_C = [.14, .28, .48, .7, .88, 1, 1, .93, .82, .68, .54]                    # 彩度曲线，相对品牌原色
THEMES = [('brand', '虹选红', 'Brand red', '#E1251B'), ('blue', '经典蓝', 'Classic blue', '#2C69FF'),
          ('fresh', '微信绿', 'Fresh green', '#00B578'), ('teal', '青碧', 'Teal', '#008484'),
          ('amber', '琥珀', 'Amber', '#A46911'), ('violet', '紫', 'Violet', '#7A4CE0'),
          ('pine', '松绿', 'Pine', '#216857')]   # v1.5 默认主题色（组件库 1.5，2026-10-23）
STATUS = [('success', '成功', '#12B76A'), ('warning', '警告', '#F79009'), ('danger', '危险', '#F04438'), ('info', '信息', '#2E90FA')]
CATEGORIES = [['red', '朱红', '#CC3430', '#FFE0DB', '#910F13'], ['orange', '橘橙', '#C25500', '#FEE1D3', '#7E3300'],
              ['yellow', '明黄', '#F6C330', '#F4E7C7', '#614900'], ['green', '草绿', '#18883A', '#D6F0D8', '#005E21'],
              ['teal', '青绿', '#038476', '#D5EEE9', '#005A50'], ['blue', '湛蓝', '#006CC4', '#D8EAFF', '#004D91'],
              ['indigo', '靛紫', '#5552BB', '#E3E6FE', '#413F94'], ['purple', '葡萄紫', '#8747AA', '#F2E0FE', '#653181'],
              ['rose', '玫红', '#BC3E79', '#FFDEE9', '#841E51'], ['brown', '栗棕', '#80512F', '#F4E4DA', '#6A4327'],
              ['gray', '石灰', '#5E5E5E', '#E6E6E6', '#3D3D3D']]
# 辅助色：业务状态用的颜色，色相避开状态色 ±30° 左右；以 (色相, 彩度) 给出，基准明度 0.62
AUX = [('cyan', '青', 200, 0.115), ('purple', '紫罗兰', 312, 0.20), ('olive', '橄榄', 123, 0.155), ('magenta', '品红', 350, 0.20)]
# 业务状态分 9 组：每组一种颜色、一种默认展示形式。chip = 浅底 + 图标 + 文字；dot = 图标 + 彩色文字（不铺底，安静）
GROUPS = [
    ('action',   '待处理', 'Needs action', 'warning', 'chip', '要人处理或留意：待接单、待结账、待审批、催菜、低库存、缺纸、离线（还能收现金和刷卡）'),
    ('progress', '进行中', 'In progress',  'info',    'dot',  '正在进行：制作中、已接单、营业中'),
    ('waiting',  '等待中', 'Waiting',      'cyan',    'dot',  '在等别人或外部系统：待取餐、配送中、支付中、排队打印、待同步'),
    ('done',     '已完成', 'Done',         'success', 'dot',  '完成，不需要再看：已上菜、已结账、已送达、已打印'),
    ('booked',   '预约',   'Booked',       'purple',  'chip', '未来的安排：已预订、待到店'),
    ('held',     '暂存',   'On hold',      'olive',   'chip', '暂时放着、还会回来：挂起、待下单'),
    ('trace',    '变更标记', 'Change mark', 'magenta', 'dot', '追溯用的标记：补打、反结账、合自、拆自、转自、改价、临时菜、加菜'),
    ('closed',   '已关闭', 'Closed',       'neutral', 'dot',  '结束且不需要处理：已取消、已拒单、已撤单、空闲、未连接'),
    ('failed',   '异常',   'Problem',      'danger',  'chip', '失败或异常：打印失败、支付失败、同步失败、超时、盘差、已沽清、已退'),
]
# 业务状态表：实体 → [状态键, 中文, English, 分组, Lucide 图标, 依据（功能清单编号）]
STATES = [
    ('order', '订单', 'Order', [
        ('open', '进行中', 'Open', 'progress', 'utensils', 'E-01'), ('due', '待结账', 'Ready to pay', 'action', 'receipt', 'E-01 G-13'),
        ('partial', '部分支付', 'Partly paid', 'action', 'wallet', 'G-11'), ('paid', '已结账', 'Paid', 'done', 'badge-check', 'E-01 G-13'),
        ('held', '挂起', 'On hold', 'held', 'circle-pause', 'E-03 E-04'), ('cancelled', '已取消', 'Cancelled', 'closed', 'ban', '—'),
        ('reopened', '已反结账', 'Reopened', 'trace', 'undo-2', 'E-07'), ('merged', '合自', 'Merged from', 'trace', 'git-merge', 'E-05'),
        ('split', '拆自', 'Split from', 'trace', 'split', 'D-07')]),
    ('item', '订单行（菜品）', 'Order item', [
        ('draft', '待下单', 'Not sent', 'held', 'pencil-line', 'D-01 F-01'), ('sent', '已下单', 'Sent', 'waiting', 'send', 'F-01 F-07'),
        ('cooking', '制作中', 'Cooking', 'progress', 'chef-hat', 'F-07'), ('rushed', '催菜中', 'Rushed', 'action', 'zap', 'D-03'),
        ('ready', '待上菜', 'Ready to serve', 'waiting', 'concierge-bell', 'F-06'), ('served', '已上菜', 'Served', 'done', 'check-check', 'F-06 F-07'),
        ('voided', '已退', 'Voided', 'failed', 'circle-minus', 'D-05'), ('comp', '赠送', 'Comp', 'trace', 'gift', 'D-04'),
        ('moved', '转自', 'Moved from', 'trace', 'arrow-right-left', 'D-06'), ('repriced', '已改价', 'Repriced', 'trace', 'pencil', 'D-08'),
        ('custom', '临时菜', 'Custom item', 'trace', 'file-plus', 'C-09')]),
    ('table', '桌台', 'Table', [
        ('free', '空闲', 'Free', 'closed', 'circle', 'B-01'), ('booked', '已预订', 'Booked', 'booked', 'calendar-clock', 'B-05'),
        ('seated', '待点餐', 'Seated', 'waiting', 'hourglass', 'B-03'), ('dining', '用餐中', 'Dining', 'progress', 'utensils', 'B-02'),
        ('late', '超时未上菜', 'Dishes late', 'action', 'timer', 'F-05'), ('due', '待结账', 'Ready to pay', 'action', 'receipt', 'B-02'),
        ('partial', '部分支付', 'Partly paid', 'action', 'wallet', 'G-11'), ('to_clear', '待清台', 'To clear', 'action', 'sparkles', 'B-09'),
        ('merged', '并入', 'Merged into', 'trace', 'git-merge', 'B-08'), ('shared', '拼桌', 'Shared', 'trace', 'users', 'B-07'),
        ('disabled', '停用', 'Disabled', 'closed', 'ban', '—')]),
    ('delivery', '外卖与自提', 'Delivery & pickup', [
        ('new', '待接单', 'New', 'action', 'bell-ring', 'H-01 H-02'), ('accepted', '已接单', 'Accepted', 'progress', 'chef-hat', 'H-02'),
        ('pickup', '待取餐', 'Ready for pickup', 'waiting', 'package', 'H-04'), ('delivering', '配送中', 'Out for delivery', 'waiting', 'bike', 'H-04'),
        ('delivered', '已送达', 'Delivered', 'done', 'circle-check', '—'), ('rejected', '已拒单', 'Rejected', 'closed', 'circle-x', 'H-03'),
        ('cancelled', '已取消', 'Cancelled', 'closed', 'ban', '—'), ('refunding', '退款中', 'Refunding', 'waiting', 'rotate-ccw', 'J-04'),
        ('timeout', '接单超时', 'Accept timeout', 'failed', 'timer-off', 'H-02')]),
    ('payment', '支付', 'Payment', [
        ('unpaid', '待支付', 'Unpaid', 'action', 'wallet', 'G-07'), ('paying', '支付中', 'Paying', 'waiting', 'loader-circle', 'G-07'),
        ('unknown', '结果未知', 'Result unknown', 'action', 'circle-help', 'G-07 J-04'), ('paid', '已支付', 'Paid', 'done', 'circle-check', 'G-13'),
        ('failed', '支付失败', 'Failed', 'failed', 'circle-alert', 'G-07 G-10'), ('refunding', '退款中', 'Refunding', 'waiting', 'rotate-ccw', 'J-04'),
        ('refunded', '已退款', 'Refunded', 'closed', 'undo-2', 'G-11')]),
    ('ticket', '厨房工单', 'Kitchen ticket', [
        ('new', '新单', 'New', 'action', 'bell-ring', 'F-04'), ('cooking', '制作中', 'Cooking', 'progress', 'chef-hat', 'F-04'),
        ('added', '加菜', 'Added', 'trace', 'plus', 'F-05'), ('rushed', '催菜', 'Rushed', 'action', 'zap', 'F-05'),
        ('late', '超时', 'Late', 'failed', 'timer-off', 'F-05'), ('done', '已出餐', 'Done', 'done', 'check-check', 'F-06'),
        ('withdrawn', '已撤单', 'Withdrawn', 'closed', 'ban', 'D-05')]),
    ('print', '打印任务', 'Print job', [
        ('queued', '排队中', 'Queued', 'waiting', 'printer', 'F-02'), ('printed', '已打印', 'Printed', 'done', 'circle-check', 'F-02'),
        ('failed', '打印失败', 'Print failed', 'failed', 'file-x', 'F-03'), ('reprint', '补打', 'Reprint', 'trace', 'repeat', 'A-04 E-06')]),
    ('device', '设备与网络', 'Devices & network', [
        ('online', '在线', 'Online', 'done', 'wifi', 'A-05'), ('offline', '离线', 'Offline', 'action', 'wifi-off', 'A-05 A-06'),
        ('no_paper', '缺纸', 'Out of paper', 'action', 'triangle-alert', 'A-03'), ('unplugged', '未连接', 'Not connected', 'closed', 'unplug', 'A-02'),
        ('to_sync', '待同步', 'Waiting to sync', 'waiting', 'cloud-upload', 'A-06'), ('sync_failed', '同步失败', 'Sync failed', 'failed', 'circle-alert', 'A-06')]),
    ('booking', '预订', 'Reservation', [
        ('upcoming', '待到店', 'Upcoming', 'booked', 'calendar-clock', 'B-05'), ('arrived', '已到店', 'Arrived', 'done', 'calendar-check', 'B-05'),
        ('no_show', '未到店', 'No-show', 'closed', 'calendar-x', 'B-05'), ('cancelled', '已取消', 'Cancelled', 'closed', 'ban', '—')]),
    ('stock', '库存与沽清', 'Stock', [
        ('in_stock', '库存充足', 'In stock', 'done', 'package-check', 'C-10'), ('low', '仅剩 N', 'Only N left', 'action', 'triangle-alert', 'C-10'),
        ('sold_out', '已沽清', 'Sold out', 'failed', 'circle-slash', 'C-10'), ('timed', '限时供应', 'Limited hours', 'booked', 'clock', 'C-02'),
        ('off_menu', '已下架', 'Off menu', 'closed', 'archive', '—')]),
    ('sync', '渠道同步', 'Channel sync', [
        ('synced', '已同步', 'Synced', 'done', 'cloud', 'C-12'), ('pending', '待同步', 'Not synced', 'waiting', 'cloud-upload', 'C-12'),
        ('syncing', '同步中', 'Syncing', 'waiting', 'refresh-cw', 'C-12'), ('failed', '同步失败', 'Sync failed', 'failed', 'cloud-alert', 'C-12'),
        ('unlisted', '未上架', 'Not listed', 'closed', 'cloud-off', 'C-12')]),
    ('shift', '班次', 'Shift', [
        ('closed', '未开班', 'Not open', 'closed', 'lock', 'A-01'), ('open', '营业中', 'Open', 'progress', 'store', 'A-01'),
        ('handover', '交班中', 'Handing over', 'waiting', 'hourglass', 'J-05'), ('handed', '已交班', 'Handed over', 'done', 'circle-check', 'J-05'),
        ('variance', '盘差', 'Cash variance', 'failed', 'scale', 'J-03')]),
    ('approval', '审批', 'Approval', [
        ('pending', '待审批', 'Pending', 'action', 'stamp', 'J-08'), ('approved', '已通过', 'Approved', 'done', 'circle-check', 'J-08'),
        ('rejected', '已驳回', 'Rejected', 'failed', 'circle-x', 'J-08')]),
    ('invoice', '发票', 'Invoice', [
        ('requested', '待开票', 'Requested', 'action', 'file-text', 'G-15'), ('issued', '已开票', 'Issued', 'done', 'file-check', 'G-15'),
        ('failed', '开票失败', 'Failed', 'failed', 'file-x', 'G-15')]),
    ('member', '会员', 'Member', [
        ('linked', '已绑定', 'Linked', 'done', 'user-check', 'I-01'), ('low_balance', '余额不足', 'Low balance', 'action', 'wallet', 'G-10'),
        ('unlinked', '已解绑', 'Unlinked', 'closed', 'user-x', 'I-03'), ('lead', '线索会员', 'Lead', 'held', 'user-round-pen', 'I-01')]),
    ('recharge', '储值单', 'Top-up', [
        ('wait_collection', '待收款', 'Awaiting payment', 'action', 'wallet', 'I-04'), ('submitted', '待确认收款', 'Payment to confirm', 'waiting', 'hourglass', 'I-04'),
        ('received', '已收款待到账', 'Received', 'waiting', 'loader-circle', 'I-04'), ('credited', '已到账', 'Credited', 'done', 'circle-check', 'I-04'),
        ('cancelled', '已取消', 'Cancelled', 'closed', 'ban', 'I-04'), ('rejected', '已拒绝', 'Rejected', 'failed', 'circle-x', 'I-04'),
        ('expired', '已过期', 'Expired', 'closed', 'timer-off', 'I-04')]),
    ('coupon', '优惠券', 'Coupon', [
        ('unused', '可用', 'Available', 'progress', 'ticket', 'I-06'), ('used', '已用', 'Used', 'closed', 'ticket-check', 'I-06'),
        ('expired', '已过期', 'Expired', 'closed', 'timer-off', 'I-06'), ('revoked', '已收回', 'Revoked', 'failed', 'undo-2', 'I-06')]),
    ('campaign', '营销活动', 'Campaign', [
        ('draft', '草稿', 'Draft', 'held', 'pencil-line', 'I-05'), ('running', '进行中', 'Running', 'progress', 'megaphone', 'I-05'),
        ('paused', '已暂停', 'Paused', 'held', 'circle-pause', 'I-05'), ('ended', '已结束', 'Ended', 'closed', 'flag', 'I-05')]),
    ('points', '积分', 'Points', [
        ('pending', '待生效', 'Pending', 'waiting', 'hourglass', 'I-07'), ('available', '可用', 'Available', 'done', 'coins', 'I-07'),
        ('held', '占用', 'Held', 'held', 'lock', 'I-07'), ('refund_reserved', '退款预留', 'Refund reserved', 'waiting', 'rotate-ccw', 'I-07')]),
]

# 避让规则（评审 P0-1，方案 A）：主题色和某个状态色色相太近时，把撞上的位置换成别的颜色
AVOID = {
    'brand': {'status': 'danger', 'mark': 'neutral', 'note': '虹选红 ↔ 危险（色相差 0°）：赠送、会员价、优惠券、预订这类主题标记改用中性标签，红色只留给已退、失败'},
    'fresh': {'status': 'success', 'group': 'done', 'note': '微信绿 ↔ 成功（色相差 5°）：「已完成」组（已上菜、已结账、已送达…）改用灰色，绿色只留给主题'},
    'amber': {'status': 'warning', 'due_mark': True, 'note': '琥珀 ↔ 警告（色相差 8°）：桌台「待结账」在桌号前加 ¥ 标记，不只靠底色'},
    'teal': {'aux': 'cyan', 'group': 'waiting', 'note': '青碧 ↔ 辅助色青（色相差 6°）：「等待中」组（待取餐、配送中、支付中…）改用灰色'},
    'blue': {'status': 'info', 'hint': 'neutral', 'group': 'progress', 'note': '经典蓝 ↔ 信息（色相差 10°）：提示类说明改中性底，「进行中」组（制作中、已接单…）改用灰色，蓝色只留给主题'},
}

# ── 计算 ─────────────────────────────────────────────────────────────────────
gray = {k: (WHITE if L >= 1 else oklch_to_hex(L, GRAY_CHROMA, GRAY_HUE)) for k, L in GRAY_L}
INK = gray['900']

def ramp(base):
    _, C0, H0 = hex_to_oklch(base)
    return {s: oklch_to_hex(STEP_L[i], C0 * STEP_C[i], H0) for i, s in enumerate(STEPS)}

def nxt(s, n=1): return STEPS[min(STEPS.index(s) + n, len(STEPS) - 1)]

themes = {}
for key, zh, en, base in THEMES:
    r = ramp(base)
    primary = next(s for s in ['500', '600', '700'] if contrast(WHITE, r[s]) >= 4.5)       # 第一个白字 ≥4.5:1 的档
    text = next(s for s in ['600', '700', '800'] if contrast(r[s], r['100']) >= 4.5 and contrast(r[s], WHITE) >= 5)
    roles = {'primary': primary, 'primary-hover': nxt(primary), 'primary-pressed': nxt(primary, 2), 'primary-tint': '100',
             'primary-tint-hover': '200', 'primary-border': '300', 'primary-text': text}
    near = min(STEPS, key=lambda s: delta_e(r[s], base))
    themes[key] = {'zh': zh, 'en': en, 'base': base, 'hue': round(hex_to_oklch(base)[2]), 'ramp': r, 'roles': roles, 'nearest': near,
                   'bright': {'primary': '500', 'on': INK, 'contrast': contrast(INK, r['500'])} if key in ('fresh', 'teal') else None}
mono_r = {s: gray[s] for s in STEPS}
themes = {'mono': {'zh': '墨黑', 'en': 'Ink', 'base': gray['900'], 'hue': GRAY_HUE, 'ramp': mono_r, 'nearest': '900', 'bright': None,
                   'roles': {'primary': '900', 'primary-hover': '800', 'primary-pressed': '950', 'primary-tint': '150',
                             'primary-tint-hover': '200', 'primary-border': '300', 'primary-text': '900'}}, **themes}

status = {}
for key, zh, base in STATUS:
    r = ramp(base)
    tint = '200'                                                                              # 评审 P0-1：比主题浅底深一档
    text = next(s for s in ['700', '800', '900'] if contrast(r[s], r[tint]) >= 4.5 and contrast(r[s], WHITE) >= 4.5)
    dot = next(s for s in ['600', '700', '800'] if contrast(r[s], r[tint]) >= 3 and contrast(r[s], WHITE) >= 3)   # P1-3
    strong = next(s for s in ['600', '700', '800'] if contrast(WHITE, r[s]) >= 4.5)
    status[key] = {'zh': zh, 'base': base, 'hue': round(hex_to_oklch(base)[2]), 'ramp': r,
                   'roles': {'dot': dot, 'text': text, 'tint': tint, 'border': '300', 'strong': strong}}

aux = {}
for key, zh, H, C in AUX:
    base = oklch_to_hex(0.62, C, H)
    aux[key] = {'zh': zh, 'base': base, 'hue': H, 'ramp': ramp(base)}
FAM = {**{k: v['ramp'] for k, v in status.items()}, **{k: v['ramp'] for k, v in aux.items()}}
groups = {}
for gk, zh, en, fam, form, desc in GROUPS:
    if fam == 'neutral':
        ro, vals = {'dot': '500', 'text': '600', 'tint': '100', 'border': '300'}, {'dot': gray['500'], 'text': gray['600'], 'tint': gray['100'], 'border': gray['300']}
    else:
        r = FAM[fam]
        dot = next(s for s in ['600', '700', '800'] if contrast(r[s], r['200']) >= 3 and contrast(r[s], WHITE) >= 3)
        text = next(s for s in ['700', '800', '900'] if contrast(r[s], r['200']) >= 4.5 and contrast(r[s], WHITE) >= 4.5)
        ro = {'dot': dot, 'text': text, 'tint': '200', 'border': '300'}
        vals = {n: r[s] for n, s in ro.items()}
    groups[gk] = {'zh': zh, 'en': en, 'family': fam, 'form': form, 'desc': desc, 'roles': ro, 'values': vals,
                  'contrast': {'text/tint': contrast(vals['text'], vals['tint']), 'text/white': contrast(vals['text'], WHITE),
                               'dot/tint': contrast(vals['dot'], vals['tint']), 'dot/white': contrast(vals['dot'], WHITE)}}

def val(fam, step): return fam['ramp'][step] if step in fam['ramp'] else gray[step]

# 实测
report = {'gray': gray, 'themes': {}, 'status': {}, 'avoid': AVOID, 'categories': CATEGORIES, 'aux': aux, 'groups': groups,
          'states': [{'key': e, 'zh': zh, 'en': en, 'states': [dict(zip(['key', 'zh', 'en', 'group', 'icon', 'ref'], st)) for st in sts]} for e, zh, en, sts in STATES]}
for key, f in themes.items():
    v = {n: val(f, s) for n, s in f['roles'].items()}
    c = {'white/primary': contrast(WHITE, v['primary']), 'text/tint': contrast(v['primary-text'], v['primary-tint']),
         'text/white': contrast(v['primary-text'], WHITE), 'focus/white': contrast(v['primary'], WHITE),
         'focus/tint': contrast(v['primary'], v['primary-tint']), 'tint/white': contrast(v['primary-tint'], WHITE)}
    clash = {}
    for sk, sf in status.items():
        clash[sk] = {'tint': delta_e(v['primary-tint'], sf['ramp']['200']), 'text': delta_e(v['primary-text'], sf['ramp'][sf['roles']['text']])}
    report['themes'][key] = {**{k: f[k] for k in ('zh', 'en', 'base', 'hue', 'nearest', 'roles', 'ramp', 'bright')}, 'values': v, 'contrast': c, 'vs_status': clash}
for key, f in status.items():
    r, ro = f['ramp'], f['roles']
    report['status'][key] = {**f, 'contrast': {'text/tint': contrast(r[ro['text']], r[ro['tint']]), 'text/white': contrast(r[ro['text']], WHITE),
                                              'dot/tint': contrast(r[ro['dot']], r[ro['tint']]), 'dot/white': contrast(r[ro['dot']], WHITE),
                                              'white/strong': contrast(WHITE, r[ro['strong']])}}
report['base'] = {'sub/white': contrast(gray['600'], WHITE), 'sub/faint': contrast(gray['600'], gray['100']), 'ink/white': contrast(gray['900'], WHITE),
                  'hint/faint': contrast(gray['600'], gray['100']), 'placeholder/white': contrast(gray['400'], WHITE),
                  'control-off/white': contrast(gray['500'], WHITE), 'control-off/faint': contrast(gray['500'], gray['100']),
                  'fill-faint/white': contrast(gray['200'], WHITE), 'sub/wash': contrast(gray['600'], gray['50']), 'sub/wash-strong': contrast(gray['600'], gray['100']), 'slot-text/faint': min(contrast(c[4], gray['100']) for c in CATEGORIES)}

assert report['base']['sub/wash-strong'] >= 4.5, ('次要字在浅底上不足 4.5:1', report['base']['sub/wash-strong'])

# ── 写 CSS ───────────────────────────────────────────────────────────────────
L = []
a = L.append
a('/* ═══════════════════════════════════════════════════════════════════════════════')
a(' * 全线点餐 POS · 色彩令牌（pos-colors.css）—— 由 tools/gen-colors.py 生成，不要手改。')
a(' * 定义见 docs/全线点餐POS-色彩体系.md；评审见 docs/全线点餐POS-色彩体系评审.md（P0、P1 已改入）。')
a(' *   第一层 原色阶：--gray-* · 6 个主题色系 --{色系}-50…950 · 4 个状态色系 · 11 个分类色。组件不直接用。')
a(' *   第二层 基本色角色（黑白灰，不随主题变）：--c-canvas / surface / faint / edge / line / ink / sub / hint / placeholder / control-off …')
a(' *   第三层 主题色角色：data-skin 选色系 → --p-50…950 → --c-primary / hover / pressed / tint / text / focus；')
a(' *          另有避让角色 --c-mark-* / --c-progress-* / --c-due-* / --c-hint-*，撞色的色系在这里换掉颜色。')
a(' *   状态色角色：--c-{success|warning|danger|info}-{dot|text|tint|border|strong}，所有主题共用。')
a(' *   第四层 接入：<html data-colors="v2"> 时把旧 --sh-* 令牌指到新角色；不加时不影响现有页面。')
a(' * ═══════════════════════════════════════════════════════════════════════════════ */')
a(':root{')
a('  /* ── 第一层 · 灰阶：OKLCH 色相 %d°、彩度 %s ── */' % (GRAY_HUE, GRAY_CHROMA))
a('  ' + ' '.join('--gray-%s:%s;' % (k, v) for k, v in gray.items()))
for key, f in themes.items():
    if key == 'mono':
        continue
    a('  /* ── 第一层 · %s %s（品牌原色 %s，色相 %d°）── */' % (f['zh'], f['en'], f['base'], f['hue']))
    a('  ' + ' '.join('--%s-%s:%s;' % (key, s, f['ramp'][s]) for s in STEPS))
for key, f in status.items():
    a('  /* ── 第一层 · 状态 %s（原色 %s，色相 %d°）── */' % (f['zh'], f['base'], f['hue']))
    a('  ' + ' '.join('--%s-%s:%s;' % (key, s, f['ramp'][s]) for s in STEPS))
for key, f in aux.items():
    a('  /* ── 第一层 · 辅助色 %s（色相 %d°，业务状态用）── */' % (f['zh'], f['hue']))
    a('  ' + ' '.join('--%s-%s:%s;' % (key, s, f['ramp'][s]) for s in STEPS))
a('  /* ── 第一层 · 分类色：-solid 实色 · -bg 粉彩底 · -fg 字 ── */')
for k, n, solid, bg, fg in CATEGORIES:
    a('  --cat-%s-solid:%s; --cat-%s-bg:%s; --cat-%s-fg:%s; /* %s */' % (k, solid, k, bg, k, fg, n))
a('')
a('  /* ── 第二层 · 基本色角色（黑白灰，不随主题变）── */')
BASE = [('canvas', 'gray-50', '画布'), ('surface', 'gray-0', '面板、卡片、弹窗、侧滑'), ('faint', 'gray-100', '中性块：muted 按钮、输入框、步进器、按键、分段轨道'),
        ('edge', 'gray-150', '少数必须的细描边（白卡细线面感已退役，组件不再用它画卡片外框）'), ('line', 'gray-100', '列表行间分隔（唯一保留的线）'), ('strong', 'gray-200', '按下态、强一档的中性块'),
        ('ink', 'gray-900', '正文、标题、金额、选中的位置'), ('sub', 'gray-600', '次要文字'),
        ('hint', 'gray-600', '作说明的占位字（搜索框等没有外部标签的输入框，P0-4）'), ('placeholder', 'gray-400', '纯示例的占位字、禁用文字，不承载必要信息'),
        ('control-off', 'gray-500', '开关关闭态轨道、勾选框未选态描边（≥3:1，P0-3）'), ('disabled-bg', 'gray-100', '禁用控件底'),
        ('slot-bg', 'gray-100', '无图菜品大字位的底（中性，字用分类色，P1-1）'), ('inverse', 'gray-900', '反色面：Toast、操作菜单'),
        ('on-inverse', 'gray-0', '反色面上的字')]
for n, v, d in BASE:
    a('  --c-%s:var(--%s); /* %s */' % (n, v, d))
a('  --c-scrim:rgba(12,14,18,.45); /* 遮罩 */')
a('  --c-on-solid:var(--gray-0); /* 实心色块上的字和图标：危险实心按钮、角标、支付方式图标 */')
a('  --c-saving:var(--c-success-text); /* 优惠金额（−¥）与已用优惠的图标；黑白灰色系下为墨 */')
a('  --c-check-off:var(--gray-150); /* 勾选框未选：实心块 */')
a('  --c-pick:var(--gray-200); /* 已点的菜（墨黑）：比浅块深两档 */')
a('')
a('  /* ── 状态色角色：浅底 200（比主题浅底深一档）· 文字在浅底上 ≥4.5:1 · 圆点在浅底和白底上都 ≥3:1 · 实心放白字 ≥4.5:1 ── */')
for key, f in status.items():
    ro = f['roles']
    a('  --c-%s-dot:var(--%s-%s); --c-%s-text:var(--%s-%s); --c-%s-tint:var(--%s-%s); --c-%s-border:var(--%s-%s); --c-%s-strong:var(--%s-%s);'
      % (key, key, ro['dot'], key, key, ro['text'], key, key, ro['tint'], key, key, ro['border'], key, key, ro['strong']))
a('')
a('  /* ── 业务状态角色：9 组，每组 圆点 / 图标 · 文字 · 浅底 · 描边。状态一律「图标 + 文字」，颜色只是辅助。清单见 docs/全线点餐POS-业务状态.md ── */')
for gk, g in groups.items():
    if g['family'] == 'neutral':
        a('  --c-st-%s-dot:var(--gray-500); --c-st-%s-text:var(--gray-600); --c-st-%s-tint:var(--gray-100); --c-st-%s-border:var(--gray-300); /* %s %s */' % (gk, gk, gk, gk, g['zh'], g['form']))
    else:
        ro = g['roles']
        a('  --c-st-%s-dot:var(--%s-%s); --c-st-%s-text:var(--%s-%s); --c-st-%s-tint:var(--%s-%s); --c-st-%s-border:var(--%s-%s); /* %s %s */'
          % (gk, g['family'], ro['dot'], gk, g['family'], ro['text'], gk, g['family'], ro['tint'], gk, g['family'], ro['border'], g['zh'], g['form']))
a('  /* 状态块：卡片整块用的状态底（桌台卡、订单卡、活动卡）。常态 = 浅底 30% 叠在面板色上，要处理的 = 60%。跟着浅底走，主题避让改了浅底也随之变 */')
a('  ' + ' '.join('--c-st-%s-block:color-mix(in srgb,var(--c-st-%s-tint) 30%%,var(--c-surface)); --c-st-%s-block-strong:color-mix(in srgb,var(--c-st-%s-tint) 60%%,var(--c-surface));' % (gk, gk, gk, gk) for gk in groups))
a('}')
a('')
a('/* ── 第三层 · 主题色 ── */')
for key, f in themes.items():
    src = 'gray' if key == 'mono' else key
    a('[data-skin="%s"]{%s}  /* %s */' % (key, ' '.join('--p-%s:var(--%s-%s);' % (s, src, s) for s in STEPS), f['zh']))
a('/* 角色：实心 = 第一个白字 ≥4.5:1 的档；悬停 / 按下再深一档、两档；浅底 100；文字 = 浅底上 ≥4.5:1 的档；焦点用实心色（P0-2） */')
for key, f in themes.items():
    r = f['roles']
    mono = key == 'mono'
    tint = 'var(--gray-100)' if mono else 'var(--p-100)'
    tint_h = 'var(--gray-150)' if mono else 'var(--p-200)'
    a('[data-skin="%s"]{--c-primary:var(--p-%s); --c-primary-hover:var(--p-%s); --c-primary-pressed:var(--p-%s); --c-on-primary:#FFFFFF; '
      '--c-primary-tint:%s; --c-primary-tint-hover:%s; --c-primary-border:var(--p-300); --c-primary-text:var(--p-%s); --c-focus:var(--c-primary)}'
      % (key, r['primary'], r['primary-hover'], r['primary-pressed'], tint, tint_h, r['primary-text']))
    # 少用灰（v10）：大面积一律白；小面积的底用 wash（墨黑 = gray-50，其他主题 = 主题 50 档）
    # 选中 sel（v20）：不用墨——墨只给正文和主按钮。墨黑 = gray-200 块 + 墨字（比未选的 gray-50 深两档，再加粗）；其他主题 = tint + 主色字
    a('[data-skin="%s"]{--c-wash:%s; --c-wash-strong:%s; --c-sel-bg:%s; --c-sel-fg:%s}'
      % (key, 'var(--gray-50)' if mono else 'var(--p-50)', 'var(--gray-100)' if mono else 'var(--p-100)', 'var(--gray-200)' if mono else 'var(--c-primary-tint)', 'var(--gray-900)' if mono else 'var(--c-primary-text)'))
a('')
a('/* ── 避让角色（P0-1 方案 A）：默认取主题色或状态色；撞色的色系在下面改掉 ──')
a(' *   mark：赠送、会员价、优惠券、预订等「和钱有关的主题标记」 · progress：制作中、已接单等进行中状态')
a(' *   due：桌台「待结账」（--c-due-mark 是桌号前的标记，none 为不加） · hint：提示类说明 */')
a('[data-skin]{--c-mark-bg:var(--c-primary-tint); --c-mark-text:var(--c-primary-text); --c-progress-dot:var(--c-st-progress-dot); --c-progress-text:var(--c-st-progress-text);')
a('  --c-due-bg:var(--c-warning-tint); --c-due-text:var(--c-warning-text); --c-due-mark:none; --c-hint-bg:var(--c-info-tint); --c-hint-fg:var(--c-info-text)}')
for key, rule in AVOID.items():
    parts = []
    if rule.get('mark') == 'neutral': parts.append('--c-mark-bg:var(--c-faint); --c-mark-text:var(--c-ink);')
    if rule.get('group'):
        g = rule['group']
        parts.append('--c-st-%s-dot:var(--gray-500); --c-st-%s-text:var(--gray-600); --c-st-%s-tint:var(--gray-100); --c-st-%s-border:var(--gray-300);' % (g, g, g, g))
        if g == 'progress': parts.append('--c-progress-dot:var(--gray-500); --c-progress-text:var(--gray-600);')
    if rule.get('due_mark'): parts.append('--c-due-mark:"¥";')
    if rule.get('hint') == 'neutral': parts.append('--c-hint-bg:var(--c-faint); --c-hint-fg:var(--c-ink);')
    a('[data-skin="%s"]{%s}  /* %s */' % (key, ' '.join(parts), rule['note']))
a('')
a('/* ── 色系 · 黑白灰（data-tone="quiet"，默认；多色为 data-tone="color"，见 docs/全线点餐POS-黑白灰色系方案.md）──')
a(' *   黑白灰承担结构和常态；颜色只留给「要人处理」的两组：待处理 = 琥珀、异常 = 红。')
a(' *   常态按明度分层：进行中 = 墨（正在发生）· 等待中 = gray-700 · 已完成 / 变更标记 = gray-600（退后）· 已关闭 = gray-500；')
a(' *   预约、暂存要提前看到，保留 chip 形式但用中性底（gray-150 + 墨字）。每组只用一档颜色：圆点和文字同档。')
a(' *   分类色退成中性小方块（gray-300），无图大字位用墨字；支付方式图标是品牌色，不在此列。 */')
QUIET = {'action': ('var(--warning-700)', 'var(--warning-700)', 'var(--warning-200)'),
         'failed': ('var(--danger-700)', 'var(--danger-700)', 'var(--danger-200)'),
         'progress': ('var(--gray-900)', 'var(--gray-900)', 'var(--gray-100)'),
         'waiting': ('var(--gray-700)', 'var(--gray-700)', 'var(--gray-100)'),
         'done': ('var(--gray-600)', 'var(--gray-600)', 'var(--gray-100)'),
         'trace': ('var(--gray-600)', 'var(--gray-600)', 'var(--gray-100)'),
         'booked': ('var(--gray-900)', 'var(--gray-900)', 'var(--gray-150)'),
         'held': ('var(--gray-900)', 'var(--gray-900)', 'var(--gray-150)'),
         'closed': ('var(--gray-500)', 'var(--gray-600)', 'var(--gray-100)')}
assert set(QUIET) == set(groups), set(groups) ^ set(QUIET)
parts = ['--c-st-%s-dot:%s; --c-st-%s-text:%s; --c-st-%s-tint:%s;' % (g, d, g, t, g, b) for g, (d, t, b) in QUIET.items()]
parts.append('--c-progress-dot:var(--gray-900); --c-progress-text:var(--gray-900); --c-saving:var(--gray-900);')
# 中性组的状态块不按 30% 混（灰混白几乎看不出），直接取 gray-100：占用中的桌（用餐、预约、暂存）比空闲桌（gray-50）重一档
parts += ['--c-st-%s-block:var(--gray-100); --c-st-%s-block-strong:var(--gray-150);' % (g, g) for g in ['progress', 'waiting', 'done', 'trace', 'booked', 'held']]
parts.append('--c-st-closed-block:var(--gray-50); --c-st-closed-block-strong:var(--gray-100);')
parts += ['--cat-%s-solid:var(--gray-300); --cat-%s-bg:var(--gray-100); --cat-%s-fg:var(--gray-900);' % (k, k, k) for k, *_ in CATEGORIES]
a('[data-tone="quiet"]{' + ' '.join(parts) + '}')
a('')
a('/* ── P1-4：微信绿、青碧的品牌原色偏亮，默认实心用深一档（白字）。品牌方要求贴近原色时，加 data-primary="bright"：500 档 + 墨字。全产品只选一种。 ── */')
for key, f in themes.items():
    if f['bright']:
        a('[data-skin="%s"][data-primary="bright"]{--c-primary:var(--p-500); --c-primary-hover:var(--p-600); --c-primary-pressed:var(--p-700); --c-on-primary:var(--gray-900)}  /* 墨字 / 实心 %.2f:1 */'
          % (key, f['bright']['contrast']))
a('')
a('/* ── 第四层 · 接入旧令牌（data-colors="v2" 时生效）── */')
a(':root[data-colors="v2"]{')
a('  --sh-bg:var(--c-canvas); --sh-card:var(--c-surface); --sh-faint:var(--c-faint); --sh-line:var(--c-line); --sh-ink:var(--c-ink); --sh-sub:var(--c-sub); --sh-disabled:var(--c-placeholder);')
a('  --sh-primary:var(--c-primary); --sh-primary-tint:var(--c-primary-tint); --sh-primary-text:var(--c-primary-text); --sh-on-primary:var(--c-on-primary);')
a('  --sh-success-tint:var(--c-success-tint); --sh-success-text:var(--c-success-text); --sh-warning-tint:var(--c-warning-tint); --sh-warning-text:var(--c-warning-text);')
a('  --sh-danger:var(--c-danger-strong); --sh-danger-tint:var(--c-danger-tint); --sh-danger-text:var(--c-danger-text); --pc-edge:var(--c-edge);')
a('  --sh-wash:var(--c-wash); --sh-wash-strong:var(--c-wash-strong); --sh-sel-bg:var(--c-sel-bg); --sh-sel-fg:var(--c-sel-fg); --sh-on-solid:var(--c-on-solid); --sh-scrim:var(--c-scrim); --sh-saving:var(--c-saving)}')
a(':root[data-colors="v2"] .pc{--sh-bg:var(--c-canvas); --sh-faint:var(--c-wash); --sh-wash:var(--c-wash); --sh-wash-strong:var(--c-wash-strong); --sh-sel-bg:var(--c-sel-bg); --sh-sel-fg:var(--c-sel-fg); --sh-primary-tint:var(--c-primary-tint)}')
for k, *_ in CATEGORIES:
    a(':root[data-colors="v2"] .pc .k-%s{--ks:var(--cat-%s-solid); --kp:var(--cat-%s-bg); --kpf:var(--cat-%s-fg)}' % (k, k, k, k))
a('/* 接入时组件要改的地方（只靠改令牌做不到的）：无图大字位改中性底（P1-1）、关闭态开关 / 勾选框、焦点环、主题标记 / 进行中 / 待结账改用避让角色 */')
a(':root[data-colors="v2"] .pc .dn-dish--img .pic.txt{background:var(--c-slot-bg); color:var(--kpf)}')
a(':root[data-colors="v2"] .pc .sh-switch:not(.is-on){background:var(--c-control-off)}')
a(':root[data-colors="v2"] .pc .sh-check:not(.is-on):not(.is-mixed){background:var(--c-check-off); box-shadow:none}   /* 未选：实心浅灰块，不用描边 */')
a(':root[data-colors="v2"] .pc :focus-visible{outline:2px solid var(--c-focus); outline-offset:2px}')
a(':root[data-colors="v2"] .pc .sh-search{color:var(--c-hint)}')
a(':root[data-colors="v2"] .pc .sh-chip--act{background:var(--c-mark-bg); color:var(--c-mark-text)}')
a(':root[data-colors="v2"] .pc .ost--ok{color:var(--c-progress-text)}')
a(':root[data-colors="v2"] .pc .dn-table--pay b::before{content:var(--c-due-mark); margin-right:2px}')

open(os.path.join(OUT, 'pos-colors.css'), 'w').write('\n'.join(L) + '\n')
json.dump(report, open(os.path.join(OUT, 'pos-colors.json'), 'w'), ensure_ascii=False, indent=1)

# ── 写预览页 ─────────────────────────────────────────────────────────────────
def fg(h): return WHITE if contrast(h, WHITE) >= contrast(h, INK) else INK
def strip(vals, marks):
    return '<div class="cs-ramp">' + ''.join('<div style="background:%s;color:%s">%s<b>%s</b>%s</div>' % (
        h, fg(h), ('<em>%s</em>' % marks[k]) if k in marks else '', k, h) for k, h in vals) + '</div>'
H = []
h = H.append
h('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">')
h('<title>全线点餐 POS · 色彩体系</title>')
h('<!-- 由 tools/gen-colors.py 生成，不要手改。样式在 proto.css「色彩体系预览」一段；色值在 pos-colors.css。 -->')
h('<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Archivo:wght@500;600;700&family=IBM+Plex+Mono:wght@400;500&display=swap">')
h('<link rel="stylesheet" href="proto.css"><link rel="stylesheet" href="pos-colors.css">')
h('<div class="wrap"><header class="top"><div class="eyebrow">全线点餐 POS · 色彩体系 · 2026-10-23（评审 P0、P1 已改入）</div><h1>全线点餐 POS · 色彩体系</h1>')
h('<p class="sub">基本色是黑白灰，承担全部结构；主题色可以调整，每个色系一套 11 档色阶和 8 个角色。状态浅底比主题浅底深一档，撞色的色系另有避让规则。取值见 pos-colors.css，定义与位置清单见 docs/全线点餐POS-色彩体系.md。</p></header>')
h('<section><div class="sec-head"><span class="sec-num">01</span><h2>基本色 · 黑白灰</h2></div>')
h(strip(list(gray.items()), {'0': 'surface', '50': 'canvas · wash（墨黑）', '100': 'faint · line · slot · wash-strong（墨黑）', '150': 'edge', '200': 'strong', '400': 'placeholder', '500': 'control-off', '600': 'sub · hint', '900': 'ink · inverse'}))
b = report['base']
h('<p class="cs-meta">sub / hint 白底 %.2f:1、中性块上 %.2f:1；control-off 白底 %.2f:1；placeholder 白底 %.2f:1（只做示例）。</p></section>' % (b['sub/white'], b['sub/faint'], b['control-off/white'], b['placeholder/white']))
h('<section><div class="sec-head"><span class="sec-num">02</span><h2>主题色 · 7 个色系</h2></div><p class="sec-note">右侧样张用该色系的角色和避让角色：桌台「用餐中 / 待结账」、标签「赠送 / 已退 / 制作中」并排，检查是否分得清。</p>')
for key, f in themes.items():
    r = f['roles']
    marks = {}
    for n, lab in [('primary-tint', '浅底'), ('primary-tint-hover', '浅底悬停'), ('primary-border', '描边'), ('primary', '实心 · 焦点'),
                   ('primary-hover', '悬停'), ('primary-pressed', '按下'), ('primary-text', '文字')]:
        s = r[n]
        marks[s] = (marks[s] + ' · ' if s in marks else '') + lab
    c = report['themes'][key]['contrast']
    note = AVOID.get(key, {}).get('note', '')
    h('<div style="margin-top:28px" data-skin="%s"><h3 style="margin:0 0 8px;font-size:17px">%s <span style="color:var(--muted);font-weight:400">%s · data-skin="%s" · 品牌原色 %s</span></h3><div class="cs-fam"><div>' % (key, f['zh'], f['en'], key, f['base']))
    h(strip([(s, f['ramp'][s]) for s in STEPS], marks))
    h('<p class="cs-meta">白字 / 实心 %.2f:1 · 文字 / 浅底 %.2f:1 · 焦点 / 白 %.2f:1 · 焦点 / 浅底 %.2f:1%s</p></div>' % (
        c['white/primary'], c['text/tint'], c['focus/white'], c['focus/tint'], ('<br>避让：' + note) if note else ''))
    h('<div class="cs-ui"><div class="card">'
      '<div class="row"><span class="tile busy"><b>A03</b><small>用餐中</small></span><span class="tile due"><b>A05</b><small>待结账</small></span><span class="tile"><b>A04</b><small>空闲</small></span></div>'
      '<div class="row"><span class="tag mark">赠送</span><span class="tag mark">会员价</span><span class="tag bad">已退</span><span class="ost2"><i></i>制作中</span><span class="tag warn">催菜中</span></div>'
      '<div class="row"><span class="dish on"><i>2</i><b>宫保鸡丁</b><small>¥48 起</small></span><span class="dish slot"><b>米饭</b><small>Steamed Rice</small></span></div>'
      '<div class="row"><span class="b m">挂单</span><span class="b s">送厨 · 2</span><span class="b p" style="flex:1;justify-content:center">结账</span></div>'
      '<div class="row"><span class="b f">焦点</span><span class="sw"></span><span class="sw off"></span><span class="cb on"></span><span class="cb"></span><span class="inp">搜索菜名 / 拼音 / 编号</span></div>'
      '</div></div></div></div>')
h('</section>')
h('<section><div class="sec-head"><span class="sec-num">03</span><h2>状态色 · 所有主题共用</h2></div>')
for key, f in status.items():
    ro = f['roles']
    marks = {ro['tint']: '浅底', ro['border']: '描边'}
    for n, lab in [('dot', '圆点'), ('text', '文字'), ('strong', '实心')]:
        s = ro[n]
        marks[s] = (marks[s] + ' · ' if s in marks else '') + lab
    c = report['status'][key]['contrast']
    h('<h3 style="margin:20px 0 8px;font-size:15px">%s <span style="color:var(--muted);font-weight:400">%s · 原色 %s · 文字 / 浅底 %.2f:1 · 圆点 / 浅底 %.2f:1</span></h3>' % (f['zh'], key, f['base'], c['text/tint'], c['dot/tint']))
    h(strip([(s, f['ramp'][s]) for s in STEPS], marks))
h('</section>')
h('<section><div class="sec-head"><span class="sec-num">04</span><h2>分类色</h2></div><div class="cs-ramp">' + ''.join(
    '<div style="background:%s;color:%s"><em>%s</em><b>%s</b>%s · %s</div>' % (bg, fgc, n, k, solid, bg) for k, n, solid, bg, fgc in CATEGORIES) + '</div>')
h('<p class="cs-meta">分类色只用于分类小方块和无图大字位的字色（大字位的底改成中性，避免看起来像已选中）。</p></section>')
def st_tag(g, icon, label):
    gg = groups[g]
    style = '--d:var(--c-st-%s-dot);--t:var(--c-st-%s-text);--b:var(--c-st-%s-tint)' % (g, g, g)
    return '<span class="cs-st %s" style="%s"><i data-lucide="%s"></i>%s</span>' % (gg['form'], style, icon, label)
h('<section><div class="sec-head"><span class="sec-num">05</span><h2>辅助色与业务状态</h2></div>')
h('<p class="sec-note">业务状态分 9 组，每组一种颜色和一种默认形式：chip（浅底 + 图标 + 文字，要人注意）或 dot（图标 + 彩色文字，不铺底）。状态一律带图标和文字，颜色只是辅助。图标用 Lucide。完整清单见 docs/全线点餐POS-业务状态.md。</p>')
for key, f in aux.items():
    h('<h3 style="margin:20px 0 8px;font-size:15px">辅助色 · %s <span style="color:var(--muted);font-weight:400">%s · 色相 %d°</span></h3>' % (f['zh'], key, f['hue']))
    h(strip([(s_, f['ramp'][s_]) for s_ in STEPS], {'200': '浅底', '300': '描边'}))
h('<div class="tw" style="margin-top:20px"><table class="spec"><tr><th>分组</th><th>颜色</th><th>形式</th><th>样子</th><th>文字/浅底</th><th>圆点/浅底</th><th>说明</th></tr>')
SAMPLE = {'action': 'receipt', 'progress': 'chef-hat', 'waiting': 'bike', 'done': 'circle-check', 'booked': 'calendar-clock', 'held': 'circle-pause', 'trace': 'repeat', 'closed': 'ban', 'failed': 'circle-alert'}
for gk, g in groups.items():
    fam = g['family']
    famzh = {'warning': '警告', 'info': '信息蓝', 'success': '成功', 'danger': '危险', 'neutral': '中性灰'}.get(fam) or aux[fam]['zh']
    h('<tr><td>%s %s</td><td>%s</td><td>%s</td><td>%s</td><td>%.2f</td><td>%.2f</td><td>%s</td></tr>' % (
        g['zh'], g['en'], famzh, g['form'], st_tag(gk, SAMPLE[gk], g['zh']), g['contrast']['text/tint'], g['contrast']['dot/tint'], g['desc']))
h('</table></div>')
h('<div class="cs-ui" style="margin-top:20px"><div class="card">')
for ek, ezh, een, sts in STATES:
    h('<div class="cs-strow"><b>%s<small>%s</small></b><div class="row">%s</div></div>' % (ezh, een, ''.join(st_tag(st[3], st[4], st[1]) for st in sts)))
h('</div></div>')
h('<p class="cs-meta">撞色主题下的避让：微信绿主题的「已完成」组、经典蓝主题的「进行中」组改为灰色（见色彩体系文档第五节）。这里展示的是默认颜色。</p></section>')
h('<script src="https://unpkg.com/lucide@0.460.0/dist/umd/lucide.min.js"></script><script>window.lucide && lucide.createIcons({attrs:{"stroke-width":1.75}});</script>')
h('<footer>全线点餐 POS · 色彩体系 · 由 tools/gen-colors.py 生成 · 定义 docs/全线点餐POS-色彩体系.md · 业务状态 docs/全线点餐POS-业务状态.md</footer></div>')
open(os.path.join(OUT, 'pos-colors.html'), 'w').write('\n'.join(H) + '\n')

# ── 写业务状态数据（原型读这一份）────────────────────────────────────────────────
JS = {'groups': {gk: {'zh': g['zh'], 'en': g['en'], 'form': g['form']} for gk, g in groups.items()},
      'states': {e: {st[0]: {'zh': st[1], 'en': st[2], 'group': st[3], 'icon': st[4]} for st in sts} for e, _, _, sts in STATES}}
open(os.path.join(OUT, 'pos-states.js'), 'w').write('/* 全线点餐 POS · 业务状态数据 —— 由 tools/gen-colors.py 生成，不要手改。颜色见 pos-colors.css 的 --c-st-{组}-*。 */\n'
    'window.POS_STATES = ' + json.dumps(JS, ensure_ascii=False, separators=(',', ':')) + ';\n')

# ── 写业务状态清单文档 ─────────────────────────────────────────────────────────
D = []
d = D.append
d('# 全线点餐 POS · 业务状态（辅助色与图标）')
d('')
d('> 由 `prototypes/bapp/tools/gen-colors.py` 生成，不要手改；改状态请改脚本里的 `STATES` 和 `GROUPS`。')
d('> 依据：《详细功能清单》A–K（已归档 `bak/docs/全线点餐POS-详细功能清单.md`）与[功能菜单](全线点餐POS-功能菜单.md)。颜色体系见[色彩体系](全线点餐POS-色彩体系.md)第七节。')
d('> 预览：`prototypes/bapp/pos-colors.html` 第 05 节。图标用 [Lucide](https://lucide.dev)（ISC 许可），线宽 1.75。')
d('')
d('## 一、规则')
d('')
d('1. **状态一律「图标 + 文字」**，颜色是辅助；同一组的状态用同一种颜色，同一个意思在不同实体上用同一个图标（例如「制作中」都是 chef-hat）。')
d('2. **按意思分组，不按实体分组。** 9 组覆盖全部状态；新增状态先判断属于哪一组，再挑图标。')
d('3. **两种形式。** chip：浅底 + 图标 + 文字，用于要人注意或要提前看到的组（待处理、预约、暂存、异常）；dot：图标 + 彩色文字、不铺底，用于进行中、等待、完成、变更标记、关闭。chip 组的浅底两两 ΔE ≥ 4；「待处理」（橙）和「异常」（红）最接近，靠图标和文字区分。')
d('4. **浅底统一 200 档**，比主题浅底深一档；文字在浅底上 ≥ 4.5:1，图标和圆点在浅底和白底上 ≥ 3:1。')
d('5. **桌台「用餐中」不是业务状态色**，它用主题浅底表示「这张桌在用」，见色彩体系的位置清单。')
d('6. **撞色避让。** 微信绿主题下「已完成」组、经典蓝主题下「进行中」组改为灰色；其余组靠图标、文字和浅底深浅区分。')
d('')
d('## 二、分组与颜色')
d('')
d('| 分组 | English | 颜色 | 形式 | 圆点 | 文字 | 浅底 | 文字/浅底 | 圆点/浅底 | 含义 |')
d('|---|---|---|---|---|---|---|---|---|---|')
for gk, g in groups.items():
    fam = g['family']
    famzh = {'warning': '警告（状态色）', 'info': '信息蓝（状态色）', 'success': '成功（状态色）', 'danger': '危险（状态色）', 'neutral': '中性灰'}.get(fam) or ('辅助色 · ' + aux[fam]['zh'])
    v = g['values']; c = g['contrast']
    d('| %s `%s` | %s | %s | %s | %s | %s | %s | %.2f | %.2f | %s |' % (g['zh'], gk, g['en'], famzh, g['form'], v['dot'], v['text'], v['tint'], c['text/tint'], c['dot/tint'], g['desc']))
d('')
d('辅助色（新增 4 个色系，色阶规则与主题色相同）：')
d('')
d('| 辅助色 | 键 | 色相 | ' + ' | '.join(STEPS) + ' |')
d('|---|---|---|' + '---|' * len(STEPS))
for key, f in aux.items():
    d('| %s | `%s` | %d° | ' % (f['zh'], key, f['hue']) + ' | '.join(f['ramp'][s_] for s_ in STEPS) + ' |')
d('')
d('## 三、各实体的状态')
d('')
n_states = 0
for ek, ezh, een, sts in STATES:
    d('### %s · %s' % (ezh, een))
    d('')
    d('| 状态 | English | 分组 | 图标（Lucide） | 依据 |')
    d('|---|---|---|---|---|')
    for st in sts:
        n_states += 1
        d('| %s | %s | %s | `%s` | %s |' % (st[1], st[2], groups[st[3]]['zh'], st[4], st[5]))
    d('')
d('合计 %d 个实体、%d 个状态。' % (len(STATES), n_states))
d('')
d('## 四、令牌')
d('')
d('每组四个令牌：`--c-st-{组}-dot`（图标、圆点）、`--c-st-{组}-text`（文字）、`--c-st-{组}-tint`（chip 浅底）、`--c-st-{组}-border`（装饰描边）。组件按状态所属的组取令牌，不直接用色阶。')
open(os.path.join(OUT, '..', '..', 'docs', '全线点餐POS-业务状态.md'), 'w').write('\n'.join(D) + '\n')

# ── 自检：规则写进来的阈值都要成立 ──────────────────────────────────────────
fails = []
for key, t in report['themes'].items():
    c = t['contrast']
    if c['white/primary'] < 4.5: fails.append('%s 白字/实心 %.2f' % (key, c['white/primary']))
    if c['text/tint'] < 4.5: fails.append('%s 文字/浅底 %.2f' % (key, c['text/tint']))
    if c['focus/white'] < 3: fails.append('%s 焦点/白 %.2f' % (key, c['focus/white']))
    for sk, d in t['vs_status'].items():
        avoided = AVOID.get(key, {}).get('status') == sk
        if d['tint'] < 5 and not avoided: fails.append('%s 浅底 vs %s 浅底 ΔE %.1f' % (key, sk, d['tint']))
        dh = abs((t['hue'] - status[sk]['hue'] + 180) % 360 - 180)
        if key != 'mono' and dh < 15 and not avoided: fails.append('%s 与 %s 色相差 %d° < 15°，需要在 AVOID 里加避让规则' % (key, sk, dh))
    for ak, af in aux.items():
        dh = abs((t['hue'] - af['hue'] + 180) % 360 - 180)
        if key != 'mono' and dh < 15 and AVOID.get(key, {}).get('aux') != ak: fails.append('%s 与辅助色 %s 色相差 %d° < 15°，需要避让' % (key, ak, dh))
for key, s in report['status'].items():
    c = s['contrast']
    if c['text/tint'] < 4.5 or c['dot/tint'] < 3 or c['dot/white'] < 3 or c['white/strong'] < 4.5: fails.append('%s %s' % (key, c))
if report['base']['control-off/white'] < 3: fails.append('control-off')
for gk, g in groups.items():
    c = g['contrast']
    if c['text/tint'] < 4.5 or c['dot/tint'] < 3 or c['dot/white'] < 3 or c['text/white'] < 4.5: fails.append('分组 %s %s' % (gk, c))
gl = list(groups.items())
for i in range(len(gl)):
    for j in range(i + 1, len(gl)):
        de = delta_e(gl[i][1]['values']['tint'], gl[j][1]['values']['tint'])
        if de < 4: fails.append('分组 %s 与 %s 浅底太近 ΔE %.1f' % (gl[i][0], gl[j][0], de))
icons_bad = [st for _, _, _, sts in STATES for st in sts if st[3] not in groups]
if icons_bad: fails.append('未知分组 %s' % icons_bad)
print('业务状态：%d 组 · %d 个实体 · %d 个状态；分组浅底两两最小 ΔE %.1f' % (len(groups), len(STATES), sum(len(x[3]) for x in STATES),
      min(delta_e(gl[i][1]['values']['tint'], gl[j][1]['values']['tint']) for i in range(len(gl)) for j in range(i + 1, len(gl)))))
print('写出：pos-colors.css · pos-colors.json · pos-colors.html · pos-states.js · docs/全线点餐POS-业务状态.md')
print('自检：' + ('全部通过' if not fails else '；'.join(fails)))
