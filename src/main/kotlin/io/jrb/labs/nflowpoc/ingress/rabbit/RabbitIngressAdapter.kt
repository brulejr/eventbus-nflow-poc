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

package io.jrb.labs.nflowpoc.ingress.rabbit

import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import io.jrb.labs.nflowpoc.ingress.InboundMessageParser
import io.jrb.labs.nflowpoc.ingress.InboundWorkflowDispatcher
import io.jrb.labs.nflowpoc.ingress.MessageIngressAdapter
import io.jrb.labs.nflowpoc.workflow.WorkflowSource
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets.UTF_8

@Component
@ConditionalOnProperty(prefix = "poc.broker.rabbit", name = ["enabled"], havingValue = "true")
class RabbitIngressAdapter(
    private val properties: RabbitProperties,
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
            properties.host,
            properties.port,
            properties.virtualHost,
            properties.queue,
            properties.exchange,
            properties.routingKey
        )

        val factory = ConnectionFactory().apply {
            host = properties.host
            port = properties.port
            username = properties.username
            password = properties.password
            virtualHost = properties.virtualHost
            isAutomaticRecoveryEnabled = true
            networkRecoveryInterval = 5_000
        }

        val conn = factory.newConnection("eventbus-nflow-poc-rabbit-ingress")
        val ch = conn.createChannel()

        ch.exchangeDeclare(properties.exchange, BuiltinExchangeType.TOPIC, true)
        ch.queueDeclare(properties.queue, true, false, false, null)
        ch.queueBind(properties.queue, properties.exchange, properties.routingKey)

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

        consumerTag = ch.basicConsume(properties.queue, false, deliverCallback, cancelCallback)
        connection = conn
        channel = ch

        log.info("RabbitMQ subscription established queue={} consumerTag={}", properties.queue, consumerTag)
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
