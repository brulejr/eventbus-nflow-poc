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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.jrb.labs.nflowpoc.features.workflow.definition.WorkflowDefinitionStep
import io.jrb.labs.nflowpoc.features.workflow.metrics.WorkflowMetrics
import io.jrb.labs.nflowpoc.features.workflow.service.execution.WorkflowExecutionCommand
import io.jrb.labs.nflowpoc.features.workflow.service.execution.WorkflowExecutionStep
import io.jrb.labs.nflowpoc.features.workflow.service.nflow.NflowWorkflowSupport
import io.jrb.labs.nflowpoc.features.workflow.store.WorkflowResultStore
import io.nflow.engine.workflow.curated.State
import io.nflow.engine.workflow.definition.NextAction
import io.nflow.engine.workflow.definition.StateExecution
import io.nflow.engine.workflow.definition.WorkflowState
import io.nflow.engine.workflow.definition.WorkflowStateType

class Rtl433DataPipelineWorkflow(
    objectMapper: ObjectMapper,
    resultStore: WorkflowResultStore,
    metrics: WorkflowMetrics,
    private val ingestRawMessageStep: IngestRawMessageStep,
    private val decodeDevicePayloadStep: DecodeDevicePayloadStep,
    private val normalizeMeasurementsStep: NormalizeMeasurementsStep,
    private val classifySensorStep: ClassifySensorStep,
    private val enrichAssetMetadataStep: EnrichAssetMetadataStep,
    private val routeTelemetryStep: RouteTelemetryStep
) : NflowWorkflowSupport(Rtl433WorkflowTypes.DATA_PIPELINE, INGEST_RAW_MESSAGE, ERROR, objectMapper, resultStore, metrics) {

    private val anyType = object : TypeReference<Any?>() {}

    init {
        name = "rtl_433 data pipeline"
        description = "Domain nFlow state machine for ingesting, decoding, normalizing, classifying, enriching, and routing rtl_433 telemetry."
        permit(INGEST_RAW_MESSAGE, DECODE_DEVICE_PAYLOAD, ERROR)
        permit(DECODE_DEVICE_PAYLOAD, NORMALIZE_MEASUREMENTS, ERROR)
        permit(NORMALIZE_MEASUREMENTS, CLASSIFY_SENSOR, ERROR)
        permit(CLASSIFY_SENSOR, ENRICH_ASSET_METADATA, ERROR)
        permit(ENRICH_ASSET_METADATA, ROUTE_TELEMETRY, ERROR)
        permit(ROUTE_TELEMETRY, COMPLETE_PIPELINE, ERROR)
        permit(COMPLETE_PIPELINE, DONE, ERROR)
    }

    @Suppress("unused")
    fun ingestRawMessage(execution: StateExecution): NextAction {
        markRunning(execution)
        return executeDefinitionStep(execution, INGEST_RAW_MESSAGE, DECODE_DEVICE_PAYLOAD, ingestRawMessageStep)
    }

    @Suppress("unused")
    fun decodeDevicePayload(execution: StateExecution): NextAction =
        executeDefinitionStep(execution, DECODE_DEVICE_PAYLOAD, NORMALIZE_MEASUREMENTS, decodeDevicePayloadStep)

    @Suppress("unused")
    fun normalizeMeasurements(execution: StateExecution): NextAction =
        executeDefinitionStep(execution, NORMALIZE_MEASUREMENTS, CLASSIFY_SENSOR, normalizeMeasurementsStep)

    @Suppress("unused")
    fun classifySensor(execution: StateExecution): NextAction =
        executeDefinitionStep(execution, CLASSIFY_SENSOR, ENRICH_ASSET_METADATA, classifySensorStep)

    @Suppress("unused")
    fun enrichAssetMetadata(execution: StateExecution): NextAction =
        executeDefinitionStep(execution, ENRICH_ASSET_METADATA, ROUTE_TELEMETRY, enrichAssetMetadataStep)

    @Suppress("unused")
    fun routeTelemetry(execution: StateExecution): NextAction =
        executeDefinitionStep(execution, ROUTE_TELEMETRY, COMPLETE_PIPELINE, routeTelemetryStep)

    @Suppress("unused")
    fun completePipeline(execution: StateExecution): NextAction =
        executeRawStep(execution, COMPLETE_PIPELINE, DONE, ERROR) {
            val command = command(execution, Rtl433WorkflowTypes.DATA_PIPELINE)
            val output = output(execution)
            WorkflowExecutionStep.success(
                message = "rtl_433 pipeline completed",
                stateVariables = mapOf(
                    "pipelineStep" to "completed",
                    "workflowPath" to WORKFLOW_PATH
                ),
                result = command.resultEnvelope(
                    mapOf(
                        "output" to output,
                        "routingKey" to routingKey(execution, command),
                        "routeDestination" to routeDestination(execution, command),
                        "steps" to STEP_IDS,
                        "workflowPath" to WORKFLOW_PATH
                    )
                )
            )
        }

    private fun executeDefinitionStep(
        execution: StateExecution,
        state: WorkflowState,
        nextState: WorkflowState,
        step: WorkflowDefinitionStep
    ): NextAction =
        executeRawStep(execution, state, nextState, ERROR) {
            val command = command(execution, Rtl433WorkflowTypes.DATA_PIPELINE)
            failureFor(command, step)?.let { return@executeRawStep it }
            val output = step.execute(context(execution))
            WorkflowExecutionStep.success(
                message = "Executed ${step.id}",
                stateVariables = output + mapOf(
                    "pipelineStep" to step.id,
                    "workflowPath" to WORKFLOW_PATH.take(WORKFLOW_PATH.indexOf(state.name()) + 1)
                )
            )
        }

    private fun failureFor(command: WorkflowExecutionCommand, step: WorkflowDefinitionStep): WorkflowExecutionStep? =
        when {
            command.failInspection && step.id == decodeDevicePayloadStep.id ->
                WorkflowExecutionStep.failure("Inspection rejected by payload", mapOf("pipelineStep" to "inspection-failed"))
            command.failTransform && step.id in TRANSFORM_STEP_IDS ->
                WorkflowExecutionStep.failure("Transform rejected by payload", mapOf("pipelineStep" to "transform-failed"))
            command.failRoute && step.id == routeTelemetryStep.id ->
                WorkflowExecutionStep.failure("Route rejected by payload", mapOf("pipelineStep" to "route-failed"))
            else -> null
        }

    private fun context(execution: StateExecution): Map<String, Any?> {
        val payload = payload(execution)
        val parameters = mapValue(payload["parameters"]) ?: emptyMap()
        val raw = mapValue(payload["raw"]) ?: mapValue(parameters["raw"]) ?: emptyMap()
        return linkedMapOf<String, Any?>(
            "raw" to raw,
            "deviceId" to variable(execution, "deviceId"),
            "normalizedMeasurements" to variable(execution, "normalizedMeasurements"),
            "sensorType" to variable(execution, "sensorType"),
            "assetKey" to variable(execution, "assetKey"),
            "routingKey" to variable(execution, "routingKey"),
            "routeDestination" to variable(execution, "routeDestination")
        ).filterValues { it != null }
    }

    private fun output(execution: StateExecution): Map<String, Any?> =
        mapOf(
            "deviceId" to variable(execution, "deviceId"),
            "sensorType" to variable(execution, "sensorType"),
            "assetKey" to variable(execution, "assetKey"),
            "normalizedMeasurements" to variable(execution, "normalizedMeasurements")
        ).filterValues { it != null }

    private fun routingKey(execution: StateExecution, command: WorkflowExecutionCommand): String =
        variable(execution, "routingKey")?.toString() ?: command.routingKey ?: Rtl433WorkflowTypes.DATA_PIPELINE

    private fun routeDestination(execution: StateExecution, command: WorkflowExecutionCommand): String =
        variable(execution, "routeDestination")?.toString() ?: command.routeDestination ?: "telemetry.normalized"

    private fun variable(execution: StateExecution, name: String): Any? {
        val value = execution.getVariable(name, "")
        if (value.isBlank()) {
            return null
        }
        return decodeValue(value)
    }

    private fun decodeValue(value: String): Any =
        runCatching { objectMapper.readValue(value, anyType) }
            .map { decoded ->
                if (decoded is String && decoded.isJsonContainer()) {
                    decodeValue(decoded)
                } else {
                    decoded ?: value
                }
            }
            .getOrDefault(value)

    private fun String.isJsonContainer(): Boolean {
        val trimmed = trim()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapValue(value: Any?): Map<String, Any?>? =
        value as? Map<String, Any?>

    companion object {
        @JvmField
        val INGEST_RAW_MESSAGE: WorkflowState = State("ingestRawMessage", WorkflowStateType.start, "Accept raw rtl_433 telemetry")

        @JvmField
        val DECODE_DEVICE_PAYLOAD: WorkflowState = State("decodeDevicePayload", "Decode device identity")

        @JvmField
        val NORMALIZE_MEASUREMENTS: WorkflowState = State("normalizeMeasurements", "Normalize measurements")

        @JvmField
        val CLASSIFY_SENSOR: WorkflowState = State("classifySensor", "Classify sensor")

        @JvmField
        val ENRICH_ASSET_METADATA: WorkflowState = State("enrichAssetMetadata", "Enrich asset metadata")

        @JvmField
        val ROUTE_TELEMETRY: WorkflowState = State("routeTelemetry", "Route telemetry")

        @JvmField
        val COMPLETE_PIPELINE: WorkflowState = State("completePipeline", "Complete rtl_433 pipeline")

        @JvmField
        val DONE: WorkflowState = State("done", WorkflowStateType.end, "Ticket completed")

        @JvmField
        val ERROR: WorkflowState = State("error", WorkflowStateType.manual, "Manual recovery required")

        private val STEP_IDS = listOf(
            "ingest-raw-message",
            "decode-device-payload",
            "normalize-measurements",
            "classify-sensor",
            "enrich-asset-metadata",
            "route-telemetry"
        )
        private val TRANSFORM_STEP_IDS = setOf("normalize-measurements", "classify-sensor", "enrich-asset-metadata")
        private val WORKFLOW_PATH = listOf(
            "ingestRawMessage",
            "decodeDevicePayload",
            "normalizeMeasurements",
            "classifySensor",
            "enrichAssetMetadata",
            "routeTelemetry",
            "completePipeline",
            "done"
        )
    }
}
