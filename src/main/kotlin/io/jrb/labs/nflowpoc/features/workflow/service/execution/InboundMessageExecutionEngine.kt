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
class InboundMessageExecutionEngine {

    fun ingest(command: WorkflowExecutionCommand): WorkflowExecutionStep =
        WorkflowExecutionStep.success(
            message = "Inbound command ingested",
            stateVariables = mapOf(
                "executionName" to command.name,
                "parameters" to command.parameters,
                "pipelineStep" to "ingested"
            )
        )

    fun inspect(command: WorkflowExecutionCommand): WorkflowExecutionStep {
        if (command.name == WorkflowExecutionCommand.UNNAMED) {
            return WorkflowExecutionStep.failure(
                message = "Execution name is required",
                stateVariables = mapOf(
                    "inspectionStatus" to "rejected",
                    "pipelineStep" to "inspection-failed"
                )
            )
        }
        if (command.failInspection) {
            return WorkflowExecutionStep.failure(
                message = "Inspection rejected by payload",
                stateVariables = mapOf(
                    "inspectionStatus" to "rejected",
                    "pipelineStep" to "inspection-failed"
                )
            )
        }
        return WorkflowExecutionStep.success(
            message = "Inbound command inspected",
            stateVariables = mapOf(
                "inspectionStatus" to "accepted",
                "pipelineStep" to "inspected"
            )
        )
    }

    fun transform(command: WorkflowExecutionCommand): WorkflowExecutionStep {
        if (command.failTransform) {
            return WorkflowExecutionStep.failure(
                message = "Transform rejected by payload",
                stateVariables = mapOf(
                    "transformStatus" to "rejected",
                    "pipelineStep" to "transform-failed"
                )
            )
        }
        return WorkflowExecutionStep.success(
            message = "Inbound command transformed",
            stateVariables = mapOf(
                "output" to command.output,
                "transformStatus" to "completed",
                "pipelineStep" to "transformed"
            )
        )
    }

    fun route(command: WorkflowExecutionCommand): WorkflowExecutionStep {
        if (command.failRoute) {
            return WorkflowExecutionStep.failure(
                message = "Route rejected by payload",
                stateVariables = mapOf(
                    "routeStatus" to "rejected",
                    "pipelineStep" to "route-failed"
                )
            )
        }
        return WorkflowExecutionStep.success(
            message = "Inbound command routed",
            stateVariables = mapOf(
                "routingKey" to routingKey(command),
                "routeDestination" to routeDestination(command),
                "routeStatus" to "accepted",
                "pipelineStep" to "routed"
            )
        )
    }

    fun completePipeline(command: WorkflowExecutionCommand): WorkflowExecutionStep =
        WorkflowExecutionStep.success(
            message = "Inbound pipeline completed",
            stateVariables = mapOf("pipelineStep" to "completed"),
            result = command.resultEnvelope(
                mapOf(
                    "routingKey" to routingKey(command),
                    "routeDestination" to routeDestination(command),
                    "workflowPath" to WORKFLOW_PATH
                )
            )
        )

    private fun routingKey(command: WorkflowExecutionCommand): String =
        command.routingKey ?: command.name

    private fun routeDestination(command: WorkflowExecutionCommand): String =
        command.routeDestination ?: "inbound.output"

    companion object {
        val WORKFLOW_PATH = listOf(
            "ingest",
            "inspect",
            "transform",
            "route",
            "completePipeline",
            "done"
        )
    }
}

