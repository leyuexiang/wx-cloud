import json
import os
import subprocess
import sys
import urllib.request
import uuid
from datetime import datetime, timezone
from urllib.parse import urlencode

import websocket


base = os.environ.get("IOT_TEST_BASE", "http://192.168.10.148:12522")
login_base = os.environ.get("IOT_TEST_LOGIN_BASE", base)
identity = json.load(sys.stdin) if "--identity-stdin" in sys.argv else {
    "username": os.environ["IOT_TEST_USERNAME"],
    "password": os.environ["IOT_TEST_PASSWORD"],
}
request = urllib.request.Request(
    login_base + "/admin-api/system/auth/login",
    data=json.dumps({"username": identity["username"], "password": identity["password"]}).encode(),
    headers={"tenant-id": "2", "Content-Type": "application/json"},
    method="POST",
)
with urllib.request.urlopen(request, timeout=20) as response:
    login = json.load(response)
print("login", login.get("code"))
if login.get("code") != 200:
    raise SystemExit("租户登录失败")
token = login["data"]["accessToken"]
publish = "--test148-kafka" in sys.argv
event_id = str(uuid.uuid4())
scene_id = os.environ["IOT_TEST_SCENE_ID"]
object_id = os.environ["IOT_TEST_OBJECT_ID"]
device_id = os.environ["IOT_TEST_DEVICE_ID"]
point_code = os.environ["IOT_TEST_POINT_CODE"]
def connect(tenant="2"):
    return websocket.create_connection(
        os.environ.get("IOT_TEST_WS", "ws://192.168.10.148:12522/dimensions/ws")
        + "?" + urlencode({"token": token, "tenant-id": tenant}),
        timeout=8,
        http_proxy_host=None,
        origin=base,
    )


subscribe_frame = json.dumps({
    "type": "iot-state-subscribe",
    "content": json.dumps({
        "requestId": "acceptance-1",
        "sceneId": scene_id,
        "bindings": [{
            "subscriptionId": "sub-acceptance-1",
            "objectId": object_id,
            "deviceId": device_id,
            "pointCode": point_code,
        }],
    }),
})


def subscribe(socket):
    socket.send(subscribe_frame)
    reply = json.loads(socket.recv())
    ack = json.loads(reply["content"])
    if reply["type"] != "iot-state-subscribe-ack" or ack.get("accepted") != ["sub-acceptance-1"]:
        socket.close()
        raise SystemExit(f"订阅未获确认：{reply['type']} {ack.get('code', '')}")


socket = connect()
subscribe(socket)
print("subscribe", "iot-state-subscribe-ack")
if publish:
    subscribe(socket)
    print("renewal request", "iot-state-subscribe-ack")
    # 注入唯一编号的标准遥测，核对同一事件同时出现在推送和状态查询中。
    event = {
        "schemaVersion": "1.0", "eventId": event_id, "tenantId": 2,
        "eventTime": datetime.now(timezone.utc).isoformat(), "deviceId": device_id,
        "sceneId": scene_id, "objectId": object_id,
        "pointCode": point_code, "value": 32.4, "valueType": "NUMBER",
    }
    subprocess.run([
        "ssh", "-o", "BatchMode=yes", "192.168.10.148",
        "docker exec -i wx-iot-ws-acceptance-kafka /opt/kafka/bin/kafka-console-producer.sh "
        "--bootstrap-server 127.0.0.1:19092 --topic twin.event.v1",
    ], input=json.dumps(event) + "\n", text=True, check=True, timeout=15)
    frame = json.loads(socket.recv())
    update = json.loads(frame["content"])
    assert frame["type"] == "iot-state-update" and update["subscriptionId"] == "sub-acceptance-1"
    assert update["eventId"] == event_id and update["value"] == 32.4 and update["stateVersion"]
    socket.close()
    socket = connect()
    subscribe(socket)
    query = urllib.request.Request(base + "/admin-api/dimensions/iot-state/get?" + urlencode({
        "deviceId": device_id, "pointCode": point_code, "sceneId": scene_id, "objectId": object_id,
    }), headers={"Authorization": "Bearer " + token, "tenant-id": "2"})
    with urllib.request.urlopen(query, timeout=10) as response:
        state = json.load(response)
    assert state["code"] == 200 and state["data"]["eventId"] == event_id and state["data"]["value"] == 32.4
    print("update and query after reconnect", event_id, update["value"])
socket.send(json.dumps({
    "type": "iot-state-unsubscribe",
    "content": json.dumps({"subscriptionIds": ["sub-acceptance-1"]}),
}))
reply = json.loads(socket.recv())
socket.close()
if reply["type"] != "iot-state-unsubscribe-ack":
    raise SystemExit("未收到注销确认")
print("unsubscribe", reply["type"])
if publish:
    # 同一令牌改写租户提示后，握手或订阅必须被拒绝。
    try:
        foreign = connect("1")
    except websocket.WebSocketBadStatusException as error:
        assert error.status_code == 403
    else:
        try:
            foreign.send(subscribe_frame)
            denied = json.loads(foreign.recv())
            assert denied["type"] == "iot-state-subscribe-error"
            assert json.loads(denied["content"])["code"] == "FORBIDDEN"
        finally:
            foreign.close()
    print("forged tenant rejected")
