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
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowSource
import io.jrb.labs.nflowpoc.features.workflow.store.WorkflowResultStore
import io.nflow.engine.workflow.definition.NextAction
import io.nflow.engine.workflow.definition.StateExecution
import io.nflow.engine.workflow.definition.WorkflowDefinition
import io.nflow.engine.workflow.definition.WorkflowState
import org.slf4j.LoggerFactory

abstract class NflowWorkflowSupport(
    workflowType: String,
    initialState: WorkflowState,
    errorState: WorkflowState,
    protected val objectMapper: ObjectMapper,
    private val resultStore: WorkflowResultStore
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

    protected fun ticketId(execution: StateExecution): String =
        execution.getVariable(NflowVars.TICKET_ID, execution.workflowInstanceExternalId)

    protected fun source(execution: StateExecution): WorkflowSource =
        runCatching { WorkflowSource.valueOf(execution.getVariable(NflowVars.SOURCE, WorkflowSource.IN_MEMORY.name)) }
            .getOrDefault(WorkflowSource.IN_MEMORY)

    protected fun correlationId(execution: StateExecution): String =
        execution.getVariable(NflowVars.CORRELATION_ID, "")

    protected fun markRunning(execution: StateExecution) {
        resultStore.markRunning(ticketId(execution))
    }

    protected fun complete(execution: StateExecution, result: Map<String, Any?>) {
        resultStore.markCompleted(ticketId(execution), result)
    }

    protected fun failAndStop(execution: StateExecution, errorState: WorkflowState, message: String): NextAction {
        resultStore.markFailed(ticketId(execution), message)
        return NextAction.stopInState(errorState, message)
    }
}
