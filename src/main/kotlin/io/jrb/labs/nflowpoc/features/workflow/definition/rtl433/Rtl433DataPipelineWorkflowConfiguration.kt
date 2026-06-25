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

package io.jrb.labs.nflowpoc.features.workflow.definition.rtl433

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Rtl433DataPipelineWorkflowConfiguration {

    @Bean
    fun rtl433DataPipelineWorkflowDefinition(
        ingestRawMessageStep: IngestRawMessageStep,
        decodeDevicePayloadStep: DecodeDevicePayloadStep,
        normalizeMeasurementsStep: NormalizeMeasurementsStep,
        classifySensorStep: ClassifySensorStep,
        enrichAssetMetadataStep: EnrichAssetMetadataStep,
        routeTelemetryStep: RouteTelemetryStep
    ): Rtl433DataPipelineWorkflowDefinition =
        Rtl433DataPipelineWorkflowDefinition(
            ingestRawMessageStep = ingestRawMessageStep,
            decodeDevicePayloadStep = decodeDevicePayloadStep,
            normalizeMeasurementsStep = normalizeMeasurementsStep,
            classifySensorStep = classifySensorStep,
            enrichAssetMetadataStep = enrichAssetMetadataStep,
            routeTelemetryStep = routeTelemetryStep
        )

    @Bean
    fun ingestRawMessageStep(): IngestRawMessageStep =
        IngestRawMessageStep()

    @Bean
    fun decodeDevicePayloadStep(): DecodeDevicePayloadStep =
        DecodeDevicePayloadStep()

    @Bean
    fun normalizeMeasurementsStep(): NormalizeMeasurementsStep =
        NormalizeMeasurementsStep()

    @Bean
    fun classifySensorStep(): ClassifySensorStep =
        ClassifySensorStep()

    @Bean
    fun enrichAssetMetadataStep(): EnrichAssetMetadataStep =
        EnrichAssetMetadataStep()

    @Bean
    fun routeTelemetryStep(): RouteTelemetryStep =
        RouteTelemetryStep()
}
