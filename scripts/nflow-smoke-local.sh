#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:4500}"
POLL_ATTEMPTS="${POLL_ATTEMPTS:-20}"
POLL_SECONDS="${POLL_SECONDS:-1}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

post_json() {
  local path="$1"
  local body="$2"
  curl -fsS \
    -X POST "${BASE_URL}${path}" \
    -H 'content-type: application/json' \
    -d "${body}"
}

get_json() {
  local path="$1"
  curl -fsS "${BASE_URL}${path}"
}

assert_status() {
  local label="$1"
  local response="$2"
  local expected="$3"
  local status
  status="$(jq -r '.status' <<<"${response}")"
  if [[ "${status}" != "${expected}" ]]; then
    echo "${label} expected status ${expected}, got ${status}" >&2
    echo "${response}" | jq . >&2
    exit 1
  fi
}

assert_jq() {
  local label="$1"
  local response="$2"
  local expression="$3"
  if ! jq -e "${expression}" <<<"${response}" >/dev/null; then
    echo "${label} failed assertion: ${expression}" >&2
    echo "${response}" | jq . >&2
    exit 1
  fi
}

poll_ticket_completed() {
  local label="$1"
  local ticket_id="$2"
  local response
  local status

  for _ in $(seq 1 "${POLL_ATTEMPTS}"); do
    response="$(get_json "/api/workflows/tickets/${ticket_id}")"
    status="$(jq -r '.status' <<<"${response}")"
    if [[ "${status}" == "COMPLETED" ]]; then
      echo "${label} completed ticketId=${ticket_id}"
      echo "${response}" | jq .
      POLLED_RESPONSE="${response}"
      return 0
    fi
    if [[ "${status}" == "FAILED" || "${status}" == "TIMED_OUT" ]]; then
      echo "${label} reached terminal failure status ${status}" >&2
      echo "${response}" | jq . >&2
      exit 1
    fi
    sleep "${POLL_SECONDS}"
  done

  echo "${label} did not complete after ${POLL_ATTEMPTS} attempts" >&2
  response="$(get_json "/api/workflows/tickets/${ticket_id}")"
  echo "${response}" | jq . >&2
  exit 1
}

require_command curl
require_command jq

echo "Checking application health at ${BASE_URL}"
get_json "/mgmt/health" | jq .

echo "Starting async REST workflow"
async_response="$(post_json "/api/workflows/rest-async" '{
  "correlationId": "smoke-rest-async-001",
  "payload": {
    "definition": "simple",
    "parameters": {
      "message": "hello",
      "priority": "normal"
    }
  }
}')"
async_ticket_id="$(jq -r '.ticketId' <<<"${async_response}")"
poll_ticket_completed "async-rest-workflow" "${async_ticket_id}"
assert_jq "async-rest-workflow" "${POLLED_RESPONSE}" '.result.engine == "async-rest-workflow"'
assert_jq "async-rest-workflow" "${POLLED_RESPONSE}" '.result.name == "simple"'
assert_jq "async-rest-workflow" "${POLLED_RESPONSE}" '.result.parameters.message == "hello"'
assert_jq "async-rest-workflow" "${POLLED_RESPONSE}" '.result.output.accepted == true'
assert_jq "async-rest-workflow" "${POLLED_RESPONSE}" '.result.output.echo.priority == "normal"'
assert_jq "async-rest-workflow" "${POLLED_RESPONSE}" '.result.steps == ["echo-input"]'
assert_jq "async-rest-workflow" "${POLLED_RESPONSE}" '.result.definitionSteps[0].id == "echo-input"'
assert_jq "async-rest-workflow" "${POLLED_RESPONSE}" '.result.workflowPath == ["begin", "done"]'

