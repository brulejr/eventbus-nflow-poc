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

package io.jrb.labs.nflowpoc.ingress

import io.jrb.labs.nflowpoc.metrics.WorkflowMetrics
import io.jrb.labs.nflowpoc.workflow.WorkflowLaunchService
import io.jrb.labs.nflowpoc.workflow.WorkflowStartCommand
import io.jrb.labs.nflowpoc.workflow.WorkflowTicket
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class InboundWorkflowDispatcher(
    private val workflowLaunchService: WorkflowLaunchService,
    private val metrics: WorkflowMetrics
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun dispatch(message: InboundMessage): WorkflowTicket {
        metrics.ingressReceived(message.source, message.workflowType)
        log.info("Dispatching inbound message source={} workflowType={} correlationId={}", message.source, message.workflowType, message.correlationId)
        return workflowLaunchService.startAsync(
            WorkflowStartCommand(
                workflowType = message.workflowType,
                correlationId = message.correlationId ?: UUID.randomUUID().toString(),
                source = message.source,
                payload = message.payload
            )
        )
    }
}
