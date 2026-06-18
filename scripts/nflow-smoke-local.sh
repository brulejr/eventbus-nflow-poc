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
    "sku": "SMOKE-ASYNC",
    "quantity": 1
  }
}')"
async_ticket_id="$(jq -r '.ticketId' <<<"${async_response}")"
poll_ticket_completed "async-rest-workflow" "${async_ticket_id}"

echo "Starting blocking REST workflow"
blocking_response="$(post_json "/api/workflows/rest-blocking?timeout=PT15S" '{
  "correlationId": "smoke-rest-blocking-001",
  "payload": {
    "sku": "SMOKE-BLOCKING",
    "quantity": 2,
    "amount": 19.95
  }
}')"
assert_status "blocking-rest-workflow" "${blocking_response}" "COMPLETED"
echo "${blocking_response}" | jq .

echo "Starting inbound-message workflow"
inbound_response="$(post_json "/api/workflows/inbound-test" '{
  "correlationId": "smoke-inbound-001",
  "payload": {
    "model": "Acurite-Tower",
    "id": 12345,
    "channel": "A",
    "temperature_C": 21.7,
    "humidity": 44,
    "battery_ok": 1
  }
}')"
inbound_ticket_id="$(jq -r '.ticketId' <<<"${inbound_response}")"
poll_ticket_completed "inbound-message-workflow" "${inbound_ticket_id}"

echo "nFlow local smoke test completed successfully"
