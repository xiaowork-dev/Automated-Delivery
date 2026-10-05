"""Run real HTTP acceptance against a dedicated test deployment.

Requires ADMIN_USERNAME / ADMIN_PASSWORD and a running application.
Creates uniquely named test records; never clears or drops existing data.
Python standard library only. Output contains no passwords or full codes.
"""
import concurrent.futures
import json
import os
import secrets
import statistics
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

BASE = os.environ.get("TEST_BASE_URL", "http://127.0.0.1:8080").rstrip("/") + "/api/v1"
RUN = secrets.token_hex(5)
checks = []
timings = []


def call(method, path, payload=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            status, result = r.status, json.load(r)
    except urllib.error.HTTPError as e:
        status, result = e.code, json.load(e)
    timings.append((time.perf_counter() - start) * 1000)
    return status, result


def ok(name, condition):
    if not condition:
        raise AssertionError(name)
    checks.append(name)
    print("PASS " + name, flush=True)


def success(method, path, payload=None, token=None):
    status, result = call(method, path, payload, token)
    if status not in (200, 201) or result.get("code") != 0:
        raise AssertionError(f"{method} {path}: HTTP {status}, code={result.get('code')}")
    return result["data"]


def error(name, method, path, http, code=None, payload=None, token=None):
    status, result = call(method, path, payload, token)
    ok(name, status == http and (code is None or result.get("code") == code))


def product(admin, suffix, count, price=19.90, status="ON_SALE"):
    p = success("POST", "/admin/products", {
        "name": f"验收-{RUN}-{suffix}", "subtitle": "自动化验收测试商品",
        "price": price, "description": "仅模拟支付", "status": status, "coverUrl": ""
    }, admin)
    codes = [f"ACCEPT-{RUN}-{suffix}-{secrets.token_hex(8)}" for _ in range(count)]
    if codes:
        imported = success("POST", "/admin/redeem-codes/import", {
            "productId": p["id"], "codes": "\n".join(codes)
        }, admin)
        ok(suffix + " import", imported["successCount"] == count)
    return p, codes


def main():
    admin = success("POST", "/auth/login", {
        "username": os.environ.get("ADMIN_USERNAME", "admin"),
        "password": os.environ["ADMIN_PASSWORD"]
    })["token"]
    pw = secrets.token_urlsafe(18)
    username = "accept_" + RUN
    a = success("POST", "/auth/register", {"username": username, "password": pw})
    b = success("POST", "/auth/register", {"username": username + "b", "password": pw})
    token, other = a["token"], b["token"]
    ok("registration role", a["user"]["role"] == "USER")
    ok("login and me", success("GET", "/auth/me", token=token)["username"] == username)
    error("anonymous rejected", "GET", "/orders", 401)
    error("user cannot access admin", "GET", "/admin/dashboard", 403, token=token)
    error("tampered token rejected", "GET", "/auth/me", 401, token=token[:-8] + "tampered")
    error("wrong password rejected", "POST", "/auth/login", 401,
          payload={"username": username, "password": "Incorrect_12345"})
    error("duplicate username", "POST", "/auth/register", 409,
          payload={"username": username, "password": pw})
    p, codes = product(admin, "flow", 12)
    listed = success("GET", "/products?keyword=" + RUN)
    ok("public pagination", listed["total"] >= 1 and bool(listed["records"]))
    ok("stock truth", success("GET", f"/products/{p['id']}")["stock"] == 12)
    imported = success("POST", "/admin/redeem-codes/import", {
        "productId": p["id"], "codes": "  " + codes[0] + "  \n\n" + codes[0] + "\n" + "x" * 129
    }, admin)
    ok("trim duplicate invalid import", imported["successCount"] == 0 and
       imported["duplicateCount"] == 2 and imported["invalidCount"] == 1)
    order = success("POST", "/orders", {"productId": p["id"]}, token)
    no = order["orderNo"]
    ok("new order state and no secret", order["status"] == "WAIT_PAY" and not order.get("redeemCode"))
    success("PUT", f"/admin/products/{p['id']}", {**p, "name": p["name"] + "改名", "price": 29.90}, admin)
    frozen = success("GET", "/orders/" + no, token=token)
    ok("immutable price/name snapshot", float(frozen["amount"]) == 19.90 and frozen["productName"] == p["name"])
    error("order ownership", "GET", "/orders/" + no, 403, token=other)
    error("payment ownership", "POST", f"/orders/{no}/mock-pay", 403, token=other)
    paid = success("POST", f"/orders/{no}/mock-pay", token=token)
    ok("automatic delivery", paid["status"] == "DELIVERED" and bool(paid["redeemCode"]["code"]))
    code = paid["redeemCode"]["code"]
    for _ in range(10):
        repeat = success("POST", f"/orders/{no}/mock-pay", token=token)
        assert repeat["redeemCode"]["code"] == code
    ok("ten duplicate payments one code", success("GET", f"/products/{p['id']}")["stock"] == 11)
    error("redeem ownership", "POST", "/redeem", 403, payload={"code": code}, token=other)
    redeemed = success("POST", "/redeem", {"code": code}, token)
    ok("redeem completes order", success("GET", "/orders/" + no, token=token)["status"] == "COMPLETED")
    error("redeem exactly once", "POST", "/redeem", 409, "CODE_ALREADY_USED", {"code": code}, token)
    ok("redeem audit record", success("GET", "/redeem/records", token=token)["total"] >= 1)
    error("used code cannot disable", "POST", f"/admin/redeem-codes/{paid['redeemCode']['id']}/disable",
          409, payload={"reason": "acceptance"}, token=admin)
    cancel = success("POST", "/orders", {"productId": p["id"]}, token)
    cancelled = success("POST", f"/orders/{cancel['orderNo']}/cancel", token=token)
    ok("buyer cancellation", cancelled["status"] == "CANCELLED")
    error("cancelled cannot pay", "POST", f"/orders/{cancel['orderNo']}/mock-pay", 409, token=token)
    closed = success("POST", "/orders", {"productId": p["id"]}, token)
    success("POST", f"/admin/orders/{closed['orderNo']}/close", token=admin)
    ok("admin close", success("GET", "/orders/" + closed["orderNo"], token=token)["status"] == "CANCELLED")
    off, _ = product(admin, "off", 1, status="OFF_SHELF")
    error("off shelf order blocked", "POST", "/orders", 409, "PRODUCT_OFF_SHELF", {"productId": off["id"]}, token)
    empty, _ = product(admin, "empty", 0)
    error("out of stock order blocked", "POST", "/orders", 409, "OUT_OF_STOCK", {"productId": empty["id"]}, token)
    single, _ = product(admin, "race", 1)
    orders = [success("POST", "/orders", {"productId": single["id"]}, token)["orderNo"] for _ in range(5)]
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        results = list(pool.map(lambda n: call("POST", f"/orders/{n}/mock-pay", token=token), orders))
    print("Concurrency responses: " + json.dumps([{ "http": s, "code": r.get("code") } for s, r in results]))
    ok("MySQL single stock five concurrent payments", sum(s == 200 for s, _ in results) == 1 and
       all(s == 200 or (s == 409 and r["code"] == "OUT_OF_STOCK") for s, r in results))
    states = [success("GET", "/orders/" + n, token=token) for n in orders]
    ok("no partial payment state", sum(o["status"] == "DELIVERED" for o in states) == 1 and
       sum(o["status"] == "WAIT_PAY" for o in states) == 4)
    same, _ = product(admin, "same", 8)
    same_order = success("POST", "/orders", {"productId": same["id"]}, token)["orderNo"]
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        repeats = list(pool.map(lambda _: call("POST", f"/orders/{same_order}/mock-pay", token=token), range(8)))
    ok("concurrent same order idempotent", all(s == 200 for s, _ in repeats) and
       len({r["data"]["redeemCode"]["code"] for _, r in repeats}) == 1 and
       success("GET", f"/products/{same['id']}")["stock"] == 7)
    masks = success("GET", f"/admin/redeem-codes?productId={same['id']}", token=admin)["records"]
    ok("admin code masks", all("*" in r["code"] for r in masks))
    unused = next(r for r in masks if r["status"] == "UNUSED")
    success("POST", f"/admin/redeem-codes/{unused['id']}/disable", {"reason": "验收作废"}, admin)
    ok("disabled stock excluded", success("GET", f"/products/{same['id']}")["stock"] == 6)
    dashboard = success("GET", "/admin/dashboard", token=admin)
    ok("dashboard counts", dashboard["productCount"] > 0 and dashboard["availableCodes"] >= 0)
    ok("operations audited", success("GET", "/admin/logs", token=admin)["total"] >= 2)
    ok("admin user query", success("GET", "/admin/users?keyword=" + username, token=admin)["total"] == 2)
    success("PUT", f"/admin/users/{b['user']['id']}/status", {"status": 0}, admin)
    denied, _ = call("GET", "/auth/me", token=other)
    ok("disabled account old token rejected", denied in (401, 403))
    success("PUT", f"/admin/users/{b['user']['id']}/status", {"status": 1}, admin)
    fresh = secrets.token_urlsafe(18)
    success("PUT", "/auth/password", {"currentPassword": pw, "newPassword": fresh}, token)
    error("changed password revokes old token", "GET", "/auth/me", 401, token=token)
    ok("new password login", bool(success("POST", "/auth/login", {"username": username, "password": fresh})["token"]))
    with concurrent.futures.ThreadPoolExecutor(max_workers=50) as pool:
        load = list(pool.map(lambda _: call("GET", "/products?size=10"), range(50)))
    ok("50 concurrent catalogue requests", all(s == 200 for s, _ in load))
    result = {"passed": len(checks), "checks": checks, "run": RUN,
              "database": "real deployed database", "requests": len(timings),
              "medianMs": round(statistics.median(timings), 2),
              "p95Ms": round(sorted(timings)[int(len(timings) * .95) - 1], 2)}
    out = Path(os.environ.get("TEST_REPORT", "artifacts/api-acceptance.json"))
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "checks"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
