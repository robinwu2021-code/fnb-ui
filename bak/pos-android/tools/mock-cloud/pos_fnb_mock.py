#!/usr/bin/env python3
"""
餐饮服务端 POS 合同（ai-shop docs/api/pos-fnb 草案 v0.1）本地模拟：只实现 POS 第一批对接的接口。

  python3 tools/mock-cloud/pos_fnb_mock.py [--port 8790] [--token T] [--store NS002] [--tables H01,H02,...]

POS 里「云端设置」：地址 http://10.0.2.2:8790/biz/pos/fnb（模拟器）或 http://<电脑内网IP>:8790/biz/pos/fnb（平板），门店号 NS002。

行为（按「不一致 API 评审」定稿）：
- 信封 {code,msg,data}，业务错误 HTTP 200；10400 校验 / 10401 未登录 / 10404 不存在 / 10409 版本冲突。
- 写接口必须带 Idempotency-Key 头（只走头，不放 body）；同键同内容返回首次结果，同键不同内容 10409。
- 请求体严格校验：出现未知字段 10400。
- X-Store-No 必须与 storeNo 一致。
- 调试：GET /_mock/state 看全部数据与调用记录；POST /_mock/fail {"times":N,"code":...} 让后续 N 次请求失败；POST /_mock/reset。
"""
import argparse
import json
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PREFIX = "/biz/pos/fnb"
LOCK = threading.Lock()


class Err(Exception):
    def __init__(self, code, msg):
        super().__init__(msg)
        self.code, self.msg = code, msg


def fresh(store_no, table_names):
    return {
        "store": {"storeNo": store_no, "name": "南山小馆 · 海岸店（模拟）", "timeZone": "Asia/Shanghai", "currency": "CNY"},
        "tables": {f"T{i + 1:03d}": {"tableNo": f"T{i + 1:03d}", "name": n, "areaNo": "A1", "status": "IDLE", "checkNo": None, "version": 1}
                   for i, n in enumerate(table_names)},
        "checks": {},
        "closes": {},
        "idem": {},
        "calls": [],
        "fail": {"times": 0, "code": 0, "http": 0},
    }


DB = {}
CFG = {}

WRITE_FIELDS = {
    "/table/open": {"storeNo", "tableNo", "guestCount"},
    "/table/transfer": {"storeNo", "checkNo", "targetTableNo", "version"},
    "/close/preview": {"storeNo", "businessDate"},
    "/close/sign": {"storeNo", "closeNo", "version"},
}


def need(body, *keys):
    for k in keys:
        if body.get(k) in (None, ""):
            raise Err(10400, f"缺少字段 {k}")


def check_store(body_store, header_store):
    if body_store != DB["store"]["storeNo"]:
        raise Err(10404, f"门店不存在：{body_store}")
    if header_store and header_store != body_store:
        raise Err(10400, "X-Store-No 与 storeNo 不一致")


def table(no):
    t = DB["tables"].get(no)
    if not t:
        raise Err(10404, f"桌台不存在：{no}")
    return t


def check(no):
    c = DB["checks"].get(no)
    if not c:
        raise Err(10404, f"账单不存在：{no}")
    return c


def zero_amounts():
    return {"currency": "CNY", "subtotalMinor": 0, "discountMinor": 0, "taxMinor": 0, "payableMinor": 0, "paidMinor": 0, "refundedMinor": 0}


# —— 读接口 ——
def r_store_info(q, h):
    check_store(q.get("storeNo"), h)
    return {"store": DB["store"], "businessDate": time.strftime("%Y-%m-%d"), "payModes": ["CASH", "QR", "CARD", "STORED_VALUE"],
            "capabilities": ["TABLE_OPEN", "TABLE_TRANSFER", "CLOSE"]}


def r_table_list(q, h):
    check_store(q.get("storeNo"), h)
    rows = list(DB["tables"].values())
    return {"records": rows, "total": len(rows), "page": 1, "size": len(rows)}


