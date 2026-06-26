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

package io.jrb.labs.nflowpoc.features.complexworkflow

import io.jrb.labs.nflowpoc.features.workflow.definition.WorkflowDefinitionStep

class CollectOutputStep(
    private val datafill: ComplexWorkflowDatafill
) : WorkflowDefinitionStep {
    override val id: String = "collect-output"
    override val description: String = "Collect the final workflow output and make it available to ticket callers."
    override val inputKeys: List<String> = listOf("output")
    override val outputKeys: List<String> = listOf("status", "parameters")

    override fun execute(input: Map<String, Any?>): Map<String, Any?> {
        val parameters = mapValue(input["parameters"]) ?: emptyMap()
        return mapOf(
            "status" to "accepted",
            "parameters" to parameters
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapValue(value: Any?): Map<String, Any?>? =
        value as? Map<String, Any?>
}
