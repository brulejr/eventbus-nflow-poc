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

package io.jrb.labs.nflowpoc.features.workflow.metrics

import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowSource
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration

class WorkflowMetrics(
    private val meterRegistry: MeterRegistry
) {
    fun workflowStarted(workflowType: String, source: WorkflowSource) {
        meterRegistry.counter("workflow.started", "workflowType", workflowType, "source", source.name).increment()
    }

    fun workflowCompleted(workflowType: String, source: WorkflowSource, duration: Duration) {
        meterRegistry.counter("workflow.completed", "workflowType", workflowType, "source", source.name).increment()
        recordWorkflowDuration(workflowType, source, "success", duration)
    }

    fun workflowFailed(workflowType: String, source: WorkflowSource) {
        meterRegistry.counter("workflow.failed", "workflowType", workflowType, "source", source.name).increment()
    }

    fun workflowFailed(workflowType: String, source: WorkflowSource, duration: Duration) {
        workflowFailed(workflowType, source)
        recordWorkflowDuration(workflowType, source, "failure", duration)
    }

    fun ingressReceived(source: WorkflowSource, workflowType: String) {
        meterRegistry.counter("workflow.ingress.received", "workflowType", workflowType, "source", source.name).increment()
    }

    fun workflowStepStarted(workflowType: String, source: WorkflowSource, step: String) {
        meterRegistry.counter(
            "workflow.step.started",
            "workflowType", workflowType,
            "source", source.name,
            "step", step
        ).increment()
    }

    fun workflowStepSucceeded(workflowType: String, source: WorkflowSource, step: String, retryNo: Int, duration: Duration) {
        meterRegistry.counter(
            "workflow.step.succeeded",
            "workflowType", workflowType,
            "source", source.name,
            "step", step
        ).increment()
        recordStepDuration(workflowType, source, step, "success", retryNo, duration)
    }

    fun workflowStepFailed(workflowType: String, source: WorkflowSource, step: String, retryNo: Int, duration: Duration) {
        meterRegistry.counter(
            "workflow.step.failed",
            "workflowType", workflowType,
            "source", source.name,
            "step", step
        ).increment()
        recordStepDuration(workflowType, source, step, "failure", retryNo, duration)
    }

    fun workflowStepRetried(workflowType: String, source: WorkflowSource, step: String, retryNo: Int) {
        meterRegistry.counter(
            "workflow.step.retry",
            "workflowType", workflowType,
            "source", source.name,
            "step", step
        ).increment()
        meterRegistry.summary(
            "workflow.step.retry.count",
            "workflowType", workflowType,
            "source", source.name,
            "step", step
        ).record(retryNo.toDouble())
    }

    private fun recordWorkflowDuration(
        workflowType: String,
        source: WorkflowSource,
        outcome: String,
        duration: Duration
    ) {
        meterRegistry.timer(
            "workflow.duration",
            "workflowType", workflowType,
            "source", source.name,
            "outcome", outcome
        ).record(duration)
    }

    private fun recordStepDuration(
        workflowType: String,
        source: WorkflowSource,
        step: String,
        outcome: String,
        retryNo: Int,
        duration: Duration
    ) {
        meterRegistry.timer(
            "workflow.step.duration",
            "workflowType", workflowType,
            "source", source.name,
            "step", step,
            "outcome", outcome,
            "retried", (retryNo > 0).toString()
        ).record(duration)
    }
}
