#!/usr/bin/env python3
"""
餐饮服务端本地模拟：按现有 Biz 接口（/biz/x/food）与门店推送契约（/biz/push、/biz/stores/{s}/push/stream），
只实现 POS 已对接的部分。真实后端上线前用来联调。

  python3 -u tools/mock-cloud/pos_fnb_mock.py [--port 8790] [--token T] [--store NS002] [--tables H01,H02,...]
                                              [--catalog tools/mock-cloud/demo-catalog.json]

POS「云端设置」：地址 http://10.0.2.2:8790（模拟器）或 http://<电脑内网IP>:8790（平板），门店号 NS002。
demo-catalog.json 由 DemoCatalogExport 单测从演示门店生成（菜名、规格名与本机一致）。

行为（按后端评审结论）：
- 信封 {code,msg,data}，业务错误 HTTP 200；10400 校验 / 10404 不存在 / 10409 版本冲突。
- 写接口必须带 Idempotency-Key 头（报价确认除外）；同键同内容返回首次结果，同键不同内容 10409。
- 请求体严格校验：出现未知字段 10400；门店只认 X-Store-No 与路径 {storeNo}，二者不一致 10403。
- 推送：登记终端拿端号；SSE 帧 event=push 带 id；定向消息 event=message；心跳为注释行；?hold=秒 控制连接保持时长。
- 调试：GET /_mock/state；POST /_mock/fail {"times":N,"http":503|"code":...}；POST /_mock/reset；
  POST /_mock/occupy {"name":"H04","state":"IN_USE|IDLE"}；POST /_mock/notify {"topic":..,"refNo":..} 发推送；
  POST /_mock/direct {"kind":"app.upgrade"} 发定向消息；POST /_mock/price {"factor":1.1} 服务端调价（制造价差）。
"""
import argparse
import json
import queue
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

FOOD = "/biz/x/food"
LOCK = threading.Lock()
DB, CFG = {}, {}
SUBSCRIBERS = []          # [(storeNo, terminalNo, queue)]


class Err(Exception):
    def __init__(self, code, msg):
        super().__init__(msg)
        self.code, self.msg = code, msg


def fresh():
    return {
        "tables": {f"T{i + 1:03d}": {"tableNo": f"T{i + 1:03d}", "storeNo": CFG["store"], "areaNo": "A1", "code": n, "name": n, "capacity": 4,
                                    "sortNo": i, "status": "ACTIVE", "turnoverState": "IDLE", "version": 1}
                   for i, n in enumerate(CFG["tables"])},
        "checks": {}, "carts": {}, "quotes": {}, "batches": {}, "closes": {}, "settlements": {}, "records": {}, "tasks": {}, "invdocs": {}, "onhand": {},
        "idem": {}, "calls": [], "fail": {"times": 0, "code": 0, "http": 0},
        "terminals": {}, "events": [], "seq": 0,
    }


def nid(prefix):
    return prefix + uuid.uuid4().hex[:8].upper()


def need(body, *keys):
    for k in keys:
        if body.get(k) in (None, "", []):
            raise Err(10400, f"缺少字段 {k}")


def strict(body, allowed):
    extra = set(body) - set(allowed)
    if extra:
        raise Err(10400, f"未知字段：{','.join(sorted(extra))}")


def table(no):
    t = DB["tables"].get(no)
    if not t:
        raise Err(10404, f"桌台不存在：{no}")
    return t


def check_versions(vs):
    for v in vs or []:
        if table(v["tableNo"])["version"] != v["version"]:
            raise Err(10409, f"桌台 {v['tableNo']} 版本已变化")


def bump(o, **kw):
    o.update(kw)
    o["version"] += 1
    return o


def emit(topic, ref_type, ref_no, ref_version):
    DB["seq"] += 1
    f = {"seq": DB["seq"], "topic": topic, "refType": ref_type, "refNo": ref_no, "refVersion": ref_version, "targetDevice": None,
         "occurredAt": time.strftime("%Y-%m-%dT%H:%M:%S+08:00")}
    DB["events"].append(f)
    for (_s, _t, q) in list(SUBSCRIBERS):
        q.put(("push", f))


# —— 桌台 / 账单 ——
def r_tables(store, q, b):
    rows = list(DB["tables"].values())
    page, size = int(q.get("page", 1)), int(q.get("size", 20))
    return {"records": rows[(page - 1) * size: page * size], "total": len(rows), "page": page, "size": size}


