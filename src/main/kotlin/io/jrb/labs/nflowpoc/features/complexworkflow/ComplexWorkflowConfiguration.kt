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

package io.jrb.labs.nflowpoc.features.complexworkflow

import io.jrb.labs.nflowpoc.features.FeatureDescriptors.CONFIG_PREFIX_WORKFLOW_DEFINITION_COMPLEX
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ComplexWorkflowDatafill::class)
@ConditionalOnProperty(prefix = CONFIG_PREFIX_WORKFLOW_DEFINITION_COMPLEX, name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ComplexWorkflowConfiguration {

    @Bean
    fun complexWorkflowInfoContributor(datafill: ComplexWorkflowDatafill): ComplexWorkflowInfoContributor =
        ComplexWorkflowInfoContributor(datafill)

    @Bean
    fun complexWorkflowDefinition(
        datafill: ComplexWorkflowDatafill,
        validateRequestStep: ValidateRequestStep,
        prepareExecutionStep: PrepareExecutionStep,
        executeWorkStep: ExecuteWorkStep,
        collectOutputStep: CollectOutputStep
    ): ComplexWorkflowDefinition =
        ComplexWorkflowDefinition(
            datafill = datafill,
            validateRequestStep = validateRequestStep,
            prepareExecutionStep = prepareExecutionStep,
            executeWorkStep = executeWorkStep,
            collectOutputStep = collectOutputStep
        )

    @Bean
    fun validateRequestStep(datafill: ComplexWorkflowDatafill): ValidateRequestStep =
        ValidateRequestStep(datafill)

    @Bean
    fun prepareExecutionStep(datafill: ComplexWorkflowDatafill): PrepareExecutionStep =
        PrepareExecutionStep(datafill)

    @Bean
    fun executeWorkStep(datafill: ComplexWorkflowDatafill): ExecuteWorkStep =
        ExecuteWorkStep(datafill)

    @Bean
    fun collectOutputStep(datafill: ComplexWorkflowDatafill): CollectOutputStep =
        CollectOutputStep(datafill)
}
