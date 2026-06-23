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

import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowSource

data class WorkflowExecutionCommand(
    val workflowType: String,
    val ticketId: String,
    val correlationId: String,
    val source: WorkflowSource,
    val name: String,
    val parameters: Map<String, Any?>,
    val output: Any?,
    val steps: List<String>,
    val definitionSteps: List<Map<String, Any?>>,
    val routingKey: String?,
    val routeDestination: String?,
    val failValidation: Boolean,
    val failPreparation: Boolean,
    val failExecution: Boolean,
    val failInspection: Boolean,
    val failTransform: Boolean,
    val failRoute: Boolean
) {
    fun resultEnvelope(extra: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val envelope = linkedMapOf<String, Any?>(
            "engine" to workflowType,
            "workflowType" to workflowType,
            "ticketId" to ticketId,
            "correlationId" to correlationId,
            "source" to source.name,
            "name" to name,
            "parameters" to parameters,
            "output" to output
        )
        if (definitionSteps.isNotEmpty()) {
            envelope["definitionSteps"] = definitionSteps
        }
        return envelope + extra
    }

    companion object {
        fun fromPayload(
            workflowType: String,
            ticketId: String,
            correlationId: String,
            source: WorkflowSource,
            payload: Map<String, Any?>,
            defaultSteps: List<String> = emptyList()
        ): WorkflowExecutionCommand {
            return WorkflowExecutionCommand(
                workflowType = workflowType,
                ticketId = ticketId,
                correlationId = correlationId,
                source = source,
                name = payload["name"]?.toString()?.takeIf { it.isNotBlank() } ?: UNNAMED,
                parameters = mapValue(payload["parameters"])
                    ?: payload.filterKeys { it !in RESERVED_PAYLOAD_KEYS },
                output = payload["output"] ?: mapValue(payload["parameters"])
                    ?: payload.filterKeys { it !in RESERVED_PAYLOAD_KEYS },
                steps = listValue(payload["steps"]).ifEmpty { defaultSteps },
                definitionSteps = mapListValue(payload["definitionSteps"]),
                routingKey = payload["routingKey"]?.toString()?.takeIf { it.isNotBlank() },
                routeDestination = payload["routeDestination"]?.toString()?.takeIf { it.isNotBlank() },
                failValidation = payload["failValidation"] == true,
                failPreparation = payload["failPreparation"] == true,
                failExecution = payload["failExecution"] == true,
                failInspection = payload["failInspection"] == true,
                failTransform = payload["failTransform"] == true,
                failRoute = payload["failRoute"] == true
            )
        }

        const val UNNAMED: String = "unnamed-execution"

        private val RESERVED_PAYLOAD_KEYS = setOf(
            "name",
            "parameters",
            "output",
            "steps",
            "definitionSteps",
            "routingKey",
            "routeDestination",
            "failValidation",
            "failPreparation",
            "failExecution",
            "failInspection",
            "failTransform",
            "failRoute"
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
    }
}
