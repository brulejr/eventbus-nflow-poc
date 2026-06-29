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

import io.jrb.labs.nflowpoc.features.workflow.definition.WorkflowDefinitionSpec
import io.jrb.labs.nflowpoc.features.workflow.definition.WorkflowDefinitionStep
class Rtl433DataPipelineWorkflowDefinition(
    datafill: Rtl433WorkflowDatafill,
    ingestRawMessageStep: IngestRawMessageStep,
    decodeDevicePayloadStep: DecodeDevicePayloadStep,
    normalizeMeasurementsStep: NormalizeMeasurementsStep,
    classifySensorStep: ClassifySensorStep,
    enrichAssetMetadataStep: EnrichAssetMetadataStep,
    routeTelemetryStep: RouteTelemetryStep
) : WorkflowDefinitionSpec {
    override val id: String = Rtl433WorkflowTypes.DATA_PIPELINE
    override val description: String = "State-machine workflow that maps rtl_433 device telemetry into normalized routed telemetry."
    override val engineWorkflowType: String = Rtl433WorkflowTypes.DATA_PIPELINE
    override val steps: List<WorkflowDefinitionStep> = listOf(
        ingestRawMessageStep,
        decodeDevicePayloadStep,
        normalizeMeasurementsStep,
        classifySensorStep,
        enrichAssetMetadataStep,
        routeTelemetryStep
    )

    override fun expand(payload: Map<String, Any?>): Map<String, Any?> {
        val raw = rawPayload(payload)
        return mapOf(
            "name" to id,
            "parameters" to mapOf("raw" to raw),
            "raw" to raw,
            "steps" to steps.map { it.id },
            "definitionSteps" to steps.map { it.toPayload() },
            "routingKey" to payload["routingKey"],
            "routeDestination" to payload["routeDestination"],
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
