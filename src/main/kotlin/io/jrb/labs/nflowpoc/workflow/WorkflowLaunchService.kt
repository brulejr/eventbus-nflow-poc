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

package io.jrb.labs.nflowpoc.workflow

import io.jrb.labs.nflowpoc.metrics.WorkflowMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class WorkflowLaunchService(
    private val workflowEngineAdapter: WorkflowEngineAdapter,
    private val resultStore: WorkflowResultStore,
    private val metrics: WorkflowMetrics
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun startAsync(command: WorkflowStartCommand): WorkflowTicket {
        log.info("Starting workflow type={} source={} correlationId={}", command.workflowType, command.source, command.correlationId)
        metrics.workflowStarted(command.workflowType, command.source)

        val ticket = resultStore.createTicket(command)
        val handle = workflowEngineAdapter.start(command, ticket.ticketId)
        return resultStore.markStarted(ticket.ticketId, handle.engineInstanceId)
    }

    fun runBlocking(command: WorkflowStartCommand, timeout: Duration): WorkflowRunResult {
        val ticket = startAsync(command)
        return resultStore.awaitTerminal(ticket.ticketId, timeout)
    }

    fun getResult(ticketId: String): WorkflowRunResult = resultStore.getResult(ticketId)

    fun listResults(): List<WorkflowRunResult> = resultStore.listResults()
}
