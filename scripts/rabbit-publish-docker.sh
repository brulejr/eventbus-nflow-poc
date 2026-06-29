#!/usr/bin/env bash
set -euo pipefail

curl -u guest:guest   -H 'content-type: application/json'   -X POST http://localhost:15672/api/exchanges/%2f/poc.workflow/publish   -d '{
    "properties": {},
    "routing_key": "workflow.start",
    "payload": "{\"workflowType\":\"rtl433-data-pipeline\",\"correlationId\":\"rabbit-demo-001\",\"payload\":{\"model\":\"Acurite-Tower\",\"id\":12345,\"channel\":\"A\",\"temperature_C\":21.7,\"humidity\":44,\"battery_ok\":1}}",
    "payload_encoding": "string"
  }'
