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

package io.jrb.labs.nflowpoc.ingress

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowSource
import org.springframework.stereotype.Component

@Component
class InboundMessageParser(
    private val objectMapper: ObjectMapper
) {
    fun parse(source: WorkflowSource, rawBody: String): InboundMessage {
        val root = objectMapper.readValue(rawBody, object : TypeReference<Map<String, Any?>>() {})
        val workflowType = root["workflowType"] as? String ?: "inbound-message-workflow"
        val correlationId = root["correlationId"] as? String
        @Suppress("UNCHECKED_CAST")
        val payload = root["payload"] as? Map<String, Any?> ?: emptyMap()

        return InboundMessage(
            source = source,
            correlationId = correlationId,
            workflowType = workflowType,
            payload = payload,
            rawBody = rawBody
        )
    }
}
