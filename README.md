# EventBus nFlow POC

A Kotlin and Spring Boot proof of concept for routing REST, MQTT, and RabbitMQ inputs into a shared
workflow launch path. The application keeps broker ingress, message normalization, ticket tracking,
metrics, and workflow engine execution as separate feature slices.

## Current design

```text
REST async             -> WorkflowController        -> WorkflowLaunchService -> WorkflowEngineAdapter
REST blocking          -> WorkflowController        -> WorkflowLaunchService -> WorkflowEngineAdapter
REST inbound test      -> WorkflowController        -> WorkflowLaunchService -> WorkflowEngineAdapter
MQTT message           -> HiveMqttIngressAdapter    -> InboundWorkflowDispatcher -> WorkflowLaunchService
RabbitMQ message       -> RabbitIngressAdapter      -> InboundMessageParser      -> InboundWorkflowDispatcher
```

The important boundary is `WorkflowLaunchService`. All entry points produce a `WorkflowStartCommand`,
create or update a claim ticket in `WorkflowResultStore`, and then start the configured
`WorkflowEngineAdapter`.

## Feature layout

- `api`: REST workflow endpoints and DTOs.
- `features/workflow`: workflow launch service, result store, metrics, feature configuration, and
  workflow engine adapters.
- `features/workflow/messaging`: shared inbound message model, JSON parser, and dispatcher.
- `features/workflow/service/nflow`: profile-gated nFlow adapter and workflow type constants.
- `features/ingress/mqtt`: HiveMQ client ingress and embedded Moquette configuration.
- `features/ingress/rabbit`: direct RabbitMQ client ingress.
- `trace`: request correlation filter.

The broker adapters intentionally avoid Spring Integration. MQTT uses the HiveMQ client directly.
RabbitMQ uses the Rabbit Java client directly.

## Workflow engine modes

The application uses this adapter boundary:

```kotlin
interface WorkflowEngineAdapter {
    fun start(command: WorkflowStartCommand, ticketId: String): WorkflowEngineHandle
}
```

Default mode uses `SimulatedWorkflowEngineAdapter` under `@Profile("!nflow")`. It is enough to
exercise REST, MQTT, RabbitMQ, tickets, blocking waits, and metrics without requiring an nFlow runtime.

The `nflow` profile enables `NflowConfig` and `NflowWorkflowEngineAdapter`. The adapter starts nFlow
instances through the nFlow `WorkflowInstanceFactory` and `WorkflowInstanceService` boundary using
reflection while the exact nFlow 11 Spring Boot API is validated.

Canonical workflow types are defined in `WorkflowTypes`:

- `async-rest-workflow`: simple starter workflow for async REST.
- `blocking-rest-workflow`: complex multi-step workflow for blocking REST.
- `inbound-message-workflow`: rtl433-data-pipeline simulator for REST inbound tests, MQTT, and RabbitMQ.

## Message format

Inbound broker messages are JSON objects:

```json
{
  "workflowType": "inbound-message-workflow",
  "correlationId": "message-001",
  "payload": {
    "model": "Acurite-Tower",
    "id": 12345,
    "channel": "A",
    "temperature_C": 21.7,
    "humidity": 44,
    "battery_ok": 1
  }
}
```

For RabbitMQ, `InboundMessageParser` defaults `workflowType` to `inbound-message-workflow` when it is
omitted, keeps `correlationId` optional, and defaults `payload` to an empty object. MQTT maps the same
shape through `MqttWorkflowMessage`.

## Run standalone

```bash
./gradlew bootRun --args='--spring.profiles.active=standalone'
```

The standalone profile starts:

- Spring Boot app on `localhost:4500`
- H2 file database in `./data`
- embedded Moquette on `localhost:1884`
- MQTT ingress enabled on topic `poc/workflow/start`
- RabbitMQ ingress disabled
- simulated workflow engine unless `nflow` is also enabled

To try the nFlow-backed adapter path:

```bash
./gradlew bootRun --args='--spring.profiles.active=standalone,nflow,nflow.db.h2'
```

## Run with Docker

```bash
docker compose up --build
```

The Docker profile is intended to run:

- app
- PostgreSQL
- Mosquitto on host port `1883`
- RabbitMQ on host port `5672`
- RabbitMQ management UI on host port `15672`

