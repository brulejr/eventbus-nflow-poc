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

import com.fasterxml.jackson.databind.ObjectMapper
import io.jrb.labs.nflowpoc.features.FeatureDescriptors.CONFIG_PREFIX_WORKFLOW_DEFINITION_RTL433
import io.jrb.labs.nflowpoc.features.workflow.metrics.WorkflowMetrics
import io.jrb.labs.nflowpoc.features.workflow.store.WorkflowResultStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableConfigurationProperties(Rtl433WorkflowDatafill::class)
@ConditionalOnProperty(prefix = CONFIG_PREFIX_WORKFLOW_DEFINITION_RTL433, name = ["enabled"], havingValue = "true", matchIfMissing = true)
class Rtl433DataPipelineWorkflowConfiguration {

    @Bean
    fun rtl433WorkflowInfoContributor(datafill: Rtl433WorkflowDatafill): Rtl433WorkflowInfoContributor =
        Rtl433WorkflowInfoContributor(datafill)

    @Bean
    fun rtl433DataPipelineWorkflowDefinition(
        datafill: Rtl433WorkflowDatafill,
        ingestRawMessageStep: IngestRawMessageStep,
        decodeDevicePayloadStep: DecodeDevicePayloadStep,
        normalizeMeasurementsStep: NormalizeMeasurementsStep,
        classifySensorStep: ClassifySensorStep,
        enrichAssetMetadataStep: EnrichAssetMetadataStep,
        routeTelemetryStep: RouteTelemetryStep
    ): Rtl433DataPipelineWorkflowDefinition =
        Rtl433DataPipelineWorkflowDefinition(
            datafill = datafill,
            ingestRawMessageStep = ingestRawMessageStep,
            decodeDevicePayloadStep = decodeDevicePayloadStep,
            normalizeMeasurementsStep = normalizeMeasurementsStep,
            classifySensorStep = classifySensorStep,
            enrichAssetMetadataStep = enrichAssetMetadataStep,
            routeTelemetryStep = routeTelemetryStep
        )

    @Bean
    fun ingestRawMessageStep(datafill: Rtl433WorkflowDatafill): IngestRawMessageStep =
        IngestRawMessageStep(datafill)

    @Bean
    fun decodeDevicePayloadStep(datafill: Rtl433WorkflowDatafill): DecodeDevicePayloadStep =
        DecodeDevicePayloadStep(datafill)

    @Bean
    fun normalizeMeasurementsStep(datafill: Rtl433WorkflowDatafill): NormalizeMeasurementsStep =
        NormalizeMeasurementsStep(datafill)

    @Bean
    fun classifySensorStep(datafill: Rtl433WorkflowDatafill): ClassifySensorStep =
        ClassifySensorStep(datafill)

    @Bean
    fun enrichAssetMetadataStep(datafill: Rtl433WorkflowDatafill): EnrichAssetMetadataStep =
        EnrichAssetMetadataStep(datafill)

    @Bean
    fun routeTelemetryStep(datafill: Rtl433WorkflowDatafill): RouteTelemetryStep =
        RouteTelemetryStep(datafill)

    @Bean
    @Profile("nflow")
    fun rtl433DataPipelineWorkflow(
        objectMapper: ObjectMapper,
        resultStore: WorkflowResultStore,
        metrics: WorkflowMetrics,
        ingestRawMessageStep: IngestRawMessageStep,
        decodeDevicePayloadStep: DecodeDevicePayloadStep,
        normalizeMeasurementsStep: NormalizeMeasurementsStep,
        classifySensorStep: ClassifySensorStep,
        enrichAssetMetadataStep: EnrichAssetMetadataStep,
        routeTelemetryStep: RouteTelemetryStep
    ): Rtl433DataPipelineWorkflow =
        Rtl433DataPipelineWorkflow(
            objectMapper = objectMapper,
            resultStore = resultStore,
            metrics = metrics,
            ingestRawMessageStep = ingestRawMessageStep,
            decodeDevicePayloadStep = decodeDevicePayloadStep,
            normalizeMeasurementsStep = normalizeMeasurementsStep,
            classifySensorStep = classifySensorStep,
            enrichAssetMetadataStep = enrichAssetMetadataStep,
            routeTelemetryStep = routeTelemetryStep
        )
}
