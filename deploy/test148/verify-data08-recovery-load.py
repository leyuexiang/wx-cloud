"""在已授权的 DATA-08 隔离服务验证停写回填恢复，并采集有界负载数据。"""
from __future__ import annotations

from argparse import Namespace
import argparse
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import statistics
import subprocess
import time
import urllib.request
import uuid


DATABASE = "wx_editor_data08"
CONTAINER = "wx-editor-dimensions-data08"
CONFIG = Path("/opt/wx/editor-test148/config/dimensions-data08.yaml")
BASE = "http://127.0.0.1:12525/admin-api"
KEYS = ("sceneJson", "threeDJson", "twoDJson", "dependenciesJson")


def load_module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


API = load_module("data08_api", "verify-data08-isolation-service.py")
VERIFY = load_module("data08_db", "verify-data08-db.py")
MIGRATE = load_module("data08_migrate", "migrate-data08-db.py")
ROUNDTRIP = load_module("data08_roundtrip", "verify-data08-roundtrip.py")
DB_ARGS = Namespace(host="127.0.0.1", port=3306, user="root", database=DATABASE,
                    password_env=None, mysql_container="mysql8", apply=True)


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def health():
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen("http://127.0.0.1:48093/actuator/health", timeout=3) as response:
                if json.load(response).get("status") == "UP":
                    return
        except (OSError, ValueError):
            pass
        time.sleep(2)
    raise RuntimeError("隔离三维服务未在 90 秒内恢复健康")


def command(*args):
    subprocess.run(list(args), check=True, capture_output=True, timeout=40)


def counters():
    rows = VERIFY.mysql(DB_ARGS, "SHOW GLOBAL STATUS WHERE Variable_name IN "
                       "('Innodb_row_lock_waits','Innodb_row_lock_time','Innodb_deadlocks')")
    return {name: int(value) for name, value in rows}


