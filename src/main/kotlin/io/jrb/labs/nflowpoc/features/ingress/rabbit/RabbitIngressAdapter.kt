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

package io.jrb.labs.nflowpoc.features.ingress.rabbit

import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import io.jrb.labs.nflowpoc.features.workflow.messaging.InboundMessageParser
import io.jrb.labs.nflowpoc.features.workflow.messaging.InboundWorkflowDispatcher
import io.jrb.labs.nflowpoc.features.workflow.messaging.MessageIngressAdapter
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowSource
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets.UTF_8

class RabbitIngressAdapter(
    private val datafill: IngressRabbitDatafill,
    private val parser: InboundMessageParser,
    private val dispatcher: InboundWorkflowDispatcher
) : MessageIngressAdapter {
    private val log = LoggerFactory.getLogger(javaClass)

    private var connection: Connection? = null
    private var channel: Channel? = null
    private var consumerTag: String? = null

    override val name: String = "rabbitmq-client"
    override val source: WorkflowSource = WorkflowSource.RABBITMQ

    @PostConstruct
    fun start() {
        log.info(
            "Connecting RabbitMQ client to {}:{} vhost={} queue={} exchange={} routingKey={}",
            datafill.host,
            datafill.port,
            datafill.virtualHost,
            datafill.queue,
            datafill.exchange,
            datafill.routingKey
        )

        val factory = ConnectionFactory().apply {
            host = datafill.host
            port = datafill.port
            username = datafill.username
            password = datafill.password
            virtualHost = datafill.virtualHost
            isAutomaticRecoveryEnabled = true
            networkRecoveryInterval = 5_000
        }

        val conn = factory.newConnection("eventbus-nflow-poc-rabbit-ingress")
        val ch = conn.createChannel()

        ch.exchangeDeclare(datafill.exchange, BuiltinExchangeType.TOPIC, true)
        ch.queueDeclare(datafill.queue, true, false, false, null)
        ch.queueBind(datafill.queue, datafill.exchange, datafill.routingKey)

        val deliverCallback = DeliverCallback { _, delivery ->
            val rawBody = String(delivery.body, UTF_8)
            try {
                dispatcher.dispatch(parser.parse(WorkflowSource.RABBITMQ, rawBody))
                ch.basicAck(delivery.envelope.deliveryTag, false)
            } catch (ex: Exception) {
                log.error("Failed to dispatch RabbitMQ message payload={}", rawBody, ex)
                ch.basicNack(delivery.envelope.deliveryTag, false, true)
            }
        }

        val cancelCallback = CancelCallback { tag ->
            log.warn("RabbitMQ consumer cancelled tag={}", tag)
        }

        consumerTag = ch.basicConsume(datafill.queue, false, deliverCallback, cancelCallback)
        connection = conn
        channel = ch

        log.info("RabbitMQ subscription established queue={} consumerTag={}", datafill.queue, consumerTag)
    }

    @PreDestroy
    fun stop() {
        val tag = consumerTag
        val ch = channel
        runCatching {
            if (tag != null && ch?.isOpen == true) {
                ch.basicCancel(tag)
            }
        }.onFailure { ex -> log.debug("Error cancelling RabbitMQ consumer", ex) }

        runCatching { ch?.close() }
            .onFailure { ex -> log.debug("Error closing RabbitMQ channel", ex) }

        runCatching { connection?.close() }
            .onFailure { ex -> log.debug("Error closing RabbitMQ connection", ex) }
    }
}
