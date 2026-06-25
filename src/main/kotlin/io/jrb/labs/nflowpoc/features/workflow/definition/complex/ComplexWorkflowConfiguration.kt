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

package io.jrb.labs.nflowpoc.features.workflow.definition.complex

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ComplexWorkflowConfiguration {

    @Bean
    fun complexWorkflowDefinition(
        validateRequestStep: ValidateRequestStep,
        prepareExecutionStep: PrepareExecutionStep,
        executeWorkStep: ExecuteWorkStep,
        collectOutputStep: CollectOutputStep
    ): ComplexWorkflowDefinition =
        ComplexWorkflowDefinition(
            validateRequestStep = validateRequestStep,
            prepareExecutionStep = prepareExecutionStep,
            executeWorkStep = executeWorkStep,
            collectOutputStep = collectOutputStep
        )

    @Bean
    fun validateRequestStep(): ValidateRequestStep =
        ValidateRequestStep()

    @Bean
    fun prepareExecutionStep(): PrepareExecutionStep =
        PrepareExecutionStep()

    @Bean
    fun executeWorkStep(): ExecuteWorkStep =
        ExecuteWorkStep()

    @Bean
    fun collectOutputStep(): CollectOutputStep =
        CollectOutputStep()
}
