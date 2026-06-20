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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.jrb.labs.nflowpoc.features.workflow.service.execution

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("nflow")
class BlockingRestExecutionEngine {

    fun begin(command: WorkflowExecutionCommand): WorkflowExecutionStep =
        WorkflowExecutionStep.success(
            message = "Request accepted",
            stateVariables = mapOf(
                "executionName" to command.name,
                "engineStep" to "received"
            )
        )

    fun validate(command: WorkflowExecutionCommand): WorkflowExecutionStep {
        if (command.name == WorkflowExecutionCommand.UNNAMED) {
            return WorkflowExecutionStep.failure(
                message = "Execution name is required",
                stateVariables = mapOf(
                    "validationStatus" to "rejected",
                    "engineStep" to "validation-failed"
                )
            )
        }
        if (command.failValidation) {
            return WorkflowExecutionStep.failure(
                message = "Validation rejected by payload",
                stateVariables = mapOf(
                    "validationStatus" to "rejected",
                    "engineStep" to "validation-failed"
                )
            )
        }
        return WorkflowExecutionStep.success(
            message = "Validated ${command.name}",
            stateVariables = mapOf(
                "parameters" to command.parameters,
                "steps" to command.steps,
                "validationStatus" to "accepted",
                "engineStep" to "validated"
            )
        )
    }

    fun prepare(command: WorkflowExecutionCommand): WorkflowExecutionStep {
        if (command.failPreparation) {
            return WorkflowExecutionStep.failure(
                message = "Preparation rejected by payload",
                stateVariables = mapOf(
                    "preparationStatus" to "rejected",
                    "engineStep" to "preparation-failed"
                )
            )
        }
        return WorkflowExecutionStep.success(
            message = "Prepared execution",
            stateVariables = mapOf(
                "preparationStatus" to "ready",
                "engineStep" to "prepared"
            )
        )
    }

    fun execute(command: WorkflowExecutionCommand): WorkflowExecutionStep {
        if (command.failExecution) {
            return WorkflowExecutionStep.failure(
                message = "Execution rejected by payload",
                stateVariables = mapOf(
                    "executionStatus" to "failed",
                    "engineStep" to "execution-failed"
                )
            )
        }
        return WorkflowExecutionStep.success(
            message = "Execution completed",
            stateVariables = mapOf(
                "executionStatus" to "completed",
                "engineStep" to "executed"
            )
        )
    }

    fun collectOutput(command: WorkflowExecutionCommand): WorkflowExecutionStep =
        WorkflowExecutionStep.success(
            message = "Output collected",
            stateVariables = mapOf(
                "output" to command.output,
                "engineStep" to "output-collected"
            )
        )

    fun completeExecution(command: WorkflowExecutionCommand): WorkflowExecutionStep =
        WorkflowExecutionStep.success(
            message = "Execution result emitted",
            stateVariables = mapOf("engineStep" to "completed"),
            result = command.resultEnvelope(
                mapOf(
                    "steps" to command.steps,
                    "workflowPath" to WORKFLOW_PATH
                )
            )
        )

    companion object {
        val DEFAULT_STEPS = listOf("validate", "prepare", "execute", "collectOutput")

        val WORKFLOW_PATH = listOf(
            "begin",
            "validate",
            "prepare",
            "execute",
            "collectOutput",
            "completeExecution",
            "done"
        )
    }
}