def w_open_check(store, q, b):
    strict(b, {"tableNos", "partySize", "payMode", "reservationNo", "tableVersions", "sharingMode", "seats", "serviceEmployeeNo"})
    need(b, "tableNos", "partySize", "payMode", "sharingMode")
    if b.get("seats") is None:
        raise Err(10400, "seats 不能为null")           # 真实后端：seats 必须有（可为空数组）
    if b.get("serviceEmployeeNo"):
        raise Err(10403, "serviceEmployeeNo 暂不接收")
    check_versions(b.get("tableVersions"))
    ts = [table(n) for n in b["tableNos"]]
    for t in ts:
        if t["turnoverState"] != "IDLE":
            raise Err(10400, f"桌台 {t['name']} 不空闲")
    c = {"checkNo": nid("CK"), "storeNo": store, "businessDate": time.strftime("%Y-%m-%d"), "status": "OPEN", "payMode": b.get("payMode"),
         "partySize": int(b["partySize"]), "tableNos": b["tableNos"], "version": 1}
    DB["checks"][c["checkNo"]] = c
    for t in ts:
        bump(t, turnoverState="IN_USE")
    emit("food.table", "CHECK", c["checkNo"], 1)
    return {"check": c}


def check(no):
    c = DB["checks"].get(no)
    if not c:
        raise Err(10404, f"账单不存在：{no}")
    return c


def r_check(store, no, q, b):
    return {"check": check(no), "seats": [], "occupancies": []}


def w_transfer(store, no, q, b):
    strict(b, {"version", "reason", "approvalRef", "targetTableNos", "tableVersions"})
    need(b, "version", "targetTableNos")
    c = check(no)
    if int(b["version"]) != c["version"]:
        raise Err(10409, f"账单版本已变化（当前 {c['version']}）")
    check_versions(b.get("tableVersions"))
    targets = [table(n) for n in b["targetTableNos"]]
    for t in targets:
        if t["turnoverState"] != "IDLE":
            raise Err(10400, f"目标桌 {t['name']} 不空闲")
    for n in c["tableNos"]:
        bump(DB["tables"][n], turnoverState="IDLE")
    for t in targets:
        bump(t, turnoverState="IN_USE")
    bump(c, tableNos=b["targetTableNos"])
    emit("food.table", "CHECK", no, c["version"])
    return {"operation": {"operationNo": nid("OP"), "status": "DONE", "version": 1, "resultRef": no}}


# —— 目录 / 购物车 / 报价 / 下单 ——
def r_catalog(store, q, b):
    return dict(CFG["catalog"], storeNo=store, orderSource=q.get("orderSource"), fulfillment=q.get("fulfillment"))


def w_create_cart(store, q, b):
    strict(b, {"checkNo", "sessionNo", "buyerNo", "orderSource", "fulfillment", "scheduledAt"})
    need(b, "checkNo")
    c = check(b["checkNo"])
    if c["status"] != "OPEN":
        raise Err(10400, "账单不是开台状态")
    cart = {"cartNo": nid("CT"), "storeNo": store, "checkNo": c["checkNo"], "status": "OPEN", "orderSource": b.get("orderSource", "POS"),
            "fulfillment": b.get("fulfillment", "DINE_IN"), "version": 0, "sessionNo": None, "buyerNo": None, "lines": []}
    DB["carts"][cart["cartNo"]] = cart
    return {"cart": cart}


def cart(no):
    c = DB["carts"].get(no)
    if not c:
        raise Err(10404, f"购物车不存在：{no}")
    return c


def r_cart(store, no, q, b):
    return {"cart": cart(no)}


LINE_FIELDS = {"sourceLineRef", "menuNo", "menuItemNo", "menuRevisionNo", "goodsNo", "skuNo", "qty", "seatNo", "courseNo", "selections", "bundleChoices"}


def w_replace_line(store, no, ref, q, b):
    strict(b, {"line", "version"})
    need(b, "line", "version")
    line = b["line"]
    strict(line, LINE_FIELDS)
    if line.get("selections") is None or line.get("bundleChoices") is None:
        raise Err(10400, "请求参数有误")                 # 真实后端：selections / bundleChoices 必须带（空数组即可）
    if not isinstance(line.get("qty"), (int, float)) or isinstance(line.get("qty"), bool):
        raise Err(10400, "qty 必须是数字")
    c = cart(no)
    if int(b["version"]) != c["version"]:
        raise Err(10409, f"购物车版本已变化（当前 {c['version']}）")
    item = next((i for i in CFG["catalog"]["items"] if i["menuItemNo"] == line.get("menuItemNo")), None)
    if not item or not any(s["skuNo"] == line.get("skuNo") for s in item["skus"]):
        raise Err(10400, "菜单项或 SKU 不存在")
    c["lines"] = [x for x in c["lines"] if x["sourceLineRef"] != ref] + [dict(line, sourceLineRef=ref)]
    bump(c)
    return {"cartNo": no, "version": c["version"]}


