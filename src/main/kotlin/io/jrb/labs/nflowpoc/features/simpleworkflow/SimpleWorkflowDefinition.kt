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

package io.jrb.labs.nflowpoc.features.simpleworkflow

import io.jrb.labs.nflowpoc.features.workflow.definition.WorkflowDefinitionSpec
import io.jrb.labs.nflowpoc.features.workflow.definition.WorkflowDefinitionStep
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowTypes

class SimpleWorkflowDefinition(
    datafill: SimpleWorkflowDatafill,
    echoInputStep: EchoInputStep
) : WorkflowDefinitionSpec {
    override val id: String = "simple"
    override val description: String = "One-step starter definition that echoes command parameters."
    override val engineWorkflowType: String = WorkflowTypes.ASYNC_REST
    override val steps: List<WorkflowDefinitionStep> = listOf(echoInputStep)

    override fun expand(payload: Map<String, Any?>): Map<String, Any?> {
        val parameters = parameters(payload)
        val context = executeSteps(mapOf("parameters" to parameters))
        return mapOf(
            "name" to id,
            "parameters" to parameters,
            "steps" to steps.map { it.id },
            "definitionSteps" to steps.map { it.toPayload() },
            "output" to (payload["output"] ?: context.filterKeys { it in outputKeys })
        )
    }

    private fun parameters(payload: Map<String, Any?>): Map<String, Any?> =
        mapValue(payload["parameters"]) ?: payload.filterKeys { it !in RESERVED_KEYS }

    @Suppress("UNCHECKED_CAST")
    private fun mapValue(value: Any?): Map<String, Any?>? =
        value as? Map<String, Any?>

    companion object {
        private val RESERVED_KEYS = setOf("definition", "workflowDefinition", "name", "output", "steps", "definitionSteps")
        private val outputKeys = setOf("accepted", "echo")
    }
}
