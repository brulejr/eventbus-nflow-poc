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
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowTypes
import io.jrb.labs.nflowpoc.features.workflow.store.WorkflowResultStore
import io.nflow.engine.workflow.curated.State
import io.nflow.engine.workflow.definition.NextAction
import io.nflow.engine.workflow.definition.StateExecution
import io.nflow.engine.workflow.definition.WorkflowState
import io.nflow.engine.workflow.definition.WorkflowStateType
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("nflow")
class BlockingRestWorkflow(
    objectMapper: ObjectMapper,
    resultStore: WorkflowResultStore
) : NflowWorkflowSupport(WorkflowTypes.BLOCKING_REST, BEGIN, ERROR, objectMapper, resultStore) {

    init {
        name = "Complex multi-step starter workflow"
        description = "Multi-step nFlow workflow that models validation, reservation, payment, fulfillment, and notification."
        permit(BEGIN, VALIDATE, ERROR)
        permit(VALIDATE, RESERVE_INVENTORY, ERROR)
        permit(RESERVE_INVENTORY, CHARGE_PAYMENT, ERROR)
        permit(CHARGE_PAYMENT, SCHEDULE_FULFILLMENT, ERROR)
        permit(SCHEDULE_FULFILLMENT, NOTIFY, ERROR)
        permit(NOTIFY, DONE, ERROR)
    }

    fun begin(execution: StateExecution): NextAction {
        markRunning(execution)
        execution.setVariable("complexStep", "received")
        return NextAction.moveToState(VALIDATE, "Request accepted")
    }

    fun validate(execution: StateExecution): NextAction {
        val payload = payload(execution)
        val sku = payload["sku"]?.toString()?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
        val quantity = (payload["quantity"] as? Number)?.toInt() ?: 1
        if (quantity < 1) {
            return failAndStop(execution, ERROR, "Quantity must be at least 1")
        }
        execution.setVariable("sku", sku)
        execution.setVariable("quantity", quantity)
        execution.setVariable("complexStep", "validated")
        return NextAction.moveToState(RESERVE_INVENTORY, "Validated $sku x$quantity")
    }

    fun reserveInventory(execution: StateExecution): NextAction {
        val sku = execution.getVariable("sku", "UNKNOWN")
        val quantity = execution.getVariable("quantity", Int::class.java, 1)
        execution.setVariable("reservationId", "res-${ticketId(execution).take(8)}")
        execution.setVariable("complexStep", "inventory-reserved")
        return NextAction.moveToState(CHARGE_PAYMENT, "Reserved $sku x$quantity")
    }

    fun chargePayment(execution: StateExecution): NextAction {
        val payload = payload(execution)
        val amount = (payload["amount"] as? Number)?.toDouble() ?: 0.0
        execution.setVariable("paymentId", "pay-${ticketId(execution).take(8)}")
        execution.setVariable("authorizedAmount", amount)
        execution.setVariable("complexStep", "payment-authorized")
        return NextAction.moveToState(SCHEDULE_FULFILLMENT, "Payment authorized")
    }

    fun scheduleFulfillment(execution: StateExecution): NextAction {
        execution.setVariable("fulfillmentId", "ful-${ticketId(execution).take(8)}")
        execution.setVariable("complexStep", "fulfillment-scheduled")
        return NextAction.moveToState(NOTIFY, "Fulfillment scheduled")
    }

    fun notify(execution: StateExecution): NextAction {
        complete(
            execution,
            mapOf(
                "example" to "complex",
                "workflowType" to WorkflowTypes.BLOCKING_REST,
                "correlationId" to correlationId(execution),
                "source" to source(execution).name,
                "sku" to execution.getVariable("sku", "UNKNOWN"),
                "quantity" to execution.getVariable("quantity", Int::class.java, 1),
                "reservationId" to execution.getVariable("reservationId", ""),
                "paymentId" to execution.getVariable("paymentId", ""),
                "fulfillmentId" to execution.getVariable("fulfillmentId", "")
            )
        )
        return NextAction.moveToState(DONE, "Notification emitted")
    }

    companion object {
        const val TYPE: String = WorkflowTypes.BLOCKING_REST

        @JvmField
        val BEGIN: WorkflowState = State("begin", WorkflowStateType.start, "Accept the request")

        @JvmField
        val VALIDATE: WorkflowState = State("validate", "Validate payload")

        @JvmField
        val RESERVE_INVENTORY: WorkflowState = State("reserveInventory", "Reserve inventory")

        @JvmField
        val CHARGE_PAYMENT: WorkflowState = State("chargePayment", "Authorize payment")

        @JvmField
        val SCHEDULE_FULFILLMENT: WorkflowState = State("scheduleFulfillment", "Schedule fulfillment")

        @JvmField
        val NOTIFY: WorkflowState = State("notify", "Emit completion notification")

        @JvmField
        val DONE: WorkflowState = State("done", WorkflowStateType.end, "Ticket completed")

        @JvmField
        val ERROR: WorkflowState = State("error", WorkflowStateType.manual, "Manual recovery required")
    }
}
