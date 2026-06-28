/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 Jon Brule
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.jrb.labs.nflowpoc.features.workflow.service.nflow

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.jrb.labs.nflowpoc.features.workflow.metrics.WorkflowMetrics
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowSource
import io.jrb.labs.nflowpoc.features.workflow.service.execution.WorkflowExecutionCommand
import io.jrb.labs.nflowpoc.features.workflow.service.execution.WorkflowExecutionStep
import io.jrb.labs.nflowpoc.features.workflow.store.WorkflowResultStore
import io.nflow.engine.workflow.definition.NextAction
import io.nflow.engine.workflow.definition.StateExecution
import io.nflow.engine.workflow.definition.WorkflowDefinition
import io.nflow.engine.workflow.definition.WorkflowState
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

abstract class NflowWorkflowSupport(
    workflowType: String,
    initialState: WorkflowState,
    errorState: WorkflowState,
    protected val objectMapper: ObjectMapper,
    private val resultStore: WorkflowResultStore,
    private val metrics: WorkflowMetrics
) : WorkflowDefinition(workflowType, initialState, errorState) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val payloadType = object : TypeReference<Map<String, Any?>>() {}

    protected fun payload(execution: StateExecution): Map<String, Any?> {
        val payloadJson = execution.getVariable(NflowVars.PAYLOAD_JSON, "{}")
        return runCatching {
            objectMapper.readValue(payloadJson, payloadType)
        }.getOrElse { ex ->
            log.warn("Failed to parse workflow payload instanceId={}", execution.workflowInstanceId, ex)
            emptyMap()
        }
    }

    protected fun command(
        execution: StateExecution,
        workflowType: String,
        defaultSteps: List<String> = emptyList()
    ): WorkflowExecutionCommand =
        WorkflowExecutionCommand.fromPayload(
            workflowType = workflowType,
            ticketId = ticketId(execution),
            correlationId = correlationId(execution),
            source = source(execution),
            payload = payload(execution),
            defaultSteps = defaultSteps
        )

    protected fun executeStep(
        execution: StateExecution,
        state: WorkflowState,
        nextState: WorkflowState,
        errorState: WorkflowState,
        block: () -> WorkflowExecutionStep
    ): NextAction {
        val startedAt = Instant.now()
        val stepName = state.name()
        val source = source(execution)
        val retryNo = execution.retries
        metrics.workflowStepStarted(type, source, stepName)
        if (retryNo > 0) {
            metrics.workflowStepRetried(type, source, stepName, retryNo)
        }
        log.info(
            "Workflow step started workflowType={} step={} ticketId={} correlationId={} source={} nflowInstanceId={} retryNo={}",
            type,
            stepName,
            ticketId(execution),
            correlationId(execution),
            source,
            execution.workflowInstanceId,
            retryNo
        )

        val step = try {
            block()
        } catch (ex: Exception) {
            val duration = Duration.between(startedAt, Instant.now())
            metrics.workflowStepFailed(type, source, stepName, retryNo, duration)
            log.error(
                "Workflow step failed workflowType={} step={} ticketId={} correlationId={} source={} nflowInstanceId={} retryNo={} durationMs={} failureType={}",
                type,
                stepName,
                ticketId(execution),
                correlationId(execution),
                source,
                execution.workflowInstanceId,
                retryNo,
                duration.toMillis(),
                ex.javaClass.simpleName,
                ex
            )
            throw ex
        }

        val duration = Duration.between(startedAt, Instant.now())
        if (step.failed) {
            metrics.workflowStepFailed(type, source, stepName, retryNo, duration)
            log.warn(
                "Workflow step failed workflowType={} step={} ticketId={} correlationId={} source={} nflowInstanceId={} retryNo={} durationMs={} message={}",
                type,
                stepName,
                ticketId(execution),
                correlationId(execution),
                source,
                execution.workflowInstanceId,
                retryNo,
                duration.toMillis(),
                step.failure ?: step.message
            )
        } else {
            metrics.workflowStepSucceeded(type, source, stepName, retryNo, duration)
            log.info(
                "Workflow step succeeded workflowType={} step={} ticketId={} correlationId={} source={} nflowInstanceId={} retryNo={} durationMs={} message={}",
                type,
                stepName,
                ticketId(execution),
                correlationId(execution),
                source,
                execution.workflowInstanceId,
                retryNo,
                duration.toMillis(),
                step.message
            )
        }
        return applyStep(execution, step, nextState, errorState)
    }

    private fun applyStep(
        execution: StateExecution,
        step: WorkflowExecutionStep,
        nextState: WorkflowState,
        errorState: WorkflowState
    ): NextAction {
        step.stateVariables.forEach { (key, value) ->
            value?.let { execution.setVariable(key, stateVariableValue(it)) }
        }
        if (step.failed) {
            val result = resultStore.markFailed(ticketId(execution), step.failure ?: step.message)
            metrics.workflowFailed(type, result.source, Duration.between(result.createdAt, result.updatedAt))
            log.warn(
                "Workflow failed workflowType={} ticketId={} correlationId={} source={} nflowInstanceId={} durationMs={} message={}",
                type,
                result.ticketId,
                result.correlationId,
                result.source,
                execution.workflowInstanceId,
                Duration.between(result.createdAt, result.updatedAt).toMillis(),
                step.failure ?: step.message
            )
            return NextAction.stopInState(errorState, step.message)
        }
        step.result?.let {
            val result = resultStore.markCompleted(ticketId(execution), it)
            metrics.workflowCompleted(type, result.source, Duration.between(result.createdAt, result.updatedAt))
            log.info(
                "Workflow completed workflowType={} ticketId={} correlationId={} source={} nflowInstanceId={} durationMs={}",
                type,
                result.ticketId,
                result.correlationId,
                result.source,
                execution.workflowInstanceId,
                Duration.between(result.createdAt, result.updatedAt).toMillis()
            )
        }
        return NextAction.moveToState(nextState, step.message)
    }

    protected fun ticketId(execution: StateExecution): String =
        execution.getVariable(NflowVars.TICKET_ID, execution.workflowInstanceExternalId)

    protected fun source(execution: StateExecution): WorkflowSource =
        runCatching { WorkflowSource.valueOf(execution.getVariable(NflowVars.SOURCE, WorkflowSource.IN_MEMORY.name)) }
            .getOrDefault(WorkflowSource.IN_MEMORY)

    protected fun correlationId(execution: StateExecution): String =
        execution.getVariable(NflowVars.CORRELATION_ID, "")

    protected fun markRunning(execution: StateExecution) {
        resultStore.markRunning(ticketId(execution))
        log.info(
            "Workflow running workflowType={} ticketId={} correlationId={} source={} nflowInstanceId={}",
            type,
            ticketId(execution),
            correlationId(execution),
            source(execution),
            execution.workflowInstanceId
        )
    }

    protected fun complete(execution: StateExecution, result: Map<String, Any?>) {
        val workflow = resultStore.markCompleted(ticketId(execution), result)
        metrics.workflowCompleted(type, workflow.source, Duration.between(workflow.createdAt, workflow.updatedAt))
        log.info(
            "Workflow completed workflowType={} ticketId={} correlationId={} source={} nflowInstanceId={} durationMs={}",
            type,
            workflow.ticketId,
            workflow.correlationId,
            workflow.source,
            execution.workflowInstanceId,
            Duration.between(workflow.createdAt, workflow.updatedAt).toMillis()
        )
    }

    protected fun failAndStop(execution: StateExecution, errorState: WorkflowState, message: String): NextAction {
        val workflow = resultStore.markFailed(ticketId(execution), message)
        metrics.workflowFailed(type, workflow.source, Duration.between(workflow.createdAt, workflow.updatedAt))
        log.warn(
            "Workflow failed workflowType={} ticketId={} correlationId={} source={} nflowInstanceId={} durationMs={} message={}",
            type,
            workflow.ticketId,
            workflow.correlationId,
            workflow.source,
            execution.workflowInstanceId,
            Duration.between(workflow.createdAt, workflow.updatedAt).toMillis(),
            message
        )
        return NextAction.stopInState(errorState, message)
    }

    private fun stateVariableValue(value: Any): Any =
        when (value) {
            is String,
            is Number,
            is Boolean -> value
            else -> runCatching { objectMapper.writeValueAsString(value) }.getOrDefault(value.toString())
        }
}
