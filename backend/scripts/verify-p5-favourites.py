"""P5 收藏/常约 HTTP 验收：幂等、账号隔离、状态展示和只读列表去重。"""
import json
import os
import urllib.error
import urllib.request
import uuid
import subprocess
from concurrent.futures import ThreadPoolExecutor

BASE = os.environ.get("P5_API_BASE", "http://127.0.0.1:8091/api")


def call(path, token=None, method="GET"):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Mini-Token"] = token
    request = urllib.request.Request(BASE + path, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return json.load(response)


def login(code):
    request = urllib.request.Request(
        BASE + "/auth/mini/mock-login", data=json.dumps({"code": code}).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(request, timeout=15) as response:
        result = json.load(response)
    assert result["code"] == 0, result
    return result["data"]["token"]


first = login("p5-favourite-one")
second = login("p5-favourite-two")

before = call("/mini/favourite-venues", first)
assert before["code"] == 0 and len({row["id"] for row in before["data"]}) == len(before["data"])

for _ in range(2):
    added = call("/mini/favourite-venues/1", first, "POST")
    assert added["code"] == 0, added
own = call("/mini/favourite-venues", first)
other = call("/mini/favourite-venues", second)
assert any(row["id"] == 1 for row in own["data"]), own
assert all(row["id"] != 1 for row in other["data"]), other

removed = call("/mini/favourite-venues/1", first, "DELETE")
removed_again = call("/mini/favourite-venues/1", first, "DELETE")
assert removed["code"] == removed_again["code"] == 0
assert all(row["id"] != 1 for row in call("/mini/favourite-venues", first)["data"])

recent = call("/mini/recent-venues", first)
assert recent["code"] == 0
recent_ids = [row["id"] for row in recent["data"]]
assert len(recent_ids) == len(set(recent_ids)), recent
assert all("status" in row for row in recent["data"])

container = os.environ.get("P5_MYSQL_CONTAINER")
if container:
    for _ in range(3):
        code = "p5-favourite-race-" + uuid.uuid4().hex
        login_request = urllib.request.Request(BASE + "/auth/mini/mock-login",
            data=json.dumps({"code": code}).encode(), headers={"Content-Type": "application/json"}, method="POST")
        with urllib.request.urlopen(login_request, timeout=15) as response:
            account = json.load(response)["data"]
        token = account["token"]
        user_id = int(account["profile"]["id"])
        with ThreadPoolExecutor(max_workers=2) as pool:
            add_result = pool.submit(call, "/mini/favourite-venues/1", token, "POST")
            close_result = pool.submit(call, "/auth/mini/close-account", token, "POST")
            add_result.result()
            assert close_result.result()["code"] == 0
        remaining = subprocess.run([
            "docker", "exec", container, "mysql", "-uroot", "-preview_root", "-Ns", "sports_booking",
            "-e", f"SELECT COUNT(*) FROM s_user_venue_favourite WHERE user_id={user_id}"],
            check=True, text=True, capture_output=True).stdout.strip()
        assert remaining == "0", remaining
print("P5 favourites: idempotent add/remove, account isolation, deduplicated recent response passed")
