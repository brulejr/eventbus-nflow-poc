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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.jrb.labs.nflowpoc.features.workflow.definition

import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowTypes
import org.springframework.stereotype.Component

@Component
class Rtl433DataPipelineWorkflowDefinition : WorkflowDefinitionSpec {
    override val id: String = "rtl433-data-pipeline"
    override val description: String = "Starter definition that maps rtl_433 device telemetry into a generic inbound pipeline command."
    override val engineWorkflowType: String = WorkflowTypes.INBOUND_MESSAGE
    override val steps: List<WorkflowDefinitionStep> = listOf(
        WorkflowDefinitionStep(
            id = "ingest-raw-message",
            description = "Accept the raw rtl_433 JSON payload from REST, MQTT, or RabbitMQ ingress.",
            inputKeys = listOf("raw"),
            outputKeys = listOf("raw")
        ),
        WorkflowDefinitionStep(
            id = "decode-device-payload",
            description = "Extract device identity fields such as model, id, and channel.",
            inputKeys = listOf("raw.model", "raw.id", "raw.channel"),
            outputKeys = listOf("deviceId")
        ),
        WorkflowDefinitionStep(
            id = "normalize-measurements",
            description = "Normalize rtl_433 measurement keys into value/unit structures.",
            inputKeys = listOf("raw.temperature_C", "raw.humidity", "raw.battery_ok"),
            outputKeys = listOf("normalizedMeasurements")
        ),
        WorkflowDefinitionStep(
            id = "classify-sensor",
            description = "Classify the sensor type from the decoded device model.",
            inputKeys = listOf("raw.model", "raw.device"),
            outputKeys = listOf("sensorType")
        ),
        WorkflowDefinitionStep(
            id = "enrich-asset-metadata",
            description = "Derive asset metadata for downstream consumers.",
            inputKeys = listOf("deviceId", "sensorType"),
            outputKeys = listOf("assetKey")
        ),
        WorkflowDefinitionStep(
            id = "route-telemetry",
            description = "Compute the routing key and route destination for normalized telemetry.",
            inputKeys = listOf("deviceId", "sensorType"),
            outputKeys = listOf("routingKey", "routeDestination")
        )
    )

    override fun expand(payload: Map<String, Any?>): Map<String, Any?> {
        val raw = rawPayload(payload)
        val sensorType = sensorType(raw)
        val deviceId = deviceId(raw)
        return mapOf(
            "name" to id,
            "parameters" to mapOf("raw" to raw),
            "steps" to steps.map { it.id },
            "definitionSteps" to steps.map { it.toPayload() },
            "output" to (payload["output"] ?: output(raw, sensorType, deviceId)),
            "routingKey" to (payload["routingKey"] ?: "rtl433.$sensorType.$deviceId"),
            "routeDestination" to (payload["routeDestination"] ?: "telemetry.normalized"),
            "failInspection" to (payload["failInspection"] == true),
            "failTransform" to (payload["failTransform"] == true),
            "failRoute" to (payload["failRoute"] == true)
        )
    }

    private fun rawPayload(payload: Map<String, Any?>): Map<String, Any?> {
        val parameters = mapValue(payload["parameters"])
        return mapValue(payload["raw"])
            ?: mapValue(parameters?.get("raw"))
            ?: payload.filterKeys { it !in RESERVED_KEYS }
    }

    private fun output(raw: Map<String, Any?>, sensorType: String, deviceId: String): Map<String, Any?> =
        mapOf(
            "deviceId" to deviceId,
            "sensorType" to sensorType,
            "assetKey" to "$sensorType:$deviceId",
            "normalizedMeasurements" to normalizedMeasurements(raw)
        )

    private fun deviceId(raw: Map<String, Any?>): String =
        raw["id"]?.toString()
            ?: raw["deviceId"]?.toString()
            ?: "unknown-device"

    private fun sensorType(raw: Map<String, Any?>): String {
        val model = raw["model"]?.toString() ?: raw["device"]?.toString() ?: ""
        return when {
            model.contains("weather", ignoreCase = true) -> "weather-station"
            model.contains("therm", ignoreCase = true) -> "temperature-sensor"
            model.contains("acurite", ignoreCase = true) -> "weather-sensor"
            else -> "generic-rtl433-sensor"
        }
    }

    private fun normalizedMeasurements(raw: Map<String, Any?>): Map<String, Map<String, Any?>> {
        val measurements = linkedMapOf<String, Map<String, Any?>>()
        firstNumber(raw, "temperature_C", "temperatureC")?.let {
            measurements["temperature"] = mapOf("value" to it, "unit" to "C")
        }
        firstNumber(raw, "temperature_F", "temperatureF", "temperature")?.let {
            measurements["temperatureF"] = mapOf("value" to it, "unit" to "F")
        }
        firstNumber(raw, "humidity", "humidity_pct")?.let {
            measurements["humidity"] = mapOf("value" to it, "unit" to "%")
        }
        firstValue(raw, "battery_ok", "batteryOk", "battery")?.let {
            measurements["battery"] = mapOf("value" to it, "unit" to "ok")
        }
        return measurements
    }

    private fun firstNumber(payload: Map<String, Any?>, vararg keys: String): Number? =
        keys.asSequence().mapNotNull { payload[it] as? Number }.firstOrNull()

    private fun firstValue(payload: Map<String, Any?>, vararg keys: String): Any? =
        keys.asSequence().mapNotNull { payload[it] }.firstOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun mapValue(value: Any?): Map<String, Any?>? =
        value as? Map<String, Any?>

    companion object {
        private val RESERVED_KEYS = setOf(
            "definition",
            "workflowDefinition",
            "name",
            "parameters",
            "raw",
            "output",
            "steps",
            "definitionSteps",
            "routingKey",
            "routeDestination",
            "failInspection",
            "failTransform",
            "failRoute"
        )
    }
}