def price_of(line):
    for i in CFG["catalog"]["items"]:
        for s in i["skus"]:
            if s["skuNo"] == line["skuNo"]:
                return int(round(int(s.get("priceMinor") or 0) * DB.get("priceFactor", 1.0)))
    return 0


def w_quote(store, q, b):
    strict(b, {"cartNo", "version", "selection"})
    need(b, "cartNo", "version")
    c = cart(b["cartNo"])
    if int(b["version"]) != c["version"]:
        raise Err(10409, "购物车版本已变化")
    total = sum(price_of(l) * l["qty"] for l in c["lines"])
    qt = {"quoteNo": nid("QT"), "cartNo": c["cartNo"], "version": c["version"], "confirmationDigest": uuid.uuid4().hex + uuid.uuid4().hex,   # 64 位小写 hex
          "totals": {"goodsMinor": total, "discountMinor": 0, "netMinor": total, "taxMinor": 0, "receivableMinor": total, "remainingDueMinor": total, "feeMinor": 0}}
    DB["quotes"][qt["quoteNo"]] = qt
    return qt


def w_confirm(store, no, q, b):
    strict(b, {"cartNo", "version", "confirmationDigest"})
    qt = DB["quotes"].get(no)
    if not qt:
        raise Err(10404, "报价不存在")
    if qt["confirmationDigest"] != b.get("confirmationDigest") or qt["version"] != int(b.get("version", -1)):
        raise Err(10409, "报价已变化，请重新报价")
    qt["confirmed"] = True
    return qt


def w_batch(store, q, b):
    strict(b, {"buyerNo", "cartNo", "version", "quoteNo", "confirmationDigest", "payMode", "partyNo"})
    need(b, "cartNo", "version", "quoteNo", "confirmationDigest")
    if b.get("payMode", "POSTPAY") != "POSTPAY":
        raise Err(10400, "员工下单只支持 POSTPAY")
    if bool(b.get("buyerNo")) == bool(b.get("partyNo")):
        raise Err(10400, "请求参数有误")                 # 真实后端：必须且只能带 buyerNo 与 partyNo 之一
    c = cart(b["cartNo"])
    qt = DB["quotes"].get(b["quoteNo"])
    if not qt or not qt.get("confirmed") or qt["confirmationDigest"] != b["confirmationDigest"] or c["version"] != int(b["version"]):
        raise Err(10409, "报价未确认或购物车已变化")
    c["status"] = "CLOSED"
    bt = {"batchNo": nid("BT"), "storeNo": store, "checkNo": c["checkNo"], "cartNo": c["cartNo"], "cartVersion": c["version"], "quoteNo": qt["quoteNo"],
          "status": "ACCEPTED", "payMode": "POSTPAY", "businessDate": time.strftime("%Y-%m-%d"), "currency": "CNY",
          "amountMinor": qt["totals"]["receivableMinor"], "version": 1, "lines": c["lines"]}
    DB["batches"][bt["batchNo"]] = bt
    for l in c["lines"]:
        t = {"taskNo": nid("TK"), "batchNo": bt["batchNo"], "checkNo": c["checkNo"], "stationNo": "STA1", "status": "QUEUED", "version": 0, "qty": float(l["qty"])}
        DB["tasks"][t["taskNo"]] = t
    emit("food.kitchen", "BATCH", bt["batchNo"], 1)
    return {k: v for k, v in bt.items() if k != "lines"}


# —— 柜台后付整桌结账 + 外部记账 ——
class Biz(Err):
    pass


def check_due(check_no):
    """服务端应收：本账单各下单批次金额合计，加服务费（--service-bp，默认 500 = 5%，与演示门店一致）"""
    goods = sum(b["amountMinor"] for b in DB["batches"].values() if b["checkNo"] == check_no)
    return int(round(goods * (10000 + CFG["service_bp"]) / 10000))


def w_counter_settle(store, no, q, b):
    if b:
        raise Err(10400, "结算单不收请求体")
    c = check(no)
    if c["status"] != "OPEN":
        raise Err(10400, "账单不是开台状态")
    if any(st["checkNo"] == no and st["status"] in ("FROZEN", "COLLECTING") for st in DB["settlements"].values()):
        raise Err(10409, "该台账已有未结束的结算单")
    st = {"settlementNo": nid("CS"), "checkNo": no, "status": "FROZEN", "currency": "CNY", "dueMinor": check_due(no),
          "receivedMinor": None, "changeMinor": None, "receiptNo": None, "items": [], "checkStatus": "OPEN", "tableReleased": False}
    DB["settlements"][st["settlementNo"]] = st
    return st


