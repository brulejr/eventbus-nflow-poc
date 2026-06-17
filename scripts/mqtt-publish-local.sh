#!/usr/bin/env bash
set -euo pipefail

MQTT_HOST="${MQTT_HOST:-localhost}"
MQTT_PORT="${MQTT_PORT:-1884}"
MQTT_TOPIC="${MQTT_TOPIC:-poc/workflow/start}"
MQTT_CLIENT_ID="${MQTT_CLIENT_ID:-eventbus-nflow-poc-publisher-$$}"

mosquitto_pub \
  -h "${MQTT_HOST}" \
  -p "${MQTT_PORT}" \
  -i "${MQTT_CLIENT_ID}" \
  -V mqttv311 \
  -t "${MQTT_TOPIC}" \
  -m '{
    "workflowType": "inbound-message-workflow",
    "correlationId": "mqtt-demo-001",
    "payload": {
      "eventType": "QuoteRequested",
      "sku": "ABC-123",
      "quantity": 5,
      "destinationZip": "12309"
    }
  }'