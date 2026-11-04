#!/usr/bin/env python3
"""用标准库探测 MQTT 3.1.1 认证和单个主题的 QoS 1 订阅权限。

只建立临时清洁会话，不发布消息；密码只从环境变量读取，不打印到输出。
"""

from __future__ import annotations

import math
import os
import socket
import ssl
import struct
import sys
import time
import uuid


def encode_remaining_length(length: int) -> bytes:
    if not 0 <= length <= 268435455:
        raise ValueError("MQTT 包长度超出协议范围")
    result = bytearray()
    while True:
        digit = length % 128
        length //= 128
        if length:
            digit |= 0x80
        result.append(digit)
        if not length:
            return bytes(result)


def receive(sock: socket.socket, count: int, deadline: float) -> bytes:
    # 每次读取共享同一截止时间，避免零碎数据或持续上报无限延长等待。
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise TimeoutError("MQTT 操作超时")
    sock.settimeout(remaining)
    return sock.recv(count)


def read_remaining_length(sock: socket.socket, deadline: float) -> int:
    multiplier = 1
    value = 0
    for _ in range(4):
        digit = receive(sock, 1, deadline)
        if len(digit) != 1:
            raise RuntimeError("Broker 在返回包长度前关闭了连接")
        number = digit[0]
        value += (number & 127) * multiplier
        if not number & 128:
            return value
        multiplier *= 128
    raise RuntimeError("Broker 返回了无效的 MQTT 包长度")


def read_packet(sock: socket.socket, deadline: float) -> tuple[int, bytes]:
    first = receive(sock, 1, deadline)
    if len(first) != 1:
        raise RuntimeError("Broker 未返回 MQTT 数据包")
    length = read_remaining_length(sock, deadline)
    if length > 1024 * 1024:
        raise RuntimeError("MQTT 返回包超过探测工具的 1 MiB 上限")
    payload = bytearray()
    while len(payload) < length:
        chunk = receive(sock, length - len(payload), deadline)
        if not chunk:
            raise RuntimeError("Broker 在数据包未接收完整前关闭了连接")
        payload.extend(chunk)
    return first[0], bytes(payload)


def utf8(value: str) -> bytes:
    if "\x00" in value:
        raise ValueError("MQTT 字符串不能包含空字符")
    encoded = value.encode("utf-8")
    if len(encoded) > 0xFFFF:
        raise ValueError("MQTT 字段不能超过 65535 字节")
    return struct.pack("!H", len(encoded)) + encoded


def connect_packet(client_id: str, username: str | None, password: str | None) -> bytes:
    flags = 0x02  # 清洁会话，退出后不保留订阅及未确认报文。
    payload = utf8(client_id)
    if username is not None:
        flags |= 0x80
        payload += utf8(username)
        if password is not None:
            flags |= 0x40
            # MQTT 密码为二进制字段，允许空字符，与用户名的字符串限制不同。
            encoded = password.encode("utf-8")
            if len(encoded) > 0xFFFF:
                raise ValueError("MQTT 密码不能超过 65535 字节")
            payload += struct.pack("!H", len(encoded)) + encoded
    variable = utf8("MQTT") + bytes((4, flags)) + struct.pack("!H", 30)
    remaining = variable + payload
    return b"\x10" + encode_remaining_length(len(remaining)) + remaining


def command_packet(packet_type: int, packet_id: int, topic: str, qos: int = 1) -> bytes:
    payload = struct.pack("!H", packet_id) + utf8(topic)
    if packet_type == 8:  # 订阅请求携带最大服务质量等级。
        payload += bytes((qos,))
    remaining = encode_remaining_length(len(payload))
    return bytes((packet_type << 4 | 2,)) + remaining + payload


def wait_ack(sock: socket.socket, expected: int, deadline: float) -> bytes:
    while True:
        header, payload = read_packet(sock, deadline)
        if header == expected:
            return payload
        # Broker 可以在 SUBACK 前投递保留消息。只丢弃，不输出、不确认、不写业务。
        # 清洁会话断开即销毁这些临时报文，探测不声明实际消息投递成功。
        if expected != 0x20 and header >> 4 == 3 and ((header >> 1) & 3) <= 1:
            continue
        raise RuntimeError("Broker 返回非预期 MQTT 控制包")


