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

package io.jrb.labs.nflowpoc.workflow.nflow

import com.fasterxml.jackson.databind.ObjectMapper
import io.jrb.labs.nflowpoc.workflow.WorkflowEngineAdapter
import io.jrb.labs.nflowpoc.workflow.WorkflowEngineHandle
import io.jrb.labs.nflowpoc.workflow.WorkflowStartCommand
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * nFlow-backed WorkflowEngineAdapter.
 *
 * This implementation deliberately uses reflection at the boundary with nFlow. The goal is to keep
 * the rest of the POC stable while the exact nFlow 11 Spring Boot API is validated locally. If this
 * reflection adapter proves out, it can be replaced with a strongly typed adapter using the same
 * WorkflowEngineAdapter interface.
 */
@Component
@Profile("nflow")
class NflowWorkflowEngineAdapter(
    private val applicationContext: ApplicationContext,
    private val objectMapper: ObjectMapper
) : WorkflowEngineAdapter {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun start(command: WorkflowStartCommand, ticketId: String): WorkflowEngineHandle {
        val workflowInstanceService = bean("io.nflow.engine.service.WorkflowInstanceService")
        val workflowInstanceFactory = bean("io.nflow.engine.workflow.instance.WorkflowInstanceFactory")

        val builder = invokeNoArgs(workflowInstanceFactory, "newWorkflowInstanceBuilder")
        invokeFluently(builder, "setType", command.workflowType)
        invokeFluently(builder, "setExternalId", ticketId)
        invokeFluently(builder, "putStateVariable", NflowVars.TICKET_ID, ticketId)
        invokeFluently(builder, "putStateVariable", NflowVars.CORRELATION_ID, command.correlationId)
        invokeFluently(builder, "putStateVariable", NflowVars.SOURCE, command.source.name)
        invokeFluently(builder, "putStateVariable", NflowVars.PAYLOAD_JSON, objectMapper.writeValueAsString(command.payload))

        val instance = invokeNoArgs(builder, "build")
        val id = invokeFirstMatching(workflowInstanceService, "insertWorkflowInstance", instance)
            ?: error("nFlow insertWorkflowInstance returned null for ticketId=$ticketId")

        log.info(
            "Started nFlow workflow type={} ticketId={} nflowInstanceId={}",
            command.workflowType,
            ticketId,
            id
        )
        return WorkflowEngineHandle(id.toString())
    }

    private fun bean(className: String): Any {
        val type = Class.forName(className)
        return applicationContext.getBean(type)
    }

    private fun invokeNoArgs(target: Any, methodName: String): Any =
        target.javaClass.methods
            .firstOrNull { it.name == methodName && it.parameterCount == 0 }
            ?.invoke(target)
            ?: error("Could not invoke no-arg method $methodName on ${target.javaClass.name}")

    private fun invokeFluently(target: Any, methodName: String, vararg args: Any): Any =
        invokeFirstMatching(target, methodName, *args) ?: target

    private fun invokeFirstMatching(target: Any, methodName: String, vararg args: Any): Any? {
        val method = target.javaClass.methods.firstOrNull { candidate ->
            candidate.name == methodName &&
                candidate.parameterCount == args.size &&
                candidate.parameterTypes.zip(args).all { (parameterType, value) ->
                    parameterType.isAssignableFrom(value.javaClass)
                }
        } ?: error(
            "Could not find method $methodName(${args.joinToString { it.javaClass.name }}) on ${target.javaClass.name}"
        )
        return method.invoke(target, *args)
    }
}