The application config currently sets `server.port: 4500`. If you use Docker Compose as written,
align the app port mapping or set `SERVER_PORT=8080` before relying on the app endpoint from the host.
The Docker profile also still contains legacy `poc.broker.*` keys; the current ingress configuration
classes bind to `workflow.ingress.*`. Set `WORKFLOW_INGRESS_MQTT_*` and `WORKFLOW_INGRESS_RABBIT_*`
environment variables, or migrate `application-docker.yml`, before using broker ingress in Docker.

## REST examples

### Async

```bash
curl -s -X POST http://localhost:4500/api/workflows/rest-async \
  -H 'content-type: application/json' \
  -d '{"correlationId":"rest-async-001","payload":{"sku":"ABC-123","quantity":5}}' | jq
```

Poll the returned `statusUrl`, or list all tickets:

```bash
curl -s http://localhost:4500/api/workflows/tickets | jq
```

### Blocking

```bash
curl -s -X POST 'http://localhost:4500/api/workflows/rest-blocking?timeout=PT10S' \
  -H 'content-type: application/json' \
  -d '{"correlationId":"rest-blocking-001","payload":{"sku":"ABC-123","quantity":5}}' | jq
```

### In-memory inbound test

```bash
curl -s -X POST http://localhost:4500/api/workflows/inbound-test \
  -H 'content-type: application/json' \
  -d '{"correlationId":"inbound-test-001","payload":{"model":"Acurite-Tower","id":12345,"channel":"A","temperature_C":21.7,"humidity":44,"battery_ok":1}}' | jq
```

## MQTT example

Standalone mode starts embedded Moquette on port `1884`:

```bash
mosquitto_pub -h localhost -p 1884 -t poc/workflow/start -m '{
  "workflowType":"inbound-message-workflow",
  "correlationId":"mqtt-demo-001",
  "payload":{"model":"Acurite-Tower","id":12345,"channel":"A","temperature_C":21.7,"humidity":44,"battery_ok":1}
}'
```

Then check tickets:

```bash
curl -s http://localhost:4500/api/workflows/tickets | jq
```

## RabbitMQ example

RabbitMQ ingress is enabled by `workflow.ingress.rabbit.enabled=true`. With RabbitMQ running:

```bash
curl -u guest:guest -H "content-type:application/json" \
  -X POST http://localhost:15672/api/exchanges/%2f/poc.workflow/publish \
  -d '{
    "properties":{},
    "routing_key":"workflow.start",
    "payload":"{\"workflowType\":\"inbound-message-workflow\",\"correlationId\":\"rabbit-demo-001\",\"payload\":{\"model\":\"Acurite-Tower\",\"id\":12345,\"channel\":\"A\",\"temperature_C\":21.7,\"humidity\":44,\"battery_ok\":1}}",
    "payload_encoding":"string"
  }'
```

## Actuator and metrics

```text
GET /mgmt/health
GET /mgmt/info
GET /mgmt/metrics
GET /mgmt/prometheus
```

Custom metric names include:

- `workflow.started`
- `workflow.completed`
- `workflow.failed`
- `workflow.duration`
- `workflow.ingress.received`

## Configuration keys

Feature toggles and ingress settings use these prefixes:

- `workflow.engine`
- `workflow.ingress.mqtt`
- `workflow.ingress.rabbit`

Standalone profile defaults:

- `workflow.ingress.mqtt.enabled=true`
- `workflow.ingress.mqtt.embedded=true`
- `workflow.ingress.mqtt.host=localhost`
- `workflow.ingress.mqtt.port=1884`
- `workflow.ingress.rabbit.enabled=false`

Docker profile defaults:

- The desired Docker ingress keys are `workflow.ingress.mqtt.*` and `workflow.ingress.rabbit.*`.
- `application-docker.yml` currently uses legacy `poc.broker.*` keys, so migrate those keys or provide
  environment variables such as `WORKFLOW_INGRESS_RABBIT_ENABLED=true`.

## Documentation

- [nFlow integration notes](docs/nflow-integration-notes.md)
- [Git submodule notes](docs/git-submodule-notes.md)

## Next steps

1. Validate the `standalone,nflow` profile against nFlow 11 and replace the reflection adapter with a
   strongly typed adapter.
2. Replace lightweight workflow type placeholders with concrete nFlow `WorkflowDefinition` classes.
3. Align the Docker app port mapping with `server.port`.
4. Move reusable MQTT client plumbing into `ksb-commons` after the ingress behavior stabilizes.
5. Add OpenTelemetry tracing once the workflow identity model stabilizes.
