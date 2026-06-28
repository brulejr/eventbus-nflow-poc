/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 Jon Brule <brulejr@gmail.com>
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

import com.fasterxml.jackson.databind.ObjectMapper
import io.jrb.labs.nflowpoc.features.workflow.metrics.WorkflowMetrics
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowTypes
import io.jrb.labs.nflowpoc.features.workflow.service.execution.AsyncRestExecutionEngine
import io.jrb.labs.nflowpoc.features.workflow.store.WorkflowResultStore
import io.nflow.engine.workflow.curated.State
import io.nflow.engine.workflow.definition.NextAction
import io.nflow.engine.workflow.definition.StateExecution
import io.nflow.engine.workflow.definition.WorkflowState
import io.nflow.engine.workflow.definition.WorkflowStateType

class AsyncRestWorkflow(
    objectMapper: ObjectMapper,
    resultStore: WorkflowResultStore,
    metrics: WorkflowMetrics,
    private val executionEngine: AsyncRestExecutionEngine
) : NflowWorkflowSupport(WorkflowTypes.ASYNC_REST, BEGIN, ERROR, objectMapper, resultStore, metrics) {

    init {
        name = "Generic async execution engine"
        description = "Minimal nFlow workflow that executes a named command with parameters and output."
        permit(BEGIN, DONE, ERROR)
    }

    @Suppress("unused")
    fun begin(execution: StateExecution): NextAction {
        markRunning(execution)
        return executeStep(
            execution = execution,
            state = BEGIN,
            nextState = DONE,
            errorState = ERROR,
            block = executionEngine::execute
        )
    }

    companion object {
        @JvmField
        val BEGIN: WorkflowState = State("begin", WorkflowStateType.start, "Accept the request")

        @JvmField
        val DONE: WorkflowState = State("done", WorkflowStateType.end, "Ticket completed")

        @JvmField
        val ERROR: WorkflowState = State("error", WorkflowStateType.manual, "Manual recovery required")
    }
}
