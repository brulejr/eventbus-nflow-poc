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

package io.jrb.labs.nflowpoc.features.workflow.service

import io.jrb.labs.nflowpoc.features.workflow.WorkflowEngineDatafill
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowEngineHandle
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowStartCommand
import io.jrb.labs.nflowpoc.features.workflow.store.WorkflowResultStore
import io.jrb.labs.nflowpoc.features.workflow.metrics.WorkflowMetrics
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Component
@Profile("!nflow")
class SimulatedWorkflowEngineAdapter(
    private val resultStore: WorkflowResultStore,
    private val metrics: WorkflowMetrics,
    private val datafill: WorkflowEngineDatafill
) : WorkflowEngineAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun start(command: WorkflowStartCommand, ticketId: String): WorkflowEngineHandle {
        val engineInstanceId = "sim-${UUID.randomUUID()}"

        CompletableFuture.runAsync {
            val started = Instant.now()
            try {
                resultStore.markRunning(ticketId)
                Thread.sleep(datafill.simulatedProcessingDelay.toMillis())

                val result = mapOf(
                    "message" to "Workflow completed by simulated engine",
                    "workflowType" to command.workflowType,
                    "source" to command.source.name,
                    "correlationId" to command.correlationId,
                    "payloadEcho" to command.payload
                )

                resultStore.markCompleted(ticketId, result)
                metrics.workflowCompleted(command.workflowType, command.source, Duration.between(started, Instant.now()))
            } catch (ex: Exception) {
                log.error("Simulated workflow failed ticketId={}", ticketId, ex)
                resultStore.markFailed(ticketId, ex.message ?: ex.javaClass.name)
                metrics.workflowFailed(command.workflowType, command.source)
            }
        }

        return WorkflowEngineHandle(engineInstanceId)
    }

}