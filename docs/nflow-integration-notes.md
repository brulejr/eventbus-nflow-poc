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

- `async-rest-workflow`
- `blocking-rest-workflow`
- `inbound-message-workflow`

REST defaults:

- `POST /api/workflows/rest-async` defaults to `async-rest-workflow`.
- `POST /api/workflows/rest-blocking` defaults to `blocking-rest-workflow`.
- `POST /api/workflows/inbound-test` defaults to `inbound-message-workflow`.

Broker defaults:

- RabbitMQ messages are parsed by `InboundMessageParser`; missing `workflowType` becomes
  `inbound-message-workflow`.
- MQTT messages are mapped through `MqttWorkflowMessage`; include `workflowType` explicitly.

## First nFlow definition sketch

The nFlow site shows workflow definitions extending `WorkflowDefinition` and using states, permits,
`StateExecution`, `moveToState`, and `stopInState`. A first strongly typed definition can mirror this
shape:

```kotlin
package io.jrb.labs.nflowpoc.features.workflow.service.nflow.definitions

import io.nflow.engine.workflow.curated.StateExecution
import io.nflow.engine.workflow.definition.NextAction
import io.nflow.engine.workflow.definition.State
import io.nflow.engine.workflow.definition.WorkflowDefinition
import io.nflow.engine.workflow.definition.WorkflowStateType

class InboundMessageWorkflowDefinition : WorkflowDefinition("inbound-message-workflow", BEGIN, ERROR) {
    companion object {
        val BEGIN = State("begin", WorkflowStateType.start)
        val PROCESS = State("process")
        val DONE = State("done", WorkflowStateType.end)
        val ERROR = State("error", WorkflowStateType.manual)
    }

    init {
        permit(BEGIN, PROCESS)
        permit(PROCESS, DONE)
    }

    fun begin(execution: StateExecution): NextAction =
        moveToState(PROCESS, "begin -> process")

    fun process(execution: StateExecution): NextAction =
        stopInState(DONE, "process -> done")
}
```

## Completion contract

The adapter starts work but does not define the public completion contract by itself. A production
nFlow-backed implementation should complete tickets through one of these patterns:

- terminal workflow states call `WorkflowResultStore.markCompleted(...)` or `markFailed(...)`
- terminal workflow states publish an event consumed by a small listener that updates
  `WorkflowResultStore`

Keeping completion in the workflow layer preserves both REST modes:

- async REST returns a ticket immediately and polls later
- blocking REST starts the same ticket and waits for a terminal state

## Validation checklist

1. Start with `./gradlew bootRun --args='--spring.profiles.active=standalone,nflow'`.
2. Confirm `NflowConfig` creates the expected nFlow beans.
3. Start each canonical workflow type through REST or broker ingress.
4. Confirm the ticket ID is stored as the nFlow external ID.
5. Confirm nFlow state variables contain `ticketId`, `correlationId`, `source`, and `payloadJson`.
6. Add a terminal state or listener that updates `WorkflowResultStore`.
7. Replace reflection in `NflowWorkflowEngineAdapter` with typed nFlow API calls.
