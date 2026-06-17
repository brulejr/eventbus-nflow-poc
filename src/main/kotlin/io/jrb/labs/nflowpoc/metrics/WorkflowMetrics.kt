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

package io.jrb.labs.nflowpoc.metrics

import io.jrb.labs.nflowpoc.workflow.WorkflowSource
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class WorkflowMetrics(
    private val meterRegistry: MeterRegistry
) {
    fun workflowStarted(workflowType: String, source: WorkflowSource) {
        meterRegistry.counter("workflow.started", "workflowType", workflowType, "source", source.name).increment()
    }

    fun workflowCompleted(workflowType: String, source: WorkflowSource, duration: Duration) {
        meterRegistry.counter("workflow.completed", "workflowType", workflowType, "source", source.name).increment()
        meterRegistry.timer("workflow.duration", "workflowType", workflowType, "source", source.name).record(duration)
    }

    fun workflowFailed(workflowType: String, source: WorkflowSource) {
        meterRegistry.counter("workflow.failed", "workflowType", workflowType, "source", source.name).increment()
    }

    fun ingressReceived(source: WorkflowSource, workflowType: String) {
        meterRegistry.counter("workflow.ingress.received", "workflowType", workflowType, "source", source.name).increment()
    }
}
