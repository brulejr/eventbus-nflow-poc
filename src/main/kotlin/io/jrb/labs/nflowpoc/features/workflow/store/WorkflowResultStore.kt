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

package io.jrb.labs.nflowpoc.features.workflow.store

import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowRunResult
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowRunStatus
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowSource
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowStartCommand
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowTicket
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class WorkflowResultStore {

    private val tickets = ConcurrentHashMap<String, MutableWorkflowRecord>()
    private val waiters = ConcurrentHashMap<String, CompletableFuture<WorkflowRunResult>>()

    fun createTicket(command: WorkflowStartCommand): WorkflowTicket {
        val now = Instant.now()
        val record = MutableWorkflowRecord(
            ticketId = UUID.randomUUID().toString(),
            workflowType = command.workflowType,
            correlationId = command.correlationId,
            source = command.source,
            engineInstanceId = null,
            status = WorkflowRunStatus.RECEIVED,
            result = null,
            error = null,
            createdAt = now,
            updatedAt = now
        )
        tickets[record.ticketId] = record
        return record.toTicket()
    }

    fun markStarted(ticketId: String, engineInstanceId: String): WorkflowTicket = update(ticketId) {
        it.engineInstanceId = engineInstanceId
        it.status = WorkflowRunStatus.STARTED
    }.toTicket()

    fun markRunning(ticketId: String): WorkflowRunResult = completeOrUpdate(ticketId) {
        it.status = WorkflowRunStatus.RUNNING
    }

    fun markCompleted(ticketId: String, result: Map<String, Any?>): WorkflowRunResult = completeOrUpdate(ticketId) {
        it.status = WorkflowRunStatus.COMPLETED
        it.result = result
    }

    fun markFailed(ticketId: String, error: String): WorkflowRunResult = completeOrUpdate(ticketId) {
        it.status = WorkflowRunStatus.FAILED
        it.error = error
    }

    fun markTimedOut(ticketId: String, error: String): WorkflowRunResult = completeOrUpdate(ticketId) {
        it.status = WorkflowRunStatus.TIMED_OUT
        it.error = error
    }

    fun getResult(ticketId: String): WorkflowRunResult =
        tickets[ticketId]?.toResult() ?: throw NoSuchElementException("No workflow ticket found for ticketId=$ticketId")

    fun listResults(): List<WorkflowRunResult> =
        tickets.values.sortedByDescending { it.createdAt }.map { it.toResult() }

    fun awaitTerminal(ticketId: String, timeout: Duration): WorkflowRunResult {
        val current = getResult(ticketId)
        if (current.status.isTerminal()) return current

        val future = waiters.computeIfAbsent(ticketId) { CompletableFuture() }
        return try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (ex: TimeoutException) {
            markTimedOut(ticketId, "Timed out waiting for workflow ticketId=$ticketId after $timeout")
        } finally {
            waiters.remove(ticketId)
        }
    }

    private fun completeOrUpdate(ticketId: String, block: (MutableWorkflowRecord) -> Unit): WorkflowRunResult {
        val result = update(ticketId, block).toResult()
        if (result.status.isTerminal()) {
            waiters[ticketId]?.complete(result)
        }
        return result
    }

    private fun update(ticketId: String, block: (MutableWorkflowRecord) -> Unit): MutableWorkflowRecord {
        val record = tickets[ticketId] ?: throw NoSuchElementException("No workflow ticket found for ticketId=$ticketId")
        synchronized(record) {
            block(record)
            record.updatedAt = Instant.now()
        }
        return record
    }

    private fun WorkflowRunStatus.isTerminal(): Boolean =
        this == WorkflowRunStatus.COMPLETED || this == WorkflowRunStatus.FAILED || this == WorkflowRunStatus.TIMED_OUT
}

private data class MutableWorkflowRecord(
    val ticketId: String,
    val workflowType: String,
    val correlationId: String,
    val source: WorkflowSource,
    var engineInstanceId: String?,
    var status: WorkflowRunStatus,
    var result: Map<String, Any?>?,
    var error: String?,
    val createdAt: Instant,
    var updatedAt: Instant
) {
    fun toTicket() = WorkflowTicket(
        ticketId = ticketId,
        workflowType = workflowType,
        correlationId = correlationId,
        source = source,
        engineInstanceId = engineInstanceId,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun toResult() = WorkflowRunResult(
        ticketId = ticketId,
        workflowType = workflowType,
        correlationId = correlationId,
        source = source,
        engineInstanceId = engineInstanceId,
        status = status,
        result = result,
        error = error,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
