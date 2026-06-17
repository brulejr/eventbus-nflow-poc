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

package io.jrb.labs.nflowpoc.api

import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowRunResult
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowTicket

data class WorkflowRequest(
    val correlationId: String? = null,
    val workflowType: String? = null,
    val payload: Map<String, Any?> = emptyMap()
)

data class WorkflowTicketResponse(
    val ticketId: String,
    val workflowType: String,
    val correlationId: String,
    val engineInstanceId: String?,
    val status: String,
    val statusUrl: String
)

fun WorkflowTicket.toResponse() = WorkflowTicketResponse(
    ticketId = ticketId,
    workflowType = workflowType,
    correlationId = correlationId,
    engineInstanceId = engineInstanceId,
    status = status.name,
    statusUrl = "/api/workflows/tickets/$ticketId"
)

data class WorkflowSummaryResponse(
    val tickets: List<WorkflowRunResult>
)
