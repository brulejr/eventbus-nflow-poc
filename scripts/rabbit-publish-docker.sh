#!/usr/bin/env bash
set -euo pipefail

curl -u guest:guest   -H 'content-type: application/json'   -X POST http://localhost:15672/api/exchanges/%2f/poc.workflow/publish   -d '{
    "properties": {},
    "routing_key": "workflow.start",
    "payload": "{\"workflowType\":\"inbound-message-workflow\",\"correlationId\":\"rabbit-demo-001\",\"payload\":{\"eventType\":\"QuoteRequested\",\"sku\":\"RABBIT-123\",\"quantity\":42}}",
    "payload_encoding": "string"
  }'
