// 设备示例数据（本地模拟，不接硬件）。接入方式见方案 §1.2：打印机走 蓝牙扫描取 IP / 直接填 IP / 扫码；副 POS、厨房屏走 mDNS 或扫码配对。
export type DeviceKind = 'printer' | 'pos' | 'kds' | 'cds' | 'scale' | 'scanner' | 'pickup';
export type DeviceSt = 'online' | 'offline' | 'no_paper' | 'unplugged' | 'to_sync' | 'sync_failed' | 'found' | 'pairing';
export type Proto = 'escpos' | 'tspl' | 'cpcl';
export type Via = 'ble' | 'ip' | 'qr' | 'mdns' | 'usb' | 'serial' | 'hdmi';
export interface Device {
  id: string; name: string; en: string; kind: DeviceKind; st: DeviceSt;
  ip?: string; port?: number; proto?: Proto; conn?: string; via?: Via;
  use?: string[]; drawer?: boolean; model?: string; fw?: string; role?: 'main' | 'sub'; lastSync?: string; addedAt?: string; todo?: boolean;
}
/** [类型, 图标, 词表 key]；排序 = 筛选和「添加设备」第一步的顺序 */
export const KINDS: [DeviceKind, string][] = [['printer', 'printer'], ['pos', 'tablet-smartphone'], ['kds', 'monitor'], ['cds', 'monitor-smartphone'], ['scale', 'scale'], ['scanner', 'scan-barcode'], ['pickup', 'megaphone']];
export const kindIcon = (k: DeviceKind) => KINDS.find((x) => x[0] === k)?.[1] ?? 'box';
/** 打印机用途 / 厨房屏档口 */
export const PRINTER_USES = ['bill', 'receipt', 'kitchen', 'drink', 'label'];
export const KDS_STATIONS = ['hot', 'cold', 'bar', 'pastry'];
export const PROTOS: Proto[] = ['escpos', 'tspl', 'cpcl'];

export const INITIAL_DEVICES: Device[] = [
  { id: 'p1', name: '前台打印机', en: 'Front printer', kind: 'printer', st: 'online', ip: '192.168.1.21', port: 9100, proto: 'escpos', via: 'ble', use: ['bill', 'receipt'], drawer: true, model: 'XP-80C', fw: 'v2.3.1', addedAt: '11-03' },
  { id: 'p2', name: '热菜档口', en: 'Hot station', kind: 'printer', st: 'no_paper', ip: '192.168.1.31', port: 9100, proto: 'escpos', via: 'ip', use: ['kitchen'], model: 'GP-80250', fw: 'v1.8.0', addedAt: '11-03' },
  { id: 'p3', name: '凉菜档口', en: 'Cold station', kind: 'printer', st: 'online', ip: '192.168.1.32', port: 9100, proto: 'escpos', via: 'ip', use: ['kitchen'], model: 'GP-80250', fw: 'v1.8.0', addedAt: '11-03' },
  { id: 'p4', name: '吧台', en: 'Bar', kind: 'printer', st: 'online', ip: '192.168.1.33', port: 9100, proto: 'escpos', via: 'qr', use: ['drink'], model: 'XP-58IIH', fw: 'v2.0.4', addedAt: '11-05' },
  { id: 'p5', name: '标签机', en: 'Label printer', kind: 'printer', st: 'offline', ip: '192.168.1.35', port: 9100, proto: 'tspl', via: 'ip', use: ['label'], model: 'XP-365B', fw: 'v1.2.0', addedAt: '11-06' },
  { id: 'pos2', name: 'POS-02 前台右', en: 'POS-02 Front right', kind: 'pos', st: 'online', ip: '192.168.1.12', via: 'mdns', role: 'sub', lastSync: '12:29', model: 'Sunmi T2s', fw: '2.6.0', addedAt: '10-28' },
  { id: 'pad1', name: 'PAD-01 服务员', en: 'PAD-01 Waiter', kind: 'pos', st: 'to_sync', ip: '192.168.1.15', via: 'qr', role: 'sub', lastSync: '12:02', model: 'iPad 10', fw: '2.6.0', addedAt: '10-30' },
  { id: 'kds1', name: 'KDS-热菜', en: 'KDS Hot', kind: 'kds', st: 'online', ip: '192.168.1.41', via: 'mdns', use: ['hot'], model: 'KDS-15', fw: '1.4.2', addedAt: '11-01' },
  { id: 'kds2', name: 'KDS-凉菜', en: 'KDS Cold', kind: 'kds', st: 'offline', ip: '192.168.1.43', via: 'mdns', use: ['cold'], model: 'KDS-15', fw: '1.4.2', addedAt: '11-01' },
  { id: 'cds1', name: '客显 1', en: 'Customer display 1', kind: 'cds', st: 'online', conn: 'HDMI-2', via: 'hdmi', model: '10.1" 1280×800', addedAt: '10-28' },
  { id: 'scale1', name: '电子秤', en: 'Scale', kind: 'scale', st: 'unplugged', conn: 'COM3', via: 'serial', model: 'Dahua TM-15', addedAt: '11-02' },
  { id: 'scan1', name: '扫码枪', en: 'Scanner', kind: 'scanner', st: 'online', conn: 'USB-HID', via: 'usb', model: 'Honeywell 1470g', addedAt: '10-28' },
  { id: 'pick1', name: '取餐叫号屏', en: 'Pickup screen', kind: 'pickup', st: 'unplugged', via: 'mdns', todo: true },
  // 附近发现（mDNS 广播，未添加）
  { id: 'kds3', name: 'KDS-02', en: 'KDS-02', kind: 'kds', st: 'found', ip: '192.168.1.42', via: 'mdns', model: 'KDS-15', fw: '1.4.2' },
  { id: 'pad3', name: 'PAD-03', en: 'PAD-03', kind: 'pos', st: 'found', ip: '192.168.1.18', via: 'mdns', model: 'iPad 10', fw: '2.6.0' },
];

