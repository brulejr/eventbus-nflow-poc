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

import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

@Profile("standalone")
class EmbeddedMoquetteConfig(
    private val datafill: IngressMqttDatafill
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean(destroyMethod = "stopServer")
    fun embeddedMoquetteServer(): Server {
        val dataPath = Path.of("data", "moquette")
        Files.createDirectories(dataPath)

        val properties = Properties().apply {
            setProperty("host", datafill.host)
            setProperty("port", datafill.port.toString())

            setProperty("allow_anonymous", "true")
            setProperty("persistence_enabled", "false")

            /*
             * Keep Moquette runtime files away from the project root and avoid
             * FileNotFoundException for data/.moquette_uuid.
             */
            setProperty("data_path", dataPath.toString())

            /*
             * Harmless if ignored by the Moquette version in use; useful if
             * supported because local dev should not emit telemetry noise.
             */
            setProperty("telemetry_enabled", "false")

            /*
             * Avoid accidentally enabling websocket MQTT in the local POC.
             */
            setProperty("websocket_port", "disabled")
        }

        log.info(
            "Starting embedded Moquette broker on {}:{} dataPath={}",
            datafill.host,
            datafill.port,
            dataPath
        )

        return Server().also { server ->
            server.startServer(MemoryConfig(properties))
        }
    }

}