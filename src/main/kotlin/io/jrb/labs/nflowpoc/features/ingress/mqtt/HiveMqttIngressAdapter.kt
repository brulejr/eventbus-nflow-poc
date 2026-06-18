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

package io.jrb.labs.nflowpoc.features.ingress.mqtt

import com.fasterxml.jackson.databind.ObjectMapper
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import io.jrb.labs.nflowpoc.features.workflow.messaging.InboundMessage
import io.jrb.labs.nflowpoc.features.workflow.messaging.InboundWorkflowDispatcher
import io.jrb.labs.nflowpoc.features.workflow.model.WorkflowSource
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

//@Component
//@EnableConfigurationProperties(IngressMqttDatafill::class)
//@ConditionalOnProperty(
//    prefix = "poc.broker.mqtt",
//    name = ["enabled"],
//    havingValue = "true",
//    matchIfMissing = false
//)
class HiveMqttIngressAdapter(
    private val datafill: IngressMqttDatafill,
    private val dispatcher: InboundWorkflowDispatcher,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val started = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)

    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "mqtt-ingress-retry").apply {
                isDaemon = true
            }
        }

    private val client: Mqtt3AsyncClient =
        MqttClient.builder()
            .useMqttVersion3()
            .identifier("eventbus-nflow-poc-${UUID.randomUUID()}")
            .serverHost(datafill.host)
            .serverPort(datafill.port)
            .buildAsync()

    /**
     * Start after the Spring context is fully ready. This avoids the race where
     * the HiveMQ client attempts to connect before embedded Moquette has bound
     * the local port.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        log.info(
            "Starting MQTT ingress adapter host={} port={} topic={}",
            datafill.host,
            datafill.port,
            datafill.topic
        )

        connectWithRetry(initialDelayMs = 0)
    }

    private fun connectWithRetry(initialDelayMs: Long) {
        if (!started.get()) {
            return
        }

        scheduler.schedule(
            {
                connectAndSubscribe()
            },
            initialDelayMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun connectAndSubscribe() {
        if (!started.get()) {
            return
        }

        log.info(
            "Connecting MQTT client to {}:{} and subscribing to topic {}",
            datafill.host,
            datafill.port,
            datafill.topic
        )

        client.connect()
            .thenCompose {
                client.subscribeWith()
                    .topicFilter(datafill.topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .callback { publish ->
                        handleMessage(
                            topic = publish.topic.toString(),
                            payload = StandardCharsets.UTF_8.decode(publish.payload.get()).toString()
                        )
                    }
                    .send()
            }
            .whenComplete { _, error ->
                if (error == null) {
                    connected.set(true)
                    log.info("MQTT subscription established topic={}", datafill.topic)
                } else {
                    connected.set(false)
                    log.warn(
                        "MQTT connection/subscription failed for {}:{} topic={}; retrying in {} ms",
                        datafill.host,
                        datafill.port,
                        datafill.topic,
                        datafill.retryDelayMs,
                        error
                    )
                    connectWithRetry(initialDelayMs = datafill.retryDelayMs)
                }
            }
    }

    private fun handleMessage(topic: String, payload: String) {
        try {
            log.info("Received MQTT message topic={} payload={}", topic, payload)

            val inboundMessage = objectMapper.readValue(payload, MqttWorkflowMessage::class.java)

            dispatcher.dispatch(
                InboundMessage(
                    source = WorkflowSource.MQTT,
                    workflowType = inboundMessage.workflowType,
                    correlationId = inboundMessage.correlationId,
                    payload = inboundMessage.payload,
                    rawBody = payload
                )
            )
        } catch (ex: Exception) {
            log.error("Failed to process MQTT message topic={} payload={}", topic, payload, ex)
        }
    }

    @PreDestroy
    fun stop() {
        started.set(false)

        runCatching {
            if (connected.get()) {
                client.disconnect()
            }
        }.onFailure { ex ->
            log.warn("Failed while disconnecting MQTT client", ex)
        }

        scheduler.shutdownNow()
    }
}

data class MqttWorkflowMessage(
    val workflowType: String,
    val correlationId: String? = null,
    val payload: Map<String, Any?> = emptyMap()
)