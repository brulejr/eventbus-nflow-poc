# nFlow integration notes

This POC intentionally keeps the runtime workflow engine behind:

```kotlin
interface WorkflowEngineAdapter {
    fun start(command: WorkflowStartCommand, ticketId: String): WorkflowEngineHandle
}
```

The starter project runs with `SimulatedWorkflowEngineAdapter` so REST, MQTT, RabbitMQ, ticketing,
blocking, and metrics can be exercised immediately.

## First nFlow definition sketch

The nFlow site shows workflow definitions extending `WorkflowDefinition` and using states, permits,
`StateExecution`, `moveToState`, and `stopInState`. The shape below mirrors that model:

```kotlin
package io.jrb.labs.nflowpoc.workflow.nflow.definitions

import io.nflow.engine.workflow.curated.StateExecution
import io.nflow.engine.workflow.definition.NextAction
import io.nflow.engine.workflow.definition.State
import io.nflow.engine.workflow.definition.WorkflowDefinition
import io.nflow.engine.workflow.definition.WorkflowStateType

class AsyncRestWorkflowDefinition : WorkflowDefinition("async-rest-workflow", BEGIN, ERROR) {
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

    fun begin(execution: StateExecution): NextAction = moveToState(PROCESS, "begin -> process")

    fun process(execution: StateExecution): NextAction = stopInState(DONE, "process -> done")
}
```

## Real adapter TODO

The first real nFlow adapter should do only this:

1. Create a workflow instance of type `command.workflowType`.
2. Store state variables:
   - `ticketId`
   - `correlationId`
   - `source`
   - serialized `payload`
3. Return the nFlow workflow instance ID as `WorkflowEngineHandle.engineInstanceId`.
4. Have the terminal nFlow state call `WorkflowResultStore.markCompleted(...)` or publish a terminal event
   consumed by a small listener that marks the ticket complete.

Keeping completion outside the controller preserves both REST blocking and async claim-ticket behavior.
