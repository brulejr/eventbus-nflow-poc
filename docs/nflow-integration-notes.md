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

## Current runtime mode

`NflowWorkflowEngineAdapter` is active under `@Profile("nflow")`. It lives under:

```text
src/main/kotlin/io/jrb/labs/nflowpoc/features/workflow/service/nflow
```

The adapter uses typed nFlow 11 collaborators:

- `io.nflow.engine.workflow.instance.WorkflowInstanceFactory`
- `io.nflow.engine.service.WorkflowInstanceService`

The nFlow-specific calls remain behind the same `WorkflowEngineAdapter` interface, so ingress, ticketing,
metrics, and REST blocking behavior stay independent from the runtime implementation. No simulated
workflow engine is registered; local and validation runs should include the `nflow` profile.

Execution behavior lives outside the nFlow package:

```text
src/main/kotlin/io/jrb/labs/nflowpoc/features/workflow/service/execution
```

Those classes are plain Kotlin/Spring services. They do not extend nFlow types and do not import
`StateExecution`, `WorkflowDefinition`, `WorkflowState`, or `NextAction`.

Named reusable workflow definitions live in a separate layer:

```text
src/main/kotlin/io/jrb/labs/nflowpoc/features/workflow/definition
```

These classes describe reusable starter definitions and expand caller input into the generic command
shape expected by the execution engines.

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

- `async-rest-workflow`: generic one-step async execution engine.
- `blocking-rest-workflow`: generic blocking multi-step execution engine.
- `inbound-message-workflow`: generic inbound pipeline execution engine.

Named starter workflow definitions are:

- `simple`: one-step starter definition backed by `async-rest-workflow`.
- `complex`: multi-step starter definition backed by `blocking-rest-workflow`.
- `rtl433-data-pipeline`: rtl_433 telemetry starter definition backed by `inbound-message-workflow`.

REST defaults:

- `POST /api/workflows/rest-async` defaults to `async-rest-workflow`.
- `POST /api/workflows/rest-blocking` defaults to `blocking-rest-workflow`.
- `POST /api/workflows/inbound-test` defaults to `inbound-message-workflow`.

Broker defaults:

- RabbitMQ messages are parsed by `InboundMessageParser`; missing `workflowType` becomes
  `inbound-message-workflow`.
- MQTT messages are mapped through `MqttWorkflowMessage`; include `workflowType` explicitly.

`WorkflowDefinitionResolver` runs before the workflow engine adapter. It supports either style:

- Set `workflowType` to a named definition, such as `complex`.
- Keep the endpoint's default engine workflow type and set payload `definition`, `workflowDefinition`, or
  `name` to a named definition.

When a definition is found, the resolver changes the command's `workflowType` to the backing engine
workflow type and replaces the payload with the expanded generic execution command.

## Starter Workflow Definitions

The three starter definitions are code, not nFlow state graphs:

- `SimpleWorkflowDefinition`: expands `simple` into `{ name, parameters, output }` for
  `async-rest-workflow`.
- `ComplexWorkflowDefinition`: expands `complex` into `{ name, parameters, steps, output }` for
  `blocking-rest-workflow`.
- `Rtl433DataPipelineWorkflowDefinition`: maps rtl_433-style raw telemetry into `{ name, parameters,
  output, routingKey, routeDestination }` for `inbound-message-workflow`.

The rtl433 starter's domain mapping belongs here. nFlow and the inbound execution engine still only see a
generic named command with parameters, output, and routing metadata.

Each starter definition is wired with separate Spring step beans. These domain-level steps are distinct
from the generic nFlow shell states.

`simple`:

- `EchoInputStep`: `echo-input`

`complex`:

- `ValidateRequestStep`: `validate-request`
- `PrepareExecutionStep`: `prepare-execution`
- `ExecuteWorkStep`: `execute-work`
- `CollectOutputStep`: `collect-output`

`rtl433-data-pipeline`:

- `IngestRawMessageStep`: `ingest-raw-message`
- `DecodeDevicePayloadStep`: `decode-device-payload`
- `NormalizeMeasurementsStep`: `normalize-measurements`
- `ClassifySensorStep`: `classify-sensor`
- `EnrichAssetMetadataStep`: `enrich-asset-metadata`
- `RouteTelemetryStep`: `route-telemetry`

Expanded commands include both `steps`, the ordered step IDs, and `definitionSteps`, the step metadata
with descriptions plus declared input and output keys.

## nFlow Definitions And Execution Engines

The `nflow` profile contributes three Spring `WorkflowDefinition` beans. These classes define the nFlow
state graph and delegate each state method to a separate execution engine:

- `AsyncRestWorkflow`: nFlow definition for `begin -> done`; delegates to `AsyncRestExecutionEngine`.
- `BlockingRestWorkflow`: nFlow definition for
  `begin -> validate -> prepare -> execute -> collectOutput -> completeExecution -> done`; delegates to
  `BlockingRestExecutionEngine`.
