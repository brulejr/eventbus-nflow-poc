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

import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowStartCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WorkflowDefinitionResolver(
    definitions: List<WorkflowDefinitionSpec>
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val definitionsById = definitions.associateBy { it.id }

    fun resolve(command: WorkflowStartCommand): WorkflowStartCommand {
        val definition = definitionFor(command) ?: return command
        val resolved = command.copy(
            workflowType = definition.engineWorkflowType,
            payload = definition.expand(command.payload)
        )
        log.info(
            "Resolved workflow definition id={} engineWorkflowType={} correlationId={}",
            definition.id,
            definition.engineWorkflowType,
            command.correlationId
        )
        return resolved
    }

    private fun definitionFor(command: WorkflowStartCommand): WorkflowDefinitionSpec? {
        return definitionsById[command.workflowType]
            ?: definitionName(command.payload)?.let(definitionsById::get)
    }

    private fun definitionName(payload: Map<String, Any?>): String? {
        return listOf("definition", "workflowDefinition", "name")
            .asSequence()
            .mapNotNull { payload[it]?.toString()?.takeIf(String::isNotBlank) }
            .firstOrNull()
    }
}

