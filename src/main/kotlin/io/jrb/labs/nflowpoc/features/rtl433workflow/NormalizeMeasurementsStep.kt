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

package io.jrb.labs.nflowpoc.features.rtl433workflow

import io.jrb.labs.nflowpoc.features.workflow.definition.WorkflowDefinitionStep

class NormalizeMeasurementsStep(
    private val datafill: Rtl433WorkflowDatafill
) : WorkflowDefinitionStep {
    override val id: String = "normalize-measurements"
    override val description: String = "Normalize rtl_433 measurement keys into value/unit structures."
    override val inputKeys: List<String> = listOf("raw.temperature_C", "raw.humidity", "raw.battery_ok")
    override val outputKeys: List<String> = listOf("normalizedMeasurements")

    override fun execute(input: Map<String, Any?>): Map<String, Any?> {
        val raw = mapValue(input["raw"]) ?: emptyMap()
        return mapOf("normalizedMeasurements" to normalizedMeasurements(raw))
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
}
