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
class InboundMessageWorkflow(
    objectMapper: ObjectMapper,
    resultStore: WorkflowResultStore
) : NflowWorkflowSupport(WorkflowTypes.INBOUND_MESSAGE, INGEST, ERROR, objectMapper, resultStore) {

    init {
        name = "rtl433 data pipeline simulator"
        description = "nFlow starter workflow that simulates ingesting, decoding, normalizing, enriching, and routing rtl_433 sensor data."
        permit(INGEST, DECODE, ERROR)
        permit(DECODE, NORMALIZE, ERROR)
        permit(NORMALIZE, CLASSIFY_DEVICE, ERROR)
        permit(CLASSIFY_DEVICE, ENRICH, ERROR)
        permit(ENRICH, ROUTE_TELEMETRY, ERROR)
        permit(ROUTE_TELEMETRY, DONE, ERROR)
    }

    fun ingest(execution: StateExecution): NextAction {
        markRunning(execution)
        execution.setVariable("rtl433Stage", "ingested")
        return NextAction.moveToState(DECODE, "Raw rtl_433 payload ingested")
    }

    fun decode(execution: StateExecution): NextAction {
        val payload = payload(execution)
        val model = payload["model"]?.toString() ?: payload["device"]?.toString() ?: "unknown-model"
        val deviceId = payload["id"]?.toString() ?: payload["deviceId"]?.toString() ?: correlationId(execution)
        execution.setVariable("model", model)
        execution.setVariable("deviceId", deviceId)
        execution.setVariable("rtl433Stage", "decoded")
        return NextAction.moveToState(NORMALIZE, "Decoded $model/$deviceId")
    }

    fun normalize(execution: StateExecution): NextAction {
        val payload = payload(execution)
        val measurements = mapOf(
            "temperatureC" to firstNumber(payload, "temperature_C", "temperatureC", "temperature"),
            "humidity" to firstNumber(payload, "humidity", "humidity_pct"),
            "batteryOk" to firstValue(payload, "battery_ok", "batteryOk", "battery")
        ).filterValues { it != null }
        execution.setVariable("measurementCount", measurements.size)
        execution.setVariable("normalizedMeasurements", objectMapperWriteSafe(measurements))
        execution.setVariable("rtl433Stage", "normalized")
        return NextAction.moveToState(CLASSIFY_DEVICE, "Normalized ${measurements.size} measurements")
    }

    fun classifyDevice(execution: StateExecution): NextAction {
        val model = execution.getVariable("model", "unknown-model")
        val sensorType = when {
            model.contains("weather", ignoreCase = true) -> "weather-station"
            model.contains("therm", ignoreCase = true) -> "temperature-sensor"
            model.contains("acurite", ignoreCase = true) -> "weather-sensor"
            else -> "generic-rtl433-sensor"
        }
        execution.setVariable("sensorType", sensorType)
        execution.setVariable("rtl433Stage", "classified")
        return NextAction.moveToState(ENRICH, "Classified as $sensorType")
    }

    fun enrich(execution: StateExecution): NextAction {
        val deviceId = execution.getVariable("deviceId", correlationId(execution))
        val sensorType = execution.getVariable("sensorType", "generic-rtl433-sensor")
        execution.setVariable("assetKey", "$sensorType:$deviceId")
        execution.setVariable("rtl433Stage", "enriched")
        return NextAction.moveToState(ROUTE_TELEMETRY, "Enriched telemetry")
    }

    fun routeTelemetry(execution: StateExecution): NextAction {
        val sensorType = execution.getVariable("sensorType", "generic-rtl433-sensor")
        val deviceId = execution.getVariable("deviceId", correlationId(execution))
        val routingKey = "rtl433.$sensorType.$deviceId"
        complete(
            execution,
            mapOf(
                "example" to "rtl433-data-pipeline",
                "workflowType" to WorkflowTypes.INBOUND_MESSAGE,
                "correlationId" to correlationId(execution),
                "source" to source(execution).name,
                "model" to execution.getVariable("model", "unknown-model"),
                "deviceId" to deviceId,
                "sensorType" to sensorType,
                "assetKey" to execution.getVariable("assetKey", ""),
                "routingKey" to routingKey,
                "measurementCount" to execution.getVariable("measurementCount", Int::class.java, 0)
            )
        )
        execution.setVariable("rtl433Stage", "routed")
        return NextAction.moveToState(DONE, "Telemetry routed")
    }

    private fun firstNumber(payload: Map<String, Any?>, vararg keys: String): Number? =
        keys.asSequence().mapNotNull { payload[it] as? Number }.firstOrNull()

    private fun firstValue(payload: Map<String, Any?>, vararg keys: String): Any? =
        keys.asSequence().mapNotNull { payload[it] }.firstOrNull()

    private fun objectMapperWriteSafe(value: Any): String =
        runCatching { objectMapper.writeValueAsString(value) }.getOrDefault("{}")

    companion object {
        const val TYPE: String = WorkflowTypes.INBOUND_MESSAGE

        @JvmField
        val INGEST: WorkflowState = State("ingest", WorkflowStateType.start, "Ingest broker payload")

        @JvmField
        val DECODE: WorkflowState = State("decode", "Decode rtl_433 payload")

        @JvmField
        val NORMALIZE: WorkflowState = State("normalize", "Normalize measurements")

        @JvmField
        val CLASSIFY_DEVICE: WorkflowState = State("classifyDevice", "Classify sensor")

        @JvmField
        val ENRICH: WorkflowState = State("enrich", "Attach derived metadata")

        @JvmField
        val ROUTE_TELEMETRY: WorkflowState = State("routeTelemetry", "Route normalized telemetry")

        @JvmField
        val DONE: WorkflowState = State("done", WorkflowStateType.end, "Ticket completed")

        @JvmField
        val ERROR: WorkflowState = State("error", WorkflowStateType.manual, "Manual recovery required")
    }
}
