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
class SimpleWorkflowDefinition : WorkflowDefinitionSpec {
    override val id: String = "simple"
    override val description: String = "One-step starter definition that echoes command parameters."
    override val engineWorkflowType: String = WorkflowTypes.ASYNC_REST
    override val steps: List<WorkflowDefinitionStep> = listOf(
        WorkflowDefinitionStep(
            id = "echo-input",
            description = "Accept the caller parameters and echo them in the workflow output.",
            inputKeys = listOf("parameters"),
            outputKeys = listOf("accepted", "echo")
        )
    )

    override fun expand(payload: Map<String, Any?>): Map<String, Any?> {
        val parameters = parameters(payload)
        return mapOf(
            "name" to id,
            "parameters" to parameters,
            "steps" to steps.map { it.id },
            "definitionSteps" to steps.map { it.toPayload() },
            "output" to (payload["output"] ?: mapOf("accepted" to true, "echo" to parameters))
        )
    }

    private fun parameters(payload: Map<String, Any?>): Map<String, Any?> =
        mapValue(payload["parameters"]) ?: payload.filterKeys { it !in RESERVED_KEYS }

    @Suppress("UNCHECKED_CAST")
    private fun mapValue(value: Any?): Map<String, Any?>? =
        value as? Map<String, Any?>

    companion object {
        private val RESERVED_KEYS = setOf("definition", "workflowDefinition", "name", "output", "steps", "definitionSteps")
    }
}
