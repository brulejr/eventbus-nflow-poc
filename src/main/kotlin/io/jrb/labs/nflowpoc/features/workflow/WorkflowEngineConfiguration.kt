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

package io.jrb.labs.nflowpoc.features.workflow

import com.fasterxml.jackson.databind.ObjectMapper
import io.jrb.labs.nflowpoc.features.FeatureDescriptors.CONFIG_PREFIX_WORKFLOW_ENGINE
import io.jrb.labs.nflowpoc.features.workflow.messaging.InboundMessageParser
import io.jrb.labs.nflowpoc.features.workflow.messaging.InboundWorkflowDispatcher
import io.jrb.labs.nflowpoc.features.workflow.metrics.WorkflowMetrics
import io.jrb.labs.nflowpoc.features.workflow.service.WorkflowEngineAdapter
import io.jrb.labs.nflowpoc.features.workflow.service.WorkflowLaunchService
import io.jrb.labs.nflowpoc.features.workflow.store.WorkflowResultStore
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(WorkflowEngineDatafill::class)
@ConditionalOnProperty(prefix = CONFIG_PREFIX_WORKFLOW_ENGINE, name = ["enabled"], havingValue = "true", matchIfMissing = true)
class WorkflowEngineConfiguration {

    @Bean
    fun workflowEngineInfoContributor(datafill: WorkflowEngineDatafill) : WorkflowEngineInfoContributor {
        return WorkflowEngineInfoContributor(datafill)
    }

    @Bean
    fun workflowLaunchService(
        workflowEngineAdapter: WorkflowEngineAdapter,
        resultStore: WorkflowResultStore,
        metrics: WorkflowMetrics
    ): WorkflowLaunchService {
        return WorkflowLaunchService(workflowEngineAdapter, resultStore, metrics)
    }

    @Bean
    fun workflowResultStore(): WorkflowResultStore {
        return WorkflowResultStore()
    }

    @Bean
    fun workflowMetrics(meterRegistry: MeterRegistry) : WorkflowMetrics {
        return WorkflowMetrics(meterRegistry)
    }

    @Bean
    fun inboundMessageParser(objectMapper: ObjectMapper): InboundMessageParser {
        return InboundMessageParser(objectMapper)
    }

    @Bean
    fun inboundWorkflowDispatcher(
        workflowLaunchService: WorkflowLaunchService,
        metrics: WorkflowMetrics
    ): InboundWorkflowDispatcher {
        return InboundWorkflowDispatcher(workflowLaunchService, metrics)
    }

}