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

package io.jrb.labs.nflowpoc.features.workflow.service.nflow

import com.fasterxml.jackson.databind.ObjectMapper
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowEngineHandle
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowStartCommand
import io.jrb.labs.nflowpoc.features.workflow.service.WorkflowEngineAdapter
import io.nflow.engine.service.WorkflowInstanceService
import io.nflow.engine.workflow.instance.WorkflowInstanceFactory
import org.slf4j.LoggerFactory

/**
 * nFlow-backed WorkflowEngineAdapter.
 */
class NflowWorkflowEngineAdapter(
    private val workflowInstanceService: WorkflowInstanceService,
    private val workflowInstanceFactory: WorkflowInstanceFactory,
    private val objectMapper: ObjectMapper
) : WorkflowEngineAdapter {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun start(command: WorkflowStartCommand, ticketId: String): WorkflowEngineHandle {
        val instance = workflowInstanceFactory.newWorkflowInstanceBuilder()
            .setType(command.workflowType)
            .setExternalId(ticketId)
            .putStateVariable(NflowVars.TICKET_ID, ticketId)
            .putStateVariable(NflowVars.CORRELATION_ID, command.correlationId)
            .putStateVariable(NflowVars.SOURCE, command.source.name)
            .putStateVariable(NflowVars.PAYLOAD_JSON, objectMapper.writeValueAsString(command.payload))
            .build()
        val id = workflowInstanceService.insertWorkflowInstance(instance)

        log.info(
            "Started nFlow workflow type={} ticketId={} nflowInstanceId={}",
            command.workflowType,
            ticketId,
            id
        )
        return WorkflowEngineHandle(id.toString())
    }
}