def r_check_detail(q, h):
    check_store(q.get("storeNo"), h)
    return {"check": check(q.get("checkNo")), "lines": [], "amounts": zero_amounts()}


def r_menu_list(q, h):
    check_store(q.get("storeNo"), h)
    return {"menus": [{"menuNo": "M1", "name": "全天菜单", "revision": "1", "channel": "POS"}]}


# —— 写接口 ——
def w_table_open(b, h):
    need(b, "storeNo", "tableNo", "guestCount")
    check_store(b["storeNo"], h)
    t = table(b["tableNo"])
    if t["status"] != "IDLE":
        raise Err(10400, f"桌台 {t['name']} 不空闲")
    no = "CK" + uuid.uuid4().hex[:8].upper()
    c = {"checkNo": no, "status": "OPEN", "tableNos": [t["tableNo"]], "guestCount": int(b["guestCount"]), "payMode": "", "version": 1}
    DB["checks"][no] = c
    t.update(status="IN_USE", checkNo=no, version=t["version"] + 1)
    return {"check": c, "table": t}


def w_table_transfer(b, h):
    need(b, "storeNo", "checkNo", "targetTableNo", "version")
    check_store(b["storeNo"], h)
    c = check(b["checkNo"])
    if int(b["version"]) != c["version"]:
        raise Err(10409, f"账单版本已变化（当前 {c['version']}）")
    target = table(b["targetTableNo"])
    if target["status"] != "IDLE":
        raise Err(10400, f"目标桌 {target['name']} 不空闲")
    for no in c["tableNos"]:
        src = DB["tables"][no]
        src.update(status="IDLE", checkNo=None, version=src["version"] + 1)
    target.update(status="IN_USE", checkNo=c["checkNo"], version=target["version"] + 1)
    c.update(tableNos=[target["tableNo"]], version=c["version"] + 1)
    return {"check": c}


def w_close_preview(b, h):
    need(b, "storeNo", "businessDate")
    check_store(b["storeNo"], h)
    blocking = [{"type": "OPEN_CHECK", "referenceNo": c["checkNo"], "reason": f"账单 {c['checkNo']} 未结"}
                for c in DB["checks"].values() if c["status"] == "OPEN"]
    no = "CL" + b["businessDate"].replace("-", "")
    cl = DB["closes"].setdefault(no, {"closeNo": no, "status": "PREVIEW", "version": 1, "businessDate": b["businessDate"]})
    if cl["status"] == "PREVIEW":
        cl["version"] += 0
    return {"closeNo": no, "status": cl["status"], "amounts": zero_amounts(), "blockingItems": blocking, "version": cl["version"]}


def w_close_sign(b, h):
    need(b, "storeNo", "closeNo", "version")
    check_store(b["storeNo"], h)
    cl = DB["closes"].get(b["closeNo"])
    if not cl:
        raise Err(10404, "日结单不存在，请先预览")
    if int(b["version"]) != cl["version"]:
        raise Err(10409, "日结单版本已变化")
    if cl["status"] == "SIGNED":
        raise Err(10400, "该营业日已签核")
    cl.update(status="SIGNED", version=cl["version"] + 1, signedAt=time.strftime("%Y-%m-%dT%H:%M:%S+08:00"))
    return {"closeNo": cl["closeNo"], "status": "SIGNED", "signedAt": cl["signedAt"]}


READS = {"/store/info": r_store_info, "/table/list": r_table_list, "/check/detail": r_check_detail, "/menu/list": r_menu_list}
WRITES = {"/table/open": w_table_open, "/table/transfer": w_table_transfer, "/close/preview": w_close_preview, "/close/sign": w_close_sign}


