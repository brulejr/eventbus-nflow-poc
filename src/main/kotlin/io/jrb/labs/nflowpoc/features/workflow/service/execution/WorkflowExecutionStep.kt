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

data class WorkflowExecutionStep(
    val message: String,
    val stateVariables: Map<String, Any?> = emptyMap(),
    val result: Map<String, Any?>? = null,
    val failure: String? = null
) {
    val failed: Boolean
        get() = failure != null

    companion object {
        fun success(
            message: String,
            stateVariables: Map<String, Any?> = emptyMap(),
            result: Map<String, Any?>? = null
        ): WorkflowExecutionStep =
            WorkflowExecutionStep(
                message = message,
                stateVariables = stateVariables,
                result = result
            )

        fun failure(
            message: String,
            stateVariables: Map<String, Any?> = emptyMap()
        ): WorkflowExecutionStep =
            WorkflowExecutionStep(
                message = message,
                stateVariables = stateVariables,
                failure = message
            )
    }
}

