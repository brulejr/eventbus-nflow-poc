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
import io.jrb.labs.nflowpoc.features.workflow.definition.WorkflowDefinitionResolver
import io.jrb.labs.nflowpoc.features.workflow.definition.WorkflowDefinitionSpec
import io.jrb.labs.nflowpoc.features.workflow.messaging.InboundMessageParser
import io.jrb.labs.nflowpoc.features.workflow.messaging.InboundWorkflowDispatcher
import io.jrb.labs.nflowpoc.features.workflow.metrics.WorkflowMetrics
import io.jrb.labs.nflowpoc.features.workflow.service.WorkflowEngineAdapter
import io.jrb.labs.nflowpoc.features.workflow.service.WorkflowLaunchService
import io.jrb.labs.nflowpoc.features.workflow.service.execution.AsyncRestExecutionEngine
import io.jrb.labs.nflowpoc.features.workflow.service.execution.BlockingRestExecutionEngine
import io.jrb.labs.nflowpoc.features.workflow.service.execution.InboundMessageExecutionEngine
import io.jrb.labs.nflowpoc.features.workflow.service.nflow.AsyncRestWorkflow
import io.jrb.labs.nflowpoc.features.workflow.service.nflow.BlockingRestWorkflow
import io.jrb.labs.nflowpoc.features.workflow.service.nflow.InboundMessageWorkflow
import io.jrb.labs.nflowpoc.features.workflow.service.nflow.NflowWorkflowEngineAdapter
import io.jrb.labs.nflowpoc.features.workflow.store.WorkflowResultStore
import io.micrometer.core.instrument.MeterRegistry
import io.nflow.engine.service.WorkflowInstanceService
import io.nflow.engine.workflow.instance.WorkflowInstanceFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@ConditionalOnProperty(prefix = CONFIG_PREFIX_WORKFLOW_ENGINE, name = ["enabled"], havingValue = "true", matchIfMissing = true)
class WorkflowEngineConfiguration {

    @Bean
    fun workflowEngineInfoContributor() : WorkflowEngineInfoContributor {
        return WorkflowEngineInfoContributor()
    }

    @Bean
    fun workflowLaunchService(
        workflowEngineAdapter: WorkflowEngineAdapter,
        resultStore: WorkflowResultStore,
        metrics: WorkflowMetrics,
        definitionResolver: WorkflowDefinitionResolver
    ): WorkflowLaunchService {
        return WorkflowLaunchService(workflowEngineAdapter, resultStore, metrics, definitionResolver)
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
    fun workflowDefinitionResolver(definitions: List<WorkflowDefinitionSpec>): WorkflowDefinitionResolver {
        return WorkflowDefinitionResolver(definitions)
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

    @Bean
    @Profile("nflow")
    fun nflowWorkflowEngineAdapter(
        workflowInstanceService: WorkflowInstanceService,
        workflowInstanceFactory: WorkflowInstanceFactory,
        objectMapper: ObjectMapper
    ): NflowWorkflowEngineAdapter {
        return NflowWorkflowEngineAdapter(workflowInstanceService, workflowInstanceFactory, objectMapper)
    }

    @Bean
    @Profile("nflow")
    fun asyncRestExecutionEngine(): AsyncRestExecutionEngine {
        return AsyncRestExecutionEngine()
    }

    @Bean
    @Profile("nflow")
    fun blockingRestExecutionEngine(): BlockingRestExecutionEngine {
        return BlockingRestExecutionEngine()
    }

    @Bean
    @Profile("nflow")
    fun inboundMessageExecutionEngine(): InboundMessageExecutionEngine {
        return InboundMessageExecutionEngine()
    }

    @Bean
    @Profile("nflow")
    fun asyncRestWorkflow(
        objectMapper: ObjectMapper,
        resultStore: WorkflowResultStore,
        metrics: WorkflowMetrics,
        executionEngine: AsyncRestExecutionEngine
    ): AsyncRestWorkflow {
        return AsyncRestWorkflow(objectMapper, resultStore, metrics, executionEngine)
    }

    @Bean
    @Profile("nflow")
    fun blockingRestWorkflow(
        objectMapper: ObjectMapper,
        resultStore: WorkflowResultStore,
        metrics: WorkflowMetrics,
        executionEngine: BlockingRestExecutionEngine
    ): BlockingRestWorkflow {
        return BlockingRestWorkflow(objectMapper, resultStore, metrics, executionEngine)
    }

    @Bean
    @Profile("nflow")
    fun inboundMessageWorkflow(
        objectMapper: ObjectMapper,
        resultStore: WorkflowResultStore,
        metrics: WorkflowMetrics,
        executionEngine: InboundMessageExecutionEngine
    ): InboundMessageWorkflow {
        return InboundMessageWorkflow(objectMapper, resultStore, metrics, executionEngine)
    }

}
