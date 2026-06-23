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

package io.jrb.labs.nflowpoc.features.workflow.definition

import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowTypes
import org.springframework.stereotype.Component

@Component
class ComplexWorkflowDefinition : WorkflowDefinitionSpec {
    override val id: String = "complex"
    override val description: String = "Multi-step starter definition backed by the blocking execution engine."
    override val engineWorkflowType: String = WorkflowTypes.BLOCKING_REST
    override val steps: List<WorkflowDefinitionStep> = listOf(
        WorkflowDefinitionStep(
            id = "validate-request",
            description = "Validate that the request can be accepted by the workflow definition.",
            inputKeys = listOf("parameters"),
            outputKeys = listOf("validationStatus")
        ),
        WorkflowDefinitionStep(
            id = "prepare-execution",
            description = "Prepare the execution context and derive the work plan.",
            inputKeys = listOf("parameters"),
            outputKeys = listOf("preparationStatus")
        ),
        WorkflowDefinitionStep(
            id = "execute-work",
            description = "Run the logical unit of work represented by the parameters.",
            inputKeys = listOf("parameters"),
            outputKeys = listOf("executionStatus")
        ),
        WorkflowDefinitionStep(
            id = "collect-output",
            description = "Collect the final workflow output and make it available to ticket callers.",
            inputKeys = listOf("output"),
            outputKeys = listOf("status", "parameters")
        )
    )

    override fun expand(payload: Map<String, Any?>): Map<String, Any?> {
        val parameters = parameters(payload)
        return mapOf(
            "name" to id,
            "parameters" to parameters,
            "steps" to stepIds(payload),
            "definitionSteps" to definitionSteps(payload),
            "output" to (payload["output"] ?: defaultOutput(parameters)),
            "failValidation" to (payload["failValidation"] == true),
            "failPreparation" to (payload["failPreparation"] == true),
            "failExecution" to (payload["failExecution"] == true)
        )
    }

    private fun parameters(payload: Map<String, Any?>): Map<String, Any?> =
        mapValue(payload["parameters"]) ?: payload.filterKeys { it !in RESERVED_KEYS }

    private fun stepIds(payload: Map<String, Any?>): List<String> =
        listValue(payload["steps"]).ifEmpty { steps.map { it.id } }

    private fun definitionSteps(payload: Map<String, Any?>): List<Map<String, Any?>> =
        mapListValue(payload["definitionSteps"]).ifEmpty { steps.map { it.toPayload() } }

    private fun defaultOutput(parameters: Map<String, Any?>): Map<String, Any?> =
        mapOf(
            "status" to "accepted",
            "parameters" to parameters
        )

    @Suppress("UNCHECKED_CAST")
    private fun mapValue(value: Any?): Map<String, Any?>? =
        value as? Map<String, Any?>

    private fun listValue(value: Any?): List<String> =
        (value as? List<*>)
            ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun mapListValue(value: Any?): List<Map<String, Any?>> =
        value as? List<Map<String, Any?>> ?: emptyList()

    companion object {
        private val RESERVED_KEYS = setOf(
            "definition",
            "workflowDefinition",
            "name",
            "output",
            "steps",
            "definitionSteps",
            "failValidation",
            "failPreparation",
            "failExecution"
        )
    }
}