def settlement(no):
    st = DB["settlements"].get(no)
    if not st:
        raise Err(10404, "结算单不存在")
    return st


def w_finalize(store, no, q, b):
    strict(b, {"offlineRecordNos"})
    nos = b.get("offlineRecordNos") or []
    if not 1 <= len(nos) <= 20:
        raise Err(10400, "offlineRecordNos 1–20 个")
    st = settlement(no)
    if st["status"] == "SETTLED":
        return st                                   # 按结算单号幂等
    if st["status"] == "CANCELLED":
        raise Err(10400, "结算单已取消")
    recs = []
    for r in nos:
        rec = DB["records"].get(r)
        if not rec or rec["bizNo"] != no or rec["currency"] != st["currency"] or rec.get("usedBy") not in (None, no):
            raise Err(20026, "记账号无效")
        recs.append(rec)
    total = sum(r["amountMinor"] for r in recs)
    cash = sum(r["amountMinor"] for r in recs if r["method"] == "CASH")
    if total < st["dueMinor"]:
        d = st["dueMinor"] - total
        raise Err(20024, f"收款还差 {d // 100}.{d % 100:02d} CNY，收齐后再确认")
    if total - st["dueMinor"] > cash:
        raise Err(20025, "多收超过现金合计")
    for r in recs:
        r["usedBy"] = no
    c = check(st["checkNo"])
    c["status"] = "CLOSED"; c["version"] += 1
    served = all(t["status"] in ("SERVED", "VOIDED") for t in DB["tasks"].values() if t["checkNo"] == c["checkNo"])
    released = served or not CFG["r4"]
    if released:
        for n in c["tableNos"]:
            bump(DB["tables"][n], turnoverState="IDLE")
    st.update(status="SETTLED", receivedMinor=total, changeMinor=total - st["dueMinor"], receiptNo=nid("RC"), checkStatus="CLOSED", tableReleased=released)
    emit("food.table", "CHECK", c["checkNo"], c["version"])
    return st


def w_settle_cancel(store, no, q, b):
    st = settlement(no)
    if st["status"] == "SETTLED":
        raise Err(20030, "已确认不能取消")
    st["status"] = "CANCELLED"
    return st


def r_settlement(store, no, q, b):
    return dict(settlement(no), receivedMinor=None, changeMinor=None)


def w_offline_record(store, q, b):
    strict(b, {"amountMinor", "currency", "method", "orderNo", "bizNo", "note", "receivedAt"})
    need(b, "amountMinor", "currency", "method")
    if not isinstance(b["amountMinor"], int) or isinstance(b["amountMinor"], bool) or b["amountMinor"] <= 0:
        raise Err(10400, "amountMinor 必须是正整数")
    if b["method"] not in ("CASH", "BANK_TRANSFER", "EXTERNAL_POS", "OTHER"):
        raise Err(10400, "method 不合法")
    rec = {"recordNo": nid("OR"), "amountMinor": b["amountMinor"], "currency": b["currency"], "method": b["method"], "orderNo": b.get("orderNo"),
           "bizNo": b.get("bizNo"), "note": b.get("note"), "receivedAt": b.get("receivedAt") or time.strftime("%Y-%m-%dT%H:%M:%S+08:00"),
           "recordedBy": "terminal", "status": "VALID", "corrected": False, "version": 1, "history": []}
    DB["records"][rec["recordNo"]] = rec
    return {k: v for k, v in rec.items() if k != "usedBy"}


# —— 厨房任务与放台（真实后端规则 R4：账单的任务没全部上菜 / 作废就不放台） ——
def r_stations(store, q, b):
    return {"records": [{"configNo": "STA1", "version": 1, "status": "PUBLISHED", "storeNo": store, "name": "热厨", "stationType": "HOT", "enabled": True}], "total": 1, "page": 1, "size": 20}


def r_tasks(store, q, b):
    if not q.get("stationNo"):
        raise Err(10400, "stationNo 必填")
    rows = [t for t in DB["tasks"].values() if t["stationNo"] == q["stationNo"] and t["status"] not in ("SERVED", "VOIDED")]
    return {"records": rows, "total": len(rows), "page": 1, "size": 100}


def r_batches(store, q, b):
    rows = [{k: v for k, v in bt.items() if k != "lines"} for bt in DB["batches"].values() if not q.get("checkNo") or bt["checkNo"] == q["checkNo"]]
    return {"records": rows, "total": len(rows), "page": 1, "size": 100}