echo "Starting blocking REST workflow"
blocking_response="$(post_json "/api/workflows/rest-blocking?timeout=PT15S" '{
  "correlationId": "smoke-rest-blocking-001",
  "payload": {
    "definition": "complex",
    "parameters": {
      "resource": "SMOKE-BLOCKING",
      "quantity": 2
    }
  }
}')"
assert_status "blocking-rest-workflow" "${blocking_response}" "COMPLETED"
assert_jq "blocking-rest-workflow" "${blocking_response}" '.result.engine == "blocking-rest-workflow"'
assert_jq "blocking-rest-workflow" "${blocking_response}" '.result.name == "complex"'
assert_jq "blocking-rest-workflow" "${blocking_response}" '.result.steps == ["validate-request", "prepare-execution", "execute-work", "collect-output"]'
assert_jq "blocking-rest-workflow" "${blocking_response}" '.result.definitionSteps | length == 4'
assert_jq "blocking-rest-workflow" "${blocking_response}" '.result.definitionSteps[2].id == "execute-work"'
assert_jq "blocking-rest-workflow" "${blocking_response}" '.result.output.status == "accepted"'
assert_jq "blocking-rest-workflow" "${blocking_response}" '.result.output.parameters.resource == "SMOKE-BLOCKING"'
assert_jq "blocking-rest-workflow" "${blocking_response}" '.result.workflowPath | length == 7'
echo "${blocking_response}" | jq .

echo "Starting blocking REST validation failure path"
blocking_failure_response="$(post_json "/api/workflows/rest-blocking?timeout=PT15S" '{
  "correlationId": "smoke-rest-blocking-invalid-001",
  "payload": {
    "definition": "complex",
    "failValidation": true,
    "parameters": {
      "reason": "smoke-test"
    }
  }
}')"
assert_status "blocking-rest-workflow invalid payload" "${blocking_failure_response}" "FAILED"
assert_jq "blocking-rest-workflow invalid payload" "${blocking_failure_response}" '.error == "Validation rejected by payload"'
echo "${blocking_failure_response}" | jq .

echo "Starting inbound-message workflow"
inbound_response="$(post_json "/api/workflows/inbound-test" '{
  "correlationId": "smoke-inbound-001",
  "payload": {
    "definition": "rtl433-data-pipeline",
    "raw": {
      "model": "Acurite-Tower",
      "id": 12345,
      "channel": "A",
      "temperature_C": 21.7,
      "humidity": 44,
      "battery_ok": 1
    }
  }
}')"
inbound_ticket_id="$(jq -r '.ticketId' <<<"${inbound_response}")"
poll_ticket_completed "inbound-message-workflow" "${inbound_ticket_id}"
assert_jq "inbound-message-workflow" "${POLLED_RESPONSE}" '.result.engine == "inbound-message-workflow"'
assert_jq "inbound-message-workflow" "${POLLED_RESPONSE}" '.result.name == "rtl433-data-pipeline"'
assert_jq "inbound-message-workflow" "${POLLED_RESPONSE}" '.result.output.sensorType == "weather-sensor"'
assert_jq "inbound-message-workflow" "${POLLED_RESPONSE}" '.result.output.assetKey == "weather-sensor:12345"'
assert_jq "inbound-message-workflow" "${POLLED_RESPONSE}" '.result.routeDestination == "telemetry.normalized"'
assert_jq "inbound-message-workflow" "${POLLED_RESPONSE}" '.result.output.normalizedMeasurements.temperature.unit == "C"'
assert_jq "inbound-message-workflow" "${POLLED_RESPONSE}" '.result.output.normalizedMeasurements.humidity.unit == "%"'
assert_jq "inbound-message-workflow" "${POLLED_RESPONSE}" '.result.steps == ["ingest-raw-message", "decode-device-payload", "normalize-measurements", "classify-sensor", "enrich-asset-metadata", "route-telemetry"]'
assert_jq "inbound-message-workflow" "${POLLED_RESPONSE}" '.result.definitionSteps | length == 6'
assert_jq "inbound-message-workflow" "${POLLED_RESPONSE}" '.result.definitionSteps[2].id == "normalize-measurements"'
assert_jq "inbound-message-workflow" "${POLLED_RESPONSE}" '.result.workflowPath | length == 6'

echo "nFlow local smoke test completed successfully"
