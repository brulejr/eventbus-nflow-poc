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
import io.jrb.labs.nflowpoc.features.workflow.service.execution.BlockingRestExecutionEngine
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
class BlockingRestWorkflow(
    objectMapper: ObjectMapper,
    resultStore: WorkflowResultStore,
    metrics: WorkflowMetrics,
    private val executionEngine: BlockingRestExecutionEngine
) : NflowWorkflowSupport(WorkflowTypes.BLOCKING_REST, BEGIN, ERROR, objectMapper, resultStore, metrics) {

    init {
        name = "Generic blocking multi-step execution engine"
        description = "Multi-step nFlow workflow that validates, prepares, executes, collects output, and completes a named command."
        permit(BEGIN, VALIDATE, ERROR)
        permit(VALIDATE, PREPARE, ERROR)
        permit(PREPARE, EXECUTE, ERROR)
        permit(EXECUTE, COLLECT_OUTPUT, ERROR)
        permit(COLLECT_OUTPUT, COMPLETE_EXECUTION, ERROR)
        permit(COMPLETE_EXECUTION, DONE, ERROR)
    }

    @Suppress("unused")
    fun begin(execution: StateExecution): NextAction {
        markRunning(execution)
        return executeStep(
            execution = execution,
            state = BEGIN,
            nextState = VALIDATE,
            errorState = ERROR,
            defaultSteps = BlockingRestExecutionEngine.DEFAULT_STEPS,
            block = executionEngine::begin
        )
    }

    @Suppress("unused")
    fun validate(execution: StateExecution): NextAction {
        return executeStep(
            execution = execution,
            state = VALIDATE,
            nextState = PREPARE,
            errorState = ERROR,
            defaultSteps = BlockingRestExecutionEngine.DEFAULT_STEPS,
            block = executionEngine::validate
        )
    }

    @Suppress("unused")
    fun prepare(execution: StateExecution): NextAction {
        return executeStep(
            execution = execution,
            state = PREPARE,
            nextState = EXECUTE,
            errorState = ERROR,
            defaultSteps = BlockingRestExecutionEngine.DEFAULT_STEPS,
            block = executionEngine::prepare
        )
    }

    @Suppress("unused")
    fun execute(execution: StateExecution): NextAction {
        return executeStep(
            execution = execution,
            state = EXECUTE,
            nextState = COLLECT_OUTPUT,
            errorState = ERROR,
            defaultSteps = BlockingRestExecutionEngine.DEFAULT_STEPS,
            block = executionEngine::execute
        )
    }

    @Suppress("unused")
    fun collectOutput(execution: StateExecution): NextAction {
        return executeStep(
            execution = execution,
            state = COLLECT_OUTPUT,
            nextState = COMPLETE_EXECUTION,
            errorState = ERROR,
            defaultSteps = BlockingRestExecutionEngine.DEFAULT_STEPS,
            block = executionEngine::collectOutput
        )
    }

    @Suppress("unused")
    fun completeExecution(execution: StateExecution): NextAction {
        return executeStep(
            execution = execution,
            state = COMPLETE_EXECUTION,
            nextState = DONE,
            errorState = ERROR,
            defaultSteps = BlockingRestExecutionEngine.DEFAULT_STEPS,
            block = executionEngine::completeExecution
        )
    }

    companion object {
        @JvmField
        val BEGIN: WorkflowState = State("begin", WorkflowStateType.start, "Accept the request")

        @JvmField
        val VALIDATE: WorkflowState = State("validate", "Validate payload")

        @JvmField
        val PREPARE: WorkflowState = State("prepare", "Prepare execution")

        @JvmField
        val EXECUTE: WorkflowState = State("execute", "Execute command")

        @JvmField
        val COLLECT_OUTPUT: WorkflowState = State("collectOutput", "Collect output")

        @JvmField
        val COMPLETE_EXECUTION: WorkflowState = State("completeExecution", "Complete execution")

        @JvmField
        val DONE: WorkflowState = State("done", WorkflowStateType.end, "Ticket completed")

        @JvmField
        val ERROR: WorkflowState = State("error", WorkflowStateType.manual, "Manual recovery required")

    }
}
