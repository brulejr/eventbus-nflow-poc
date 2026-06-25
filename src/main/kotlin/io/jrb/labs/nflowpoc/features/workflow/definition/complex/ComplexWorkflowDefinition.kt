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

package io.jrb.labs.nflowpoc.features.workflow.definition.complex

import io.jrb.labs.nflowpoc.features.workflow.definition.WorkflowDefinitionSpec
import io.jrb.labs.nflowpoc.features.workflow.definition.WorkflowDefinitionStep
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowTypes

class ComplexWorkflowDefinition(
    validateRequestStep: ValidateRequestStep,
    prepareExecutionStep: PrepareExecutionStep,
    executeWorkStep: ExecuteWorkStep,
    collectOutputStep: CollectOutputStep
) : WorkflowDefinitionSpec {
    override val id: String = "complex"
    override val description: String = "Multi-step starter definition backed by the blocking execution engine."
    override val engineWorkflowType: String = WorkflowTypes.BLOCKING_REST
    override val steps: List<WorkflowDefinitionStep> = listOf(
        validateRequestStep,
        prepareExecutionStep,
        executeWorkStep,
        collectOutputStep
    )

    override fun expand(payload: Map<String, Any?>): Map<String, Any?> {
        val parameters = parameters(payload)
        val context = executeSteps(mapOf("parameters" to parameters))
        return mapOf(
            "name" to id,
            "parameters" to parameters,
            "steps" to stepIds(payload),
            "definitionSteps" to definitionSteps(payload),
            "output" to (payload["output"] ?: context.filterKeys { it in outputKeys }),
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
        private val outputKeys = setOf("status", "parameters")
    }
}