def w_task(action):
    nxt = {"start": ("QUEUED", "MAKING"), "ready": ("MAKING", "READY"), "serve": ("READY", "SERVED")}[action]

    def f(store, no, q, b):
        strict(b, {"version", "qty", "reason"})
        t = DB["tasks"].get(no)
        if not t:
            raise Err(10404, "任务不存在")
        if int(b["version"]) != t["version"]:
            raise Err(10409, "任务版本已变化")
        if t["status"] != nxt[0]:
            raise Err(10409, f"任务状态 {t['status']} 不能 {action}")
        t.update(status=nxt[1], version=t["version"] + 1)
        return {"eventNo": nid("EV"), "taskNo": no, "action": action.upper(), "qty": t["qty"], "reason": b.get("reason"), "inventoryDocNo": None}
    return f


def w_release(store, q, b):
    strict(b, {"check", "version", "reason"})
    c = check(b["check"])
    if int(b["version"]) != c["version"]:
        raise Err(10409, "账单版本已变化")
    if any(t["status"] not in ("SERVED", "VOIDED") for t in DB["tasks"].values() if t["checkNo"] == c["checkNo"]):
        raise Err(10409, "有菜没上齐")
    for n in c["tableNos"]:
        if DB["tables"][n]["turnoverState"] != "IDLE":
            bump(DB["tables"][n], turnoverState="IDLE")
    c["version"] += 1
    return {"operationNo": nid("OP"), "status": "SUCCEEDED", "version": c["version"], "resultRef": c["checkNo"]}


# —— 进销存（/biz/inventory，零售）：没有幂等键；单号字段 no；过账 / 作废按单号幂等；数量只收整数 ——
INV = "/biz/inventory"


def r_item_by_barcode(store, q, b):
    code = q.get("code", "")
    if not code or code.startswith("NONE"):
        return None                                   # 没命中返回 data=null
    return {"itemId": "ITM-" + code, "skuNo": None, "name": code, "specText": "", "baseUom": "PIECE", "onHand": DB["onhand"].get("ITM-" + code, 0),
            "reserved": 0, "available": DB["onhand"].get("ITM-" + code, 0), "safetyStock": None, "lastMovedAt": None, "flags": []}


def inv_lines(lines, cost_required=False):
    if not lines:
        raise Err(10400, "lines 不能为空")
    for l in lines:
        if not isinstance(l.get("qty"), int) or isinstance(l.get("qty"), bool) or l["qty"] <= 0:
            raise Err(10400, "qty 必须是正整数")
        if cost_required and l.get("unitCostMinor") is None:
            raise Err(10400, "采购入库每行要 unitCostMinor")
    return lines


def new_doc(kind, prefix, body, lines):
    no = prefix + time.strftime("%Y%m%d%H%M%S") + uuid.uuid4().hex[:4]
    DB["invdocs"][no] = {"no": no, "kind": kind, "status": "DRAFT", "body": body, "lines": lines}
    return {"no": no}


def w_inbound(store, q, b):
    return new_doc("IN", "IN", b, inv_lines(b.get("lines"), b.get("sourceType") == "PURCHASE"))


def w_outbound(store, q, b):
    if b.get("purpose") == "SALE":
        raise Err(10403, "SALE 不能手工建")
    if b.get("purpose") == "SCRAP" and not b.get("reasonCode"):
        raise Err(10400, "报损要 reasonCode")
    return new_doc("OUT", "OUT", b, inv_lines(b.get("lines")))


def w_doc_post(store, no, q, b):
    d = DB["invdocs"].get(no)
    if not d:
        raise Err(10404, "单据不存在")
    if d["status"] == "POSTED":
        return None                                   # 幂等
    sign = -1 if d["kind"] == "OUT" else 1
    for l in d["lines"]:
        DB["onhand"][l["itemId"]] = DB["onhand"].get(l["itemId"], 0) + sign * l["qty"]
    d["status"] = "POSTED"
    return None


def w_count_open(store, q, b):
    if not b.get("itemIds"):
        raise Err(10400, "itemIds 不能为空")
    if any(d["kind"] == "COUNT" and d["status"] == "COUNTING" for d in DB["invdocs"].values()):
        raise Err(10409, "已有开着的盘点单")
    no = "CNT" + time.strftime("%Y%m%d%H%M%S") + uuid.uuid4().hex[:4]
    DB["invdocs"][no] = {"no": no, "kind": "COUNT", "status": "COUNTING", "lines": {i: None for i in b["itemIds"]}}
    return {"no": no}