class H(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("[mock]", self.command, self.path, "→", *args[1:2])

    def send(self, status, obj):
        data = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def env(self, data=None, code=0, msg="ok"):
        self.send(200, {"code": code, "msg": msg, "data": data})

    def debug(self, path, body):
        if path == "/_mock/state":
            return self.send(200, {k: v for k, v in DB.items() if k != "idem"})
        if path == "/_mock/reset":
            DB.clear(); DB.update(fresh(CFG["store"], CFG["tables"])); return self.send(200, {"ok": True})
        if path == "/_mock/fail":
            DB["fail"] = {"times": int(body.get("times", 1)), "code": int(body.get("code", 0)), "http": int(body.get("http", 0))}
            return self.send(200, DB["fail"])
        if path == "/_mock/occupy":   # 让服务端某桌被占（制造业务拒绝）；status=IDLE 则空出
            t = next((t for t in DB["tables"].values() if t["name"] == body.get("name")), None)
            if t: t.update(status=body.get("status", "IN_USE"), version=t["version"] + 1)
            return self.send(200, t or {})
        self.send(404, {})

    def handle_any(self, method):
        u = urlparse(self.path)
        raw = self.rfile.read(int(self.headers.get("Content-Length") or 0)) if method == "POST" else b""
        try:
            body = json.loads(raw or b"{}")
        except ValueError:
            return self.env(None, 10400, "请求体不是 JSON")
        with LOCK:
            if u.path.startswith("/_mock/"):
                return self.debug(u.path, body)
            if not u.path.startswith(PREFIX):
                return self.send(404, {"code": 10404, "msg": "no route"})
            route = u.path[len(PREFIX):]
            key = self.headers.get("Idempotency-Key")
            DB["calls"].append({"at": time.strftime("%H:%M:%S"), "method": method, "route": route, "key": key, "operator": self.headers.get("X-Operator")})
            f = DB["fail"]
            if f["times"] > 0:
                f["times"] -= 1
                if f["http"]:
                    return self.send(f["http"], {"error": "mock"})
                return self.env(None, f["code"] or 50000, "模拟失败")
            if CFG["token"] and self.headers.get("Authorization") != f"Bearer {CFG['token']}":
                return self.send(401, {"code": 10401, "msg": "未登录"})
            try:
                if method == "GET" and route in READS:
                    q = {k: v[0] for k, v in parse_qs(u.query).items()}
                    return self.env(READS[route](q, self.headers.get("X-Store-No")))
                if method == "POST" and route in WRITES:
                    if not key:
                        raise Err(10400, "缺少 Idempotency-Key")
                    extra = set(body) - WRITE_FIELDS[route]
                    if extra:
                        raise Err(10400, f"未知字段：{','.join(sorted(extra))}")
                    sig = json.dumps({k: v for k, v in body.items() if k != "idempotencyKey"}, sort_keys=True)
                    hit = DB["idem"].get(key)
                    if hit:
                        if hit["route"] != route or hit["sig"] != sig:
                            raise Err(10409, "幂等键已用于不同请求")
                        return self.send(200, hit["resp"])
                    try:
                        resp = {"code": 0, "msg": "ok", "data": WRITES[route](body, self.headers.get("X-Store-No"))}
                    except Err as e:
                        resp = {"code": e.code, "msg": e.msg, "data": None}
                    if resp["code"] in (0, 10400, 10404):   # 业务结果固定下来；冲突不记，允许重读后同键重试
                        DB["idem"][key] = {"route": route, "sig": sig, "resp": resp}
                    return self.send(200, resp)
                raise Err(10404, f"未实现：{method} {route}")
            except Err as e:
                return self.env(None, e.code, e.msg)

    def do_GET(self):
        self.handle_any("GET")

    def do_POST(self):
        self.handle_any("POST")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8790)
    ap.add_argument("--token", default="")
    ap.add_argument("--store", default="NS002")
    ap.add_argument("--tables", default="H01,H02,H03,H04,T01,T02")
    a = ap.parse_args()
    CFG.update(token=a.token, store=a.store, tables=a.tables.split(","))
    DB.update(fresh(a.store, CFG["tables"]))
    print(f"POS 模拟服务端 http://0.0.0.0:{a.port}{PREFIX}  门店 {a.store}")
    ThreadingHTTPServer(("0.0.0.0", a.port), H).serve_forever()
