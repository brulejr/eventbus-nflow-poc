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
import io.jrb.labs.nflowpoc.features.workflow.service.execution.InboundMessageExecutionEngine
import io.jrb.labs.nflowpoc.features.workflow.store.WorkflowResultStore
import io.nflow.engine.workflow.curated.State
import io.nflow.engine.workflow.definition.NextAction
import io.nflow.engine.workflow.definition.StateExecution
import io.nflow.engine.workflow.definition.WorkflowState
import io.nflow.engine.workflow.definition.WorkflowStateType
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("nflow")
class InboundMessageWorkflow(
    objectMapper: ObjectMapper,
    resultStore: WorkflowResultStore,
    metrics: WorkflowMetrics,
    private val executionEngine: InboundMessageExecutionEngine
) : NflowWorkflowSupport(WorkflowTypes.INBOUND_MESSAGE, INGEST, ERROR, objectMapper, resultStore, metrics) {

    init {
        name = "Generic inbound pipeline execution engine"
        description = "nFlow workflow that ingests, inspects, transforms, routes, and completes a named inbound command."
        permit(INGEST, INSPECT, ERROR)
        permit(INSPECT, TRANSFORM, ERROR)
        permit(TRANSFORM, ROUTE, ERROR)
        permit(ROUTE, COMPLETE_PIPELINE, ERROR)
        permit(COMPLETE_PIPELINE, DONE, ERROR)
    }

    fun ingest(execution: StateExecution): NextAction {
        markRunning(execution)
        return executeStep(
            execution = execution,
            state = INGEST,
            nextState = INSPECT,
            errorState = ERROR
        ) {
            executionEngine.ingest(command(execution, WorkflowTypes.INBOUND_MESSAGE))
        }
    }

    fun inspect(execution: StateExecution): NextAction {
        return executeStep(
            execution = execution,
            state = INSPECT,
            nextState = TRANSFORM,
            errorState = ERROR
        ) {
            executionEngine.inspect(command(execution, WorkflowTypes.INBOUND_MESSAGE))
        }
    }

    fun transform(execution: StateExecution): NextAction {
        return executeStep(
            execution = execution,
            state = TRANSFORM,
            nextState = ROUTE,
            errorState = ERROR
        ) {
            executionEngine.transform(command(execution, WorkflowTypes.INBOUND_MESSAGE))
        }
    }

    fun route(execution: StateExecution): NextAction {
        return executeStep(
            execution = execution,
            state = ROUTE,
            nextState = COMPLETE_PIPELINE,
            errorState = ERROR
        ) {
            executionEngine.route(command(execution, WorkflowTypes.INBOUND_MESSAGE))
        }
    }

    fun completePipeline(execution: StateExecution): NextAction {
        return executeStep(
            execution = execution,
            state = COMPLETE_PIPELINE,
            nextState = DONE,
            errorState = ERROR
        ) {
            executionEngine.completePipeline(command(execution, WorkflowTypes.INBOUND_MESSAGE))
        }
    }

    companion object {
        const val TYPE: String = WorkflowTypes.INBOUND_MESSAGE

        @JvmField
        val INGEST: WorkflowState = State("ingest", WorkflowStateType.start, "Ingest inbound command")

        @JvmField
        val INSPECT: WorkflowState = State("inspect", "Inspect inbound command")

        @JvmField
        val TRANSFORM: WorkflowState = State("transform", "Transform inbound command")

        @JvmField
        val ROUTE: WorkflowState = State("route", "Route inbound output")

        @JvmField
        val COMPLETE_PIPELINE: WorkflowState = State("completePipeline", "Complete inbound pipeline")

        @JvmField
        val DONE: WorkflowState = State("done", WorkflowStateType.end, "Ticket completed")

        @JvmField
        val ERROR: WorkflowState = State("error", WorkflowStateType.manual, "Manual recovery required")

    }
}