def w_count_lines(store, no, q, b):
    d = DB["invdocs"].get(no)
    if not d or d["kind"] != "COUNT":
        raise Err(10404, "盘点单不存在")
    for l in b:
        if l["itemId"] not in d["lines"]:
            raise Err(10404, "物料不在单里")
        d["lines"][l["itemId"]] = l["countedQty"]
    return None


def w_count_post(store, no, q, b):
    d = DB["invdocs"].get(no)
    if d["status"] == "POSTED":
        return None
    if any(v is None for v in d["lines"].values()):
        raise Err(10409, "还有物料没录实盘")
    for i, v in d["lines"].items():
        DB["onhand"][i] = v
    d["status"] = "POSTED"
    return None


def w_adjust(store, q, b):
    need(b, "itemId")
    DB["onhand"][b["itemId"]] = int(b["countedQty"])
    return None


# —— 日结 ——
def w_open_day(store, no, q, b):
    d = DB.setdefault("days", {})
    return d.setdefault(no, {"dayNo": nid("DAY"), "businessDate": no, "status": "OPEN", "version": 0})   # 幂等：已开过原样返回


def w_close_preview(store, q, b):
    strict(b, {"businessDate", "currency"})
    need(b, "businessDate", "currency")
    if b["businessDate"] not in DB.get("days", {}):
        raise Err(10404, "营业日未开")                  # 真实后端：没开过营业日 preview 返回 10404
    no = "CL" + b["businessDate"].replace("-", "")
    return DB["closes"].setdefault(no, {"closeNo": no, "dayNo": "D" + b["businessDate"], "currency": b["currency"], "revision": 1, "status": "REVIEW",
                                         "snapshotHash": "sh" + uuid.uuid4().hex[:12], "version": 1})


def w_close_sign(store, q, b):
    strict(b, {"closeNo", "snapshotHash"})
    need(b, "closeNo", "snapshotHash")
    cl = DB["closes"].get(b["closeNo"])
    if not cl:
        raise Err(10404, "日结单不存在")
    if cl["snapshotHash"] != b["snapshotHash"]:
        raise Err(10409, "快照已变化，请重新预览")
    if cl["status"] == "SIGNED":
        raise Err(10400, "该营业日已签核")
    bump(cl, status="SIGNED", signedAt=time.strftime("%Y-%m-%dT%H:%M:%S"))
    return cl


# —— 推送 ——
def w_terminal(store, q, b):
    strict(b, {"installId", "clientType", "platform", "appVersion"})
    need(b, "installId", "clientType", "platform")
    t = DB["terminals"].setdefault(b["installId"] + "@" + store, {"terminalNo": nid("TN"), "storeNo": store})
    return {"terminalNo": t["terminalNo"]}


def w_acks(store, q, b):
    strict(b, {"terminalNo", "acks"})
    DB.setdefault("acks", []).extend(b["acks"])
    return {"applied": len(b["acks"])}


# 路由：(方法, 路径模式, 处理函数, 是否必须带幂等键)；{s}=门店，{x}/{y}=对象号
ROUTES = [
    ("GET", FOOD + "/stores/{s}/tables", r_tables, False),
    ("POST", FOOD + "/stores/{s}/checks", w_open_check, True),
    ("GET", FOOD + "/checks/{x}", r_check, False),
    ("POST", FOOD + "/checks/{x}:transfer", w_transfer, True),
    ("GET", FOOD + "/stores/{s}/catalog", r_catalog, False),
    ("POST", FOOD + "/stores/{s}/carts", w_create_cart, True),
    ("GET", FOOD + "/carts/{x}", r_cart, False),
    ("PUT", FOOD + "/carts/{x}/lines/{y}", w_replace_line, True),
    ("POST", FOOD + "/stores/{s}/quotes", w_quote, True),
    ("POST", FOOD + "/quotes/{x}:confirm", w_confirm, False),
    ("POST", FOOD + "/stores/{s}/batches", w_batch, True),
    ("POST", FOOD + "/checks/{x}/counter-settlements", w_counter_settle, True),
    ("POST", FOOD + "/counter-settlements/{x}:finalize", w_finalize, False),
    ("POST", FOOD + "/counter-settlements/{x}:cancel", w_settle_cancel, False),
    ("GET", FOOD + "/counter-settlements/{x}", r_settlement, False),
    ("POST", "/biz/payment/offline-records", w_offline_record, True),
    ("GET", FOOD + "/stores/{s}/stations", r_stations, False),
    ("GET", FOOD + "/stores/{s}/kitchen/tasks", r_tasks, False),
    ("GET", FOOD + "/stores/{s}/batches", r_batches, False),
    ("POST", FOOD + "/tasks/{x}:start", w_task("start"), True),
    ("POST", FOOD + "/tasks/{x}:ready", w_task("ready"), True),
    ("POST", FOOD + "/tasks/{x}:serve", w_task("serve"), True),
    ("POST", FOOD + "/business-operations/checkCollaboration/releaseService", w_release, True),
    ("GET", INV + "/items/by-barcode", r_item_by_barcode, False),
    ("POST", INV + "/inbounds", w_inbound, False),
    ("POST", INV + "/inbounds/{x}/post", w_doc_post, False),
    ("POST", INV + "/outbounds", w_outbound, False),
    ("POST", INV + "/outbounds/{x}/post", w_doc_post, False),
    ("POST", INV + "/counts", w_count_open, False),
    ("PUT", INV + "/counts/{x}/lines", w_count_lines, False),
    ("POST", INV + "/counts/{x}/post", w_count_post, False),
    ("POST", INV + "/adjust", w_adjust, False),
    ("POST", FOOD + "/business-days/{x}:open", w_open_day, True),
    ("POST", FOOD + "/closes:preview", w_close_preview, True),
    ("POST", FOOD + "/closes:sign", w_close_sign, True),
    ("POST", "/biz/push/terminals", w_terminal, False),
    ("POST", "/biz/push/acks", w_acks, False),
]


