# nFlow integration notes

The application keeps nFlow behind a small workflow engine adapter so ingress and ticketing stay
independent from the workflow runtime.

```kotlin
interface WorkflowEngineAdapter {
    fun start(command: WorkflowStartCommand, ticketId: String): WorkflowEngineHandle
}
```

`WorkflowLaunchService` owns the shared launch sequence:

1. Record `workflow.started`.
2. Create a ticket in `WorkflowResultStore`.
3. Call `WorkflowEngineAdapter.start(...)`.
4. Store the returned `engineInstanceId`.
5. Let async callers poll the ticket, or let blocking callers wait for a terminal result.

## Current runtime modes

`SimulatedWorkflowEngineAdapter` is active under `@Profile("!nflow")`. It completes tickets after the
configured simulated delay and lets REST, MQTT, RabbitMQ, metrics, and blocking waits be tested without
nFlow.

`NflowWorkflowEngineAdapter` is active under `@Profile("nflow")`. It lives under:

```text
src/main/kotlin/io/jrb/labs/nflowpoc/features/workflow/service/nflow
```

The adapter currently uses reflection to call the nFlow 11 runtime boundary:

- `io.nflow.engine.workflow.instance.WorkflowInstanceFactory`
- `io.nflow.engine.service.WorkflowInstanceService`

This keeps the rest of the POC compile-stable while the exact nFlow 11 Spring Boot API is validated.
Once that path is proven locally, replace the reflection calls with strongly typed nFlow calls behind
the same `WorkflowEngineAdapter` interface.

## State variables passed to nFlow

The nFlow adapter writes these variables when inserting the workflow instance:

- `ticketId`
- `correlationId`
- `source`
- `payloadJson`

The workflow external ID is also set to the ticket ID. That makes the ticket the stable public handle
for REST clients and broker-driven messages, while the nFlow instance ID remains the engine handle.

## Workflow types

Canonical workflow type names are defined in `WorkflowTypes`:

- `async-rest-workflow`: simple starter workflow.
- `blocking-rest-workflow`: complex multi-step starter workflow.
- `inbound-message-workflow`: rtl433-data-pipeline simulator.

REST defaults:

- `POST /api/workflows/rest-async` defaults to `async-rest-workflow`.
- `POST /api/workflows/rest-blocking` defaults to `blocking-rest-workflow`.
- `POST /api/workflows/inbound-test` defaults to `inbound-message-workflow`.

Broker defaults:

- RabbitMQ messages are parsed by `InboundMessageParser`; missing `workflowType` becomes
  `inbound-message-workflow`.
- MQTT messages are mapped through `MqttWorkflowMessage`; include `workflowType` explicitly.

## Starter nFlow definitions

The `nflow` profile now contributes three Spring `WorkflowDefinition` beans:

- `AsyncRestWorkflow`: simple `begin -> done` flow that proves launch and completion.
- `BlockingRestWorkflow`: multi-step flow with validation, inventory reservation, payment authorization,
  fulfillment scheduling, and notification states.
- `InboundMessageWorkflow`: rtl433-style pipeline with ingest, decode, normalize, classify, enrich, and
  route states.

All three definitions share `NflowWorkflowSupport`, which parses the payload state variable and updates
`WorkflowResultStore` when the workflow reaches a terminal outcome.

## Completion contract

The adapter starts work but does not define the public completion contract by itself. A production
nFlow-backed implementation should complete tickets through one of these patterns:

- terminal workflow states call `WorkflowResultStore.markCompleted(...)` or `markFailed(...)`
- terminal workflow states publish an event consumed by a small listener that updates
  `WorkflowResultStore`

Keeping completion in the workflow layer preserves both REST modes:

- async REST returns a ticket immediately and polls later
- blocking REST starts the same ticket and waits for a terminal state

## Validation status

Validated with:

```bash
./gradlew --console=plain bootRun --args='--spring.profiles.active=standalone,nflow,nflow.db.h2'
```

- [x] Application starts with the `standalone,nflow,nflow.db.h2` profiles.
- [x] `NflowConfig` loads and nFlow initializes its H2 schema.
- [x] `POST /api/workflows/rest-async` starts `async-rest-workflow` and completes successfully.
- [x] `POST /api/workflows/rest-blocking` starts `blocking-rest-workflow` and completes successfully.
- [x] `POST /api/workflows/inbound-test`, MQTT, or RabbitMQ starts `inbound-message-workflow` and completes successfully.
- [x] Terminal workflow states update `WorkflowResultStore`.
- [x] The ticket ID is stored as the nFlow workflow `external_id`.
- [x] nFlow state variables contain `ticketId`, `correlationId`, `source`, and `payloadJson`.

The nFlow H2 state-variable checks can be verified with:

```sql
select wf.id, wf.type, wf.external_id, ws.action_id, ws.state_key, ws.state_value
from nflow_workflow wf
join nflow_workflow_state ws on ws.workflow_id = wf.id
where wf.external_id = '<ticket-id>'
order by ws.action_id, ws.state_key;
```

Remaining validation:

- [ ] Replace reflection in `NflowWorkflowEngineAdapter` with typed nFlow API calls.