def measure(token, scene_id, packages):
    samples = []
    for _ in range(7):
        start = time.perf_counter()
        info = API.call(BASE, "/dim3d/scene/info/" + scene_id, token, 2)
        elapsed = (time.perf_counter() - start) * 1000
        require(info.get("code") == 200, "场景读取失败")
        revision = info["data"]["currentRevision"]
        require(all(revision[key] == packages[key] for key in KEYS), "场景回读四包字节不一致")
        samples.append(round(elapsed, 3))
    # 固定七次串行样本只提供初步对照，保留全部样本供复核。
    return {"samplesMs": samples, "medianMs": round(statistics.median(samples), 3),
            "maxMs": max(samples)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--confirm", default="")
    args = parser.parse_args()
    require(args.execute and args.confirm == "DATA08-ISOLATION-RECOVERY", "需要显式确认隔离恢复演练")
    original = CONFIG.read_bytes()
    enabled = b"    dual-write-enabled: true"
    require(b"jdbc:mysql://127.0.0.1:3306/wx_editor_data08?" in original and original.count(enabled) == 1,
            "隔离数据库或开关门禁未通过")
    require(VERIFY.mysql(DB_ARGS, "SELECT COUNT(*) FROM wx_3d_scene WHERE deleted=0 AND lock_expire>CURRENT_TIMESTAMP")[0][0] == "0",
            "隔离库存在活动编辑租约")
    require(ROUNDTRIP.check(DB_ARGS)["passed"], "演练前全量回读不一致")
    run_id = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    folder = Path("/opt/wx/editor-test148/backups") / ("data08-recovery-" + run_id)
    folder.mkdir(mode=0o700)
    # 备份仅覆盖本轮会增量写入的隔离场景表，凭据保持在容器环境中。
    backup = subprocess.run(["docker", "exec", "mysql8", "sh", "-c",
                             'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -u root --single-transaction --no-tablespaces wx_editor_data08 wx_3d_scene wx_3d_scene_revision wx_3d_scene_content'],
                            check=True, capture_output=True, timeout=60).stdout
    backup_path = folder / "scene-tables.sql.gz"
    backup_path.write_bytes(gzip.compress(backup))
    backup_path.chmod(0o600)
    identities = json.loads(Path("/opt/wx/editor-test148/test-identities.json").read_text())
    identity = next(item for item in identities if item["username"] == "tenantadmin" and item["tenantId"] == 2)
    login = API.call(BASE, "/system/auth/login", None, 2, "POST",
                     {"username": identity["username"], "password": identity["password"]})
    require(login.get("code") == 200, "隔离环境登录失败")
    token = login["data"]["accessToken"]
    fixtures = []
    report = {"passed": False, "database": DATABASE, "backup": str(backup_path),
              "backupSha256": hashlib.sha256(backup_path.read_bytes()).hexdigest(), "fixtures": fixtures,
              "scope": "串行接口负载初测，未覆盖并发容量", "lockCountersBefore": counters()}
    try:
        CONFIG.write_bytes(original.replace(enabled, b"    dual-write-enabled: false"))
        command("docker", "restart", CONTAINER)
        health()
        for label, count, padding in (("wide128", 128, 0), ("wide1024-large", 1024, 1024 * 1024)):
            name = "DATA08-恢复负载-" + label + "-" + run_id
            created = API.call(BASE, "/dim3d/scene/add", token, 2, "POST", {"name": name, "sceneType": "local3d"})
            require(created.get("code") == 200, "负载场景创建失败")
            scene_id = created["data"]["sceneId"]
            client = "data08-recovery-" + uuid.uuid4().hex
            fixture = {"sceneId": scene_id, "label": label, "nodes": count + 1, "client": client}
            fixtures.append(fixture)
            info = API.call(BASE, "/dim3d/scene/info/" + scene_id, token, 2)
            packages = {key: info["data"]["currentRevision"][key] for key in KEYS}
            tree = json.loads(packages["threeDJson"])
            tree["components"] = [{"instanceId": "root", "objectType": "group", "sortNo": 0}]
            tree["components"].extend({"instanceId": "n" + str(index), "parentInstanceId": "root", "sortNo": index,
                                       "objectType": "primitive", "name": "节点" + str(index),
                                       "transform": {"kind": "local", "position": [index, 0, 0], "rotation": [0, 0, 0], "scale": [1, 1, 1]}}
                                      for index in range(count))
            tree["metadata"] = {"padding": "x" * padding}
            packages["threeDJson"] = json.dumps(tree, ensure_ascii=False, separators=(",", ":"))
            fixture["packageBytes"] = sum(len(value.encode()) for value in packages.values())
            fixture["packages"] = packages
            lock = API.call(BASE, "/dim3d/scene/lock/" + scene_id + "?clientKey=" + client, token, 2, "POST")
            require(lock.get("code") == 200, "隔离场景加锁失败")
            fixture["lockToken"] = lock["data"]["lockToken"]
            fixture["savesDisabledMs"] = []
            for baseline in (1, 2):
                body = {**packages, "sceneId": scene_id, "schemaVersion": 1, "baseRevisionNo": baseline,
                        "requestId": uuid.uuid4().hex, "lockToken": fixture["lockToken"]}
                start = time.perf_counter()
                saved = API.call(BASE, "/dim3d/scene/saveAll", token, 2, "PUT", body)
                fixture["savesDisabledMs"].append(round((time.perf_counter() - start) * 1000, 3))
                require(saved.get("code") == 200 and saved.get("data", {}).get("revisionNo") == baseline + 1,
                        "关闭双写期间保存失败，业务码=" + str(saved.get("code")))
            fixture["readDisabled"] = measure(token, scene_id, packages)
        require(VERIFY.mysql(DB_ARGS, "SELECT COUNT(*) FROM wx_3d_scene_revision WHERE content_id IS NULL")[0][0] == "6",
                "关闭双写产生的新修订数量异常")
        pointers_before = VERIFY.mysql(DB_ARGS, "SELECT id,current_revision FROM wx_3d_scene ORDER BY id")
        command("docker", "stop", CONTAINER)
        start = time.perf_counter()
        migration, sql = MIGRATE.plan(DB_ARGS)
        VERIFY.mysql(DB_ARGS, sql)
        migration["databaseWrite"] = True
        report["backfillMs"] = round((time.perf_counter() - start) * 1000, 3)
        report["backfill"] = migration
        require(ROUNDTRIP.check(DB_ARGS)["passed"], "停写回填后全量校验失败")
        repeat, _ = MIGRATE.plan(DB_ARGS)
        report["repeatedBackfill"] = repeat
        require(repeat["newContentRows"] == 0 and repeat["revisionReferencesToWrite"] == 0, "回填重复执行不幂等")
        require(VERIFY.mysql(DB_ARGS, "SELECT id,current_revision FROM wx_3d_scene ORDER BY id") == pointers_before,
                "回填改变了场景修订指针")
        CONFIG.write_bytes(original)
        command("docker", "restart", CONTAINER)
        health()
        for fixture in fixtures:
            scene_id, packages = fixture["sceneId"], fixture["packages"]
            fixture["readEnabled"] = measure(token, scene_id, packages)
            body = {**packages, "sceneId": scene_id, "schemaVersion": 1, "baseRevisionNo": 3,
                    "requestId": uuid.uuid4().hex, "lockToken": fixture["lockToken"]}
            start = time.perf_counter()
            saved = API.call(BASE, "/dim3d/scene/saveAll", token, 2, "PUT", body)
            fixture["saveEnabledMs"] = round((time.perf_counter() - start) * 1000, 3)
            require(saved.get("code") == 200 and saved.get("data", {}).get("revisionNo") == 4, "恢复双写后保存失败")
            references = VERIFY.mysql(DB_ARGS, f"SELECT revision_no,content_id FROM wx_3d_scene_revision WHERE scene_id={int(scene_id)} AND tenant_id=2 ORDER BY revision_no")
            require(len(references) == 4 and len({row[1] for row in references[1:]}) == 1, "恢复后相同正文未复用")
            fixture["revisionReferences"] = references
        report["roundtrip"] = ROUNDTRIP.check(DB_ARGS)
        require(report["roundtrip"]["passed"], "恢复双写后全量回读失败")
        report["lockCountersAfter"] = counters()
        report["tableStorage"] = VERIFY.mysql(DB_ARGS, "SELECT TABLE_NAME,TABLE_ROWS,DATA_LENGTH,INDEX_LENGTH FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('wx_3d_scene_revision','wx_3d_scene_content')")
        report["passed"] = True
    finally:
        # 异常也恢复本轮专用服务配置，样本只软删除并保留修订用于审计。
        CONFIG.write_bytes(original)
        command("docker", "restart", CONTAINER)
        health()
        for fixture in fixtures:
            scene_id = fixture["sceneId"]
            API.call(BASE, "/dim3d/scene/unlock/" + scene_id + "?clientKey=" + fixture["client"], token, 2, "DELETE")
            result = API.call(BASE, "/dim3d/scene/remove/" + scene_id, token, 2, "DELETE")
            fixture["softDeleted"] = result.get("code") == 200
            for key in ("packages", "lockToken", "client"):
                fixture.pop(key, None)
        API.call(BASE, "/system/auth/logout", token, 2, "POST")
        report["configRestored"] = CONFIG.read_bytes() == original
        (folder / "result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