def match(pattern, path):
    pp, xp = pattern.split("/"), path.split("/")
    if len(pp) != len(xp):
        return None
    got = {}
    for a, b in zip(pp, xp):
        if a.startswith("{"):
            head, _, act = a.partition(":")      # 段内可带 :动作，例如 {x}:transfer
            val, _, bact = b.partition(":")
            if act != bact or not val:
                return None
            got.setdefault(head, []).append(val)
        elif a != b:
            return None
    return got


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("[mock]", self.command, self.path, "→", *args[1:2], flush=True)

    def send(self, status, obj):
        data = json.dumps(obj, ensure_ascii=False).encode() if obj is not None else b""
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def env(self, data=None, code=0, msg="success"):
        self.send(200, {"code": code, "msg": msg, "data": data})

    def debug(self, path, body):
        if path == "/_mock/state":
            return self.send(200, {k: v for k, v in DB.items() if k != "idem"})
        if path == "/_mock/reset":
            DB.clear(); DB.update(fresh()); return self.send(200, {"ok": True})
        if path == "/_mock/fail":
            DB["fail"] = {"times": int(body.get("times", 1)), "code": int(body.get("code", 0)), "http": int(body.get("http", 0))}
            return self.send(200, DB["fail"])
        if path == "/_mock/occupy":
            t = next((t for t in DB["tables"].values() if t["name"] == body.get("name")), None)
            if t:
                bump(t, turnoverState=body.get("state", "IN_USE"))
            return self.send(200, t or {})
        if path == "/_mock/price":   # 服务端调价（制造价差）：factor=1.1 即贵 10%
            DB["priceFactor"] = float(body.get("factor", 1.0)); return self.send(200, {"priceFactor": DB["priceFactor"]})
        if path == "/_mock/notify":
            emit(body.get("topic", "trade.order"), body.get("refType", "ORDER"), body.get("refNo", nid("O")), int(body.get("refVersion", 1)))
            return self.send(200, {"seq": DB["seq"]})
        if path == "/_mock/direct":
            m = {"messageNo": nid("MSG"), "kind": body.get("kind", "app.upgrade"), "payload": body.get("payload"), "expireAt": "2099-01-01T00:00:00Z"}
            for (_s, _t, q) in list(SUBSCRIBERS):
                q.put(("message", m))
            return self.send(200, m)
        self.send(404, {})

    def stream(self, store, q):
        hold = float(q.get("hold", 1800))
        terminal = q.get("terminalNo")
        with LOCK:
            known = any(t["terminalNo"] == terminal and t["storeNo"] == store for t in DB["terminals"].values())
        if terminal and not known:
            return self.send(400, {"code": 10400, "msg": "端号与门店不一致"})
        last = int(self.headers.get("Last-Event-ID") or 0)
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        qu = queue.Queue()
        with LOCK:
            backlog = [f for f in DB["events"] if f["seq"] > last]
            SUBSCRIBERS.append((store, terminal, qu))
        try:
            for f in backlog:
                self.wfile.write(f"id: {f['seq']}\nevent: push\ndata: {json.dumps(f, ensure_ascii=False)}\n\n".encode())
            self.wfile.flush()
            end = time.time() + hold
            while time.time() < end:
                try:
                    kind, f = qu.get(timeout=min(5, max(0.1, end - time.time())))
                    if kind == "push":
                        self.wfile.write(f"id: {f['seq']}\nevent: push\ndata: {json.dumps(f, ensure_ascii=False)}\n\n".encode())
                    else:
                        self.wfile.write(f"event: message\ndata: {json.dumps(f, ensure_ascii=False)}\n\n".encode())
                except queue.Empty:
                    self.wfile.write(b": heartbeat\n\n")
                self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass
        finally:
            with LOCK:
                SUBSCRIBERS[:] = [s for s in SUBSCRIBERS if s[2] is not qu]

    def handle_any(self, method):
        u = urlparse(self.path)
        n = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(n) if n else b""
        try:
            body = json.loads(raw or b"{}")
        except ValueError:
            return self.env(None, 10400, "请求体不是 JSON")
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        header_store = self.headers.get("X-Store-No")
        if u.path.startswith("/_mock/"):
            with LOCK:
                return self.debug(u.path, body)
        if method == "GET" and u.path.startswith("/biz/stores/") and u.path.endswith("/push/stream"):
            if CFG["token"] and self.headers.get("Authorization") != f"Bearer {CFG['token']}":
                return self.send(401, None)
            return self.stream(u.path.split("/")[3], q)
        with LOCK:
            key = self.headers.get("Idempotency-Key")
            DB["calls"].append({"at": time.strftime("%H:%M:%S"), "method": method, "path": u.path, "key": key, "operator": self.headers.get("X-Operator")})
            f = DB["fail"]
            if f["times"] > 0:
                f["times"] -= 1
                if f["http"]:
                    return self.send(f["http"], {"error": "mock"})
                return self.env(None, f["code"] or 50000, "模拟失败")
            if CFG["token"] and self.headers.get("Authorization") != f"Bearer {CFG['token']}":
                return self.send(401, None)
            for (m, pat, fn, idem) in ROUTES:
                got = match(pat, u.path) if m == method else None
                if got is None:
                    continue
                try:
                    store = (got.get("{s}") or [header_store])[0]
                    if got.get("{s}") and header_store and header_store != store:
                        raise Err(10403, "X-Store-No 与路径门店不一致")
                    if store != CFG["store"]:
                        raise Err(10404, f"门店不存在：{store}")
                    args = [store] + got.get("{x}", []) + got.get("{y}", []) + [q, body]
                    if method == "GET" or not idem:
                        return self.env(fn(*args))
                    if not key:
                        raise Err(10400, "缺少 Idempotency-Key")
                    sig = json.dumps(body, sort_keys=True)
                    hit = DB["idem"].get((key, u.path))
                    if hit:
                        if hit["sig"] != sig:
                            raise Err(10409, "幂等键已用于不同请求")
                        return self.send(200, hit["resp"])
                    try:
                        resp = {"code": 0, "msg": "success", "data": fn(*args)}
                    except Err as e:
                        resp = {"code": e.code, "msg": e.msg, "data": None}
                    if resp["code"] in (0, 10400, 10404):   # 业务结果固定；冲突不记，允许重读后同键重试
                        DB["idem"][(key, u.path)] = {"sig": sig, "resp": resp}
                    return self.send(200, resp)
                except Err as e:
                    return self.env(None, e.code, e.msg)
            return self.env(None, 10404, f"未实现：{method} {u.path}")

    def do_GET(self):
        self.handle_any("GET")

    def do_POST(self):
        self.handle_any("POST")

    def do_PUT(self):
        self.handle_any("PUT")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8790)
    ap.add_argument("--token", default="")
    ap.add_argument("--store", default="NS002")
    ap.add_argument("--tables", default="H01,H02,H03,H04,T01,T02")
    ap.add_argument("--service-bp", type=int, default=500, help="服务费万分比（应收 = 批次合计 × (1 + bp/10000)）")
    ap.add_argument("--no-r4", action="store_true", help="关闭 R4：结账时直接放台（默认和真实后端一致：菜没上齐不放台）")
    ap.add_argument("--catalog", default=__file__.rsplit("/", 1)[0] + "/demo-catalog.json")
    a = ap.parse_args()
    CFG.update(r4=not a.no_r4, service_bp=a.service_bp, token=a.token, store=a.store, tables=a.tables.split(","), catalog=json.load(open(a.catalog, encoding="utf-8")))
    DB.update(fresh())
    print(f"餐饮服务端模拟 http://0.0.0.0:{a.port}  门店 {a.store}  菜品 {len(CFG['catalog']['items'])}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", a.port), H).serve_forever()
