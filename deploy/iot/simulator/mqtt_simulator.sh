#!/usr/bin/env bash
set -Eeuo pipefail

# 虚拟设备模拟器：使用 mosquitto_pub 复用真实 MQTT 上报合同，不依赖物理设备。
# 运行前通过受限环境注入凭据和 CA 文件，脚本不会打印密码或完整连接参数。

require_value() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "$value" ]]; then
    echo "缺少环境变量：$name" >&2
    exit 2
  fi
}

require_value MQTT_HOST
require_value MQTT_PORT
require_value MQTT_TOPIC
require_value MQTT_USERNAME
require_value MQTT_PASSWORD
require_value EXTERNAL_DEVICE_ID

MQTT_TLS="${MQTT_TLS:-true}"
MQTT_CA_FILE="${MQTT_CA_FILE:-}"
POINT_CODE="${POINT_CODE:-rawTemperature}"
VALUE_TYPE="${VALUE_TYPE:-NUMBER}"
UNIT="${UNIT:-C}"
QUALITY="${QUALITY:-GOOD}"
SCENARIO="${SCENARIO:-normal}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-5}"
MAX_MESSAGES="${MAX_MESSAGES:-0}"
START_VALUE="${START_VALUE:-25.0}"
STEP="${STEP:-0.5}"

if [[ "$MQTT_TLS" == "true" && -z "$MQTT_CA_FILE" ]]; then
  echo "启用 TLS 时必须提供 MQTT_CA_FILE" >&2
  exit 2
fi

case "$SCENARIO" in
  normal|duplicate|out_of_order|invalid|disconnect) ;;
  *) echo "不支持的模拟场景：$SCENARIO" >&2; exit 2 ;;
esac

if ! [[ "$INTERVAL_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]] || ! [[ "$MAX_MESSAGES" =~ ^[0-9]+$ ]]; then
  echo "INTERVAL_SECONDS 或 MAX_MESSAGES 格式无效" >&2
  exit 2
fi

publish() {
  local payload="$1"
  local command=(mosquitto_pub -h "$MQTT_HOST" -p "$MQTT_PORT" -u "$MQTT_USERNAME"
    -P "$MQTT_PASSWORD" -q 1 -t "$MQTT_TOPIC" -m "$payload")
  if [[ "$MQTT_TLS" == "true" ]]; then
    command+=(--cafile "$MQTT_CA_FILE")
  fi
  "${command[@]}"
}

event_time() {
  date -u +"%Y-%m-%dT%H:%M:%S.%3NZ"
}

event_time_seconds_ago() {
  local seconds="$1"
  date -u -d "-$seconds seconds" +"%Y-%m-%dT%H:%M:%S.%3NZ"
}

trace_id() {
  if [[ -r /proc/sys/kernel/random/uuid ]]; then
    tr -d '\n' < /proc/sys/kernel/random/uuid
  else
    date -u +"%Y%m%dT%H%M%S%3N"
  fi
}

number_add() {
  awk -v left="$1" -v right="$2" 'BEGIN { printf "%.12f", left + right }'
}

make_payload() {
  local value="$1"
  local timestamp="$2"
  local trace="$3"
  printf '{"externalDeviceId":"%s","pointCode":"%s","value":%s,"valueType":"%s","unit":"%s","quality":"%s","eventTime":"%s","traceId":"%s"}' \
    "$EXTERNAL_DEVICE_ID" "$POINT_CODE" "$value" "$VALUE_TYPE" "$UNIT" "$QUALITY" "$timestamp" "$trace"
}

publish_one() {
  local value="$1"
  local timestamp="$2"
  local trace="$3"
  local payload
  payload="$(make_payload "$value" "$timestamp" "$trace")"
  publish "$payload"
  echo "已发布模拟事件 traceId=$trace value=$value scenario=$SCENARIO"
}

publish_invalid() {
  local trace
  trace="$(trace_id)"
  publish "{\"schemaVersion\":\"0.0\",\"eventId\":\"invalid-$trace\"}"
  echo "已发布非法模拟事件 traceId=$trace scenario=invalid"
}

value="$START_VALUE"
count=0
while [[ "$MAX_MESSAGES" == "0" || "$count" -lt "$MAX_MESSAGES" ]]; do
  if [[ "$SCENARIO" == "invalid" ]]; then
    publish_invalid
  else
    now="$(event_time)"
    trace="$(trace_id)"
    publish_one "$value" "$now" "$trace"

    case "$SCENARIO" in
      duplicate)
        publish_one "$value" "$now" "$trace"
        ;;
      out_of_order)
        old_trace="$(trace_id)"
        old_value="$(number_add "$value" -5)"
        publish_one "$old_value" "$(event_time_seconds_ago 3)" "$old_trace"
        ;;
      disconnect)
        break
        ;;
    esac
    value="$(number_add "$value" "$STEP")"
  fi

  count=$((count + 1))
  if [[ "$MAX_MESSAGES" == "0" || "$count" -lt "$MAX_MESSAGES" ]]; then
    sleep "$INTERVAL_SECONDS"
  fi
done