def probe() -> None:
    host = os.getenv("IOT_MQTT_PROBE_HOST", "192.168.10.148")
    port = int(os.getenv("IOT_MQTT_PROBE_PORT", "1883"))
    topic = os.getenv("IOT_MQTT_PROBE_TOPIC")
    username = os.getenv("IOT_MQTT_PROBE_USERNAME")
    password = os.getenv("IOT_MQTT_PROBE_PASSWORD")
    tls_setting = os.getenv("IOT_MQTT_PROBE_TLS", "false").lower()
    if tls_setting not in ("true", "false"):
        raise RuntimeError("IOT_MQTT_PROBE_TLS 必须为 true 或 false")
    use_tls = tls_setting == "true"
    timeout = float(os.getenv("IOT_MQTT_PROBE_TIMEOUT", "10"))
    if not topic or not username or password is None:
        raise RuntimeError("请设置 IOT_MQTT_PROBE_TOPIC、IOT_MQTT_PROBE_USERNAME 和 IOT_MQTT_PROBE_PASSWORD")
    if "+" in topic or "#" in topic:
        raise RuntimeError("只允许一个确切主题，禁止通配符 + 和 #")
    if not host or not 1 <= port <= 65535:
        raise RuntimeError("主机不能为空，端口必须为 1 到 65535")
    if not math.isfinite(timeout) or not 0 < timeout <= 60:
        raise RuntimeError("超时必须为大于 0 且不超过 60 的有限秒数")
    # 默认使用协议要求支持的 23 位字母数字编号；受限代理可注入独立探测编号。
    client_id = os.getenv("IOT_MQTT_PROBE_CLIENT_ID", "probe" + uuid.uuid4().hex[:18])
    if not client_id or len(client_id.encode("utf-8")) > 256:
        raise RuntimeError("IOT_MQTT_PROBE_CLIENT_ID 必须为 1 到 256 字节")
    # 全部报文先校验、编码，再打开网络连接，非法配置不会触达服务器。
    connect = connect_packet(client_id, username, password)
    subscribe = command_packet(8, 1, topic, 1)
    unsubscribe = command_packet(10, 2, topic)

    raw = socket.create_connection((host, port), timeout=timeout)
    sock: socket.socket = raw
    connected = False
    try:
        if use_tls:
            context = ssl.create_default_context()
            context.minimum_version = ssl.TLSVersion.TLSv1_2
            sock = context.wrap_socket(raw, server_hostname=host)
        sock.settimeout(timeout)
        sock.sendall(connect)
        payload = wait_ack(sock, 0x20, time.monotonic() + timeout)
        if len(payload) != 2 or payload[0] != 0 or payload[1] > 5:
            raise RuntimeError("Broker 返回的 CONNACK 格式无效")
        if payload[1] != 0:
            raise RuntimeError(f"MQTT 认证失败或 Broker 拒绝连接，CONNACK 返回码={payload[1]}")

        connected = True
        sock.sendall(subscribe)
        payload = wait_ack(sock, 0x90, time.monotonic() + timeout)
        if len(payload) != 3 or payload[:2] != b"\x00\x01":
            raise RuntimeError("Broker 未返回匹配的 SUBACK")
        if payload[2] != 1:
            raise RuntimeError(f"主题未获得 QoS 1 订阅权限，SUBACK 返回码={payload[2]}")

        sock.sendall(unsubscribe)
        payload = wait_ack(sock, 0xB0, time.monotonic() + timeout)
        if payload != b"\x00\x02":
            raise RuntimeError("Broker 未返回匹配的 UNSUBACK")
        sock.sendall(b"\xe0\x00")
        connected = False
        print("MQTT 探测成功：认证通过、QoS 1 订阅及取消订阅已确认")
    finally:
        # 失败也尽力断开已接受的清洁会话，原始异常不被清理错误覆盖。
        if connected:
            try:
                sock.settimeout(min(timeout, 1))
                sock.sendall(b"\xe0\x00")
            except OSError:
                pass
        sock.close()
        if sock is not raw:
            raw.close()


def main() -> int:
    try:
        probe()
        return 0
    except RuntimeError as error:  # 只回显工具自身的固定错误和数字返回码。
        print(f"MQTT 探测失败：{error}", file=sys.stderr)
    except (ValueError, UnicodeError):
        print("MQTT 探测失败：环境变量数值或 MQTT 字段编码无效", file=sys.stderr)
    except TimeoutError:
        print("MQTT 探测失败：连接、握手或确认等待超时", file=sys.stderr)
    except ssl.SSLError:
        print("MQTT 探测失败：TLS 握手或证书校验失败", file=sys.stderr)
    except OSError:
        print("MQTT 探测失败：网络连接或传输错误", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