- `InboundMessageWorkflow`: nFlow definition for
  `ingest -> inspect -> transform -> route -> completePipeline -> done`; delegates to
  `InboundMessageExecutionEngine`.

All three definitions share `NflowWorkflowSupport`, which is now adapter glue. It parses nFlow state into
a `WorkflowExecutionCommand`, applies `WorkflowExecutionStep` state variables back to nFlow, updates
`WorkflowResultStore` when an execution engine returns a terminal result or failure, and converts the
engine outcome to a nFlow `NextAction`.

## Generic execution contract

The execution engines are not business workflows. They do not know about orders, devices, rtl433, nFlow,
or any particular domain flow. Each engine receives a generic command:

```json
{
  "name": "command-or-workflow-name",
  "parameters": {
    "any": "caller supplied input"
  },
  "output": {
    "any": "caller supplied expected or simulated result"
  }
}
```

The result envelope uses the same shape:

```json
{
  "engine": "blocking-rest-workflow",
  "name": "command-or-workflow-name",
  "parameters": {},
  "output": {},
  "steps": [],
  "definitionSteps": [],
  "workflowPath": []
}
```

The blocking engine also accepts `steps` to describe the caller's logical command steps. The engine does
not interpret those steps; it records them in the result.

The inbound engine also accepts `routingKey` and `routeDestination`. The engine routes by those generic
values without interpreting the domain payload.

Failure simulation is generic:

- `failValidation: true` rejects the blocking engine during validation.
- `failPreparation: true` rejects the blocking engine during preparation.
- `failExecution: true` rejects the blocking engine during execution.
- `failInspection: true` rejects the inbound engine during inspection.
- `failTransform: true` rejects the inbound engine during transform.
- `failRoute: true` rejects the inbound engine during route.

The rtl433-data-pipeline starter example is now a payload submitted to `InboundMessageWorkflow`, not
logic embedded inside the nFlow definition. Its normalized measurements, device metadata, and routing
metadata live in `parameters`, `output`, `routingKey`, and `routeDestination`.

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

## Local H2 inspection

The `nflow` profile keeps the default local nFlow database in memory and suppresses the nFlow H2 TCP
and console listeners. That keeps normal local startup quiet and avoids binding the default nFlow H2
ports unless a debugging session needs them.

To inspect the nFlow tables from IntelliJ, opt in to the H2 TCP listener for that run:

```bash
./gradlew --console=plain bootRun --args='--spring.profiles.active=standalone,nflow,nflow.db.h2 --nflow.db.h2.tcp.port=9092'
```

Then add an H2 data source in IntelliJ with:

- JDBC URL: `jdbc:h2:tcp://localhost:9092/mem:nflow`
- User: `sa`
- Password: empty

The application ticket database is separate from the nFlow database. The default local ticket database
is the file-backed H2 data source under `./data/nflow-poc`; if IntelliJ is connected to that file while
the app starts, H2 can report a file-lock error. For isolated smoke tests, override the app data source
with an in-memory URL.

## Local smoke test

The repeatable local smoke test assumes the app is already running with the nFlow profile:

```bash
./gradlew --console=plain bootRun --args='--spring.profiles.active=standalone,nflow,nflow.db.h2'
```

In another shell, run:

```bash
BASE_URL=http://localhost:4500 scripts/nflow-smoke-local.sh
```

The script requires `curl` and `jq`. It validates the management health endpoint, starts the async REST,
blocking REST, and inbound-message execution engines, confirms each successful ticket reaches
`COMPLETED`, and checks the generic `{ name, parameters, output }` result shape. It also exercises the
blocking engine validation-failure branch.

## Result store decision

`WorkflowResultStore` should remain in-memory for the local POC. nFlow is now the durable workflow
runtime and owns workflow history plus state variables. The application result store is still only the
public ticket/result cache used by REST polling and blocking waits.

A persisted `WorkflowResultStore` is a better fit for the Docker/PostgreSQL promotion, multi-instance
runtime tests, or any scenario where API ticket results must survive application restarts.

## Near-Term Follow-Up

- [x] Document the IntelliJ H2 database connection workflow, including optional nFlow H2 TCP settings.
- [x] Add a repeatable local smoke-test script for the three starter workflows.
- [x] Decide whether `WorkflowResultStore` should remain in-memory for the POC or gain a persisted local implementation.
- [x] Reduce local nFlow/H2 log noise once the debugging workflow is comfortable.

## Backlog

- [ ] Exercise the nFlow profile against the Docker/PostgreSQL runtime path.
- [ ] Promote the stabilized local nFlow settings into Docker configuration.
- [ ] Align the Docker app port mapping with `server.port`.
- [ ] Move reusable MQTT client plumbing into `ksb-commons` after the ingress behavior stabilizes.
- [ ] Add OpenTelemetry tracing once the workflow identity model stabilizes.
