# EventBus nFlow POC

A Kotlin + Spring Boot proof-of-concept for a message-driven workflow service with:

- REST async workflow launch returning a claim ticket
- REST blocking workflow launch returning a result
- MQTT inbound workflow launch using embedded Moquette locally
- RabbitMQ inbound workflow launch in Docker
- Micrometer / Spring Boot Actuator metrics
- a narrow `WorkflowEngineAdapter` seam for nFlow integration
- no Spring Integration

## Why this shape

The POC separates workflow ingress from workflow execution:

```text
REST async        -> WorkflowLaunchService -> WorkflowEngineAdapter -> ticket/result store
REST blocking     -> WorkflowLaunchService -> WorkflowEngineAdapter -> await terminal result
MQTT/Rabbit input -> MessageIngressAdapter  -> WorkflowLaunchService
```

Local development uses embedded Moquette only. Docker uses Mosquitto and RabbitMQ.

## Important implementation note

The project has two workflow engine modes behind the same seam:

```kotlin
interface WorkflowEngineAdapter {
    fun start(command: WorkflowStartCommand, ticketId: String): WorkflowEngineHandle
}
```

Default mode uses `SimulatedWorkflowEngineAdapter` under `@Profile("!nflow")`. This lets you validate
the REST, MQTT, RabbitMQ, ticketing, blocking, and metrics behavior immediately.

The `nflow` profile enables `NflowConfig` and `NflowWorkflowEngineAdapter`. The nFlow config imports
`io.nflow.rest.config.RestConfiguration` through an import selector, and the adapter starts workflow
instances through the nFlow `WorkflowInstanceFactory` / `WorkflowInstanceService` boundary using
reflection. This keeps the source compile-safe while making the nFlow integration point real and
profile-gated. Once you validate the exact nFlow 11 API locally, replace the reflection adapter with
a strongly typed adapter.

The three workflow blueprint objects under `io.jrb.labs.nflowpoc.workflow.nflow` keep the exemplar
workflow type/state names in one place:

- `async-rest-workflow`
- `blocking-rest-workflow`
- `inbound-message-workflow`

## Run standalone

```bash
./gradlew bootRun --args='--spring.profiles.active=standalone'
```

To try the nFlow-backed adapter path instead of the simulated adapter:

```bash
./gradlew bootRun --args='--spring.profiles.active=standalone,nflow'
```

The standalone profile starts:

- Spring Boot app on `8080`
- H2 file database in `./data`
- embedded Moquette on `localhost:1884`
- no RabbitMQ

## Run with Docker

```bash
docker compose up --build
```

The Docker profile starts:

- app
- PostgreSQL
- Mosquitto
- RabbitMQ with management UI

## REST examples

### Async

```bash
curl -s -X POST http://localhost:8080/api/workflows/rest-async \
  -H 'content-type: application/json' \
  -d '{"correlationId":"rest-async-001","payload":{"sku":"ABC-123","quantity":5}}' | jq
```

Then poll the returned `statusUrl`.

### Blocking

```bash
curl -s -X POST 'http://localhost:8080/api/workflows/rest-blocking?timeout=PT10S' \
  -H 'content-type: application/json' \
  -d '{"correlationId":"rest-blocking-001","payload":{"sku":"ABC-123","quantity":5}}' | jq
```

### Local MQTT through embedded Moquette

Install Mosquitto clients locally, then:

```bash
mosquitto_pub -h localhost -p 1884 -t poc/workflow/start -m '{
  "workflowType":"inbound-message-workflow",
  "correlationId":"mqtt-demo-001",
  "payload":{"sku":"ABC-123","quantity":5,"destinationZip":"12309"}
}'
```

Check the log or call:

```bash
curl -s http://localhost:8080/api/workflows/tickets | jq
```

## RabbitMQ Docker example

With Docker profile running:

```bash
curl -u guest:guest -H "content-type:application/json" \
  -X POST http://localhost:15672/api/exchanges/%2f/poc.workflow/publish \
  -d '{
    "properties":{},
    "routing_key":"workflow.start",
    "payload":"{\"workflowType\":\"inbound-message-workflow\",\"correlationId\":\"rabbit-demo-001\",\"payload\":{\"sku\":\"ABC-123\",\"quantity\":5}}",
    "payload_encoding":"string"
  }'
```

## Actuator / metrics

```text
GET /mgmt/health
GET /mgmt/metrics
GET /mgmt/prometheus
```

Custom metric names include:

- `workflow_started_total`
- `workflow_completed_total`
- `workflow_failed_total`
- `workflow_duration_seconds`
- `workflow_ingress_received_total`

## Next steps

1. Validate `standalone,nflow` against nFlow 11 and convert `NflowWorkflowEngineAdapter` from reflection to strongly typed calls.
2. Replace the lightweight blueprint objects with concrete nFlow `WorkflowDefinition` classes.
3. Move MQTT client plumbing into `ksb-commons` and keep only the adapter usage here.
4. Add OpenTelemetry tracing once the workflow identity model stabilizes.
5. Add nFlow Explorer if you decide it should be part of the POC runtime.

## Local profile behavior

By default, the application uses the `standalone` profile via `spring.profiles.default=standalone`.
That local mode is intentionally RabbitMQ-free:

- H2 file database
- embedded Moquette MQTT broker
- direct HiveMQ MQTT client
- simulated workflow engine unless `nflow` is explicitly enabled
- RabbitMQ auto-configuration and Rabbit health checks disabled

RabbitMQ is only expected in the Docker profile. Run Docker mode with:

```bash
SPRING_PROFILES_ACTIVE=docker ./gradlew bootRun
```

The Docker profile enables RabbitMQ health checks and expects the RabbitMQ container from
`docker-compose.yml` to be running.


## Local RabbitMQ behavior

The standalone/default profile intentionally does not load Spring AMQP or connect to RabbitMQ. Local development uses embedded Moquette for MQTT and H2 for persistence. RabbitMQ is only activated when `poc.broker.rabbit.enabled=true`, which is set by the `docker` profile.