/** 蓝牙扫描结果（模拟）。ip 为空 = 打印机没配网；net:false = 读到了 IP 但连不上（不同网段） */
export interface BleHit { name: string; mac: string; rssi: number; ip?: string; proto?: Proto; model?: string; net?: boolean }
export const BLE_HITS: BleHit[] = [
  { name: 'Printer_A3F2', mac: 'DC:0D:30:A3:F2:11', rssi: -48, ip: '192.168.1.52', proto: 'escpos', model: 'XP-80T' },
  { name: 'XP-365B', mac: '66:22:B1:07:5D:3A', rssi: -60, ip: '192.168.1.57', proto: 'tspl', model: 'XP-365B' },
  { name: 'GP-L80250', mac: '00:15:83:4C:9E:02', rssi: -66 },
  { name: 'MTP-II', mac: '86:67:7A:12:C4:90', rssi: -79, ip: '192.168.3.20', proto: 'escpos', model: 'MTP-II', net: false },
];
/** 直接填 IP 的模拟规则：同网段 192.168.1.x 能连上 */
export const ipValid = (ip: string) => /^(25[0-5]|2[0-4]\d|1?\d?\d)(\.(25[0-5]|2[0-4]\d|1?\d?\d)){3}$/.test(ip);
export const ipReachable = (ip: string) => /^192\.168\.1\.\d+$/.test(ip);
/** 扫码约定格式（方案 §1.2 C）：fnbpos://printer?ip=…&port=…&proto=…&name=… */
export const DEMO_QR = 'fnbpos://printer?ip=192.168.1.61&port=9100&proto=escpos&name=XP-80C';
export function parseQr(s: string): { ip: string; port: number; proto: Proto; name: string } | null {
  const m = /^fnbpos:\/\/printer\?(.*)$/.exec(s); if (!m) return null;
  const q = new URLSearchParams(m[1]); const ip = q.get('ip') || '';
  return ipValid(ip) ? { ip, port: Number(q.get('port') || 9100), proto: (q.get('proto') as Proto) || 'escpos', name: q.get('name') || '' } : null;
}
/** 客显、秤、扫码枪的本机端口（模拟） */
export const DISPLAY_OUTS = [{ id: 'HDMI-2', label: 'HDMI-2', model: '10.1" 1280×800' }, { id: 'DP-1', label: 'USB-C · DP-1', model: '15.6" 1920×1080' }];
export const SERIAL_PORTS = [{ id: 'COM3', label: 'COM3' }, { id: 'COM4', label: 'COM4' }, { id: 'USB-HID', label: 'USB-HID' }, { id: 'USB-HID-2', label: 'USB-HID #2' }];
export const SCALE_PROTOS = ['dahua', 'aclas', 'generic'];
