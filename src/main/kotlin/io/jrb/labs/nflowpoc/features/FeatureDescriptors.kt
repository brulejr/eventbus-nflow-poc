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

package io.jrb.labs.nflowpoc.features

import io.jrb.labs.commons.feature.FeatureDescriptor
import io.jrb.labs.nflowpoc.NflowPocApplication.Companion.APP_NAME

object FeatureDescriptors {

    const val CONFIG_PREFIX_WORKFLOW_ENGINE = "workflow.engine"
    const val CONFIG_PREFIX_WORKFLOW_INGRESS_MQTT = "workflow.ingress.mqtt"
    const val CONFIG_PREFIX_WORKFLOW_INGRESS_RABBIT = "workflow.ingress.rabbit"
    const val CONFIG_PREFIX_WORKFLOW_DEFINITION_SIMPLE = "workflow.definition.simple"
    const val CONFIG_PREFIX_WORKFLOW_DEFINITION_COMPLEX = "workflow.definition.complex"
    const val CONFIG_PREFIX_WORKFLOW_DEFINITION_RTL433 = "workflow.definition.rtl433"

    val WORKFLOW_ENGINE = FeatureDescriptor(
        application = APP_NAME,
        featureId = "workflow-engine",
        displayName = "Workflow Engine",
        description = "Provides the core workflow engine",
        configPrefix = CONFIG_PREFIX_WORKFLOW_ENGINE
    )

    val WORKFLOW_INGRESS_MQTT = FeatureDescriptor(
        application = APP_NAME,
        featureId = "workflow-ingress-mqtt",
        displayName = "Workflow Engine MQTT Ingress",
        description = "Provides MQTT access to the workflow engine",
        configPrefix = CONFIG_PREFIX_WORKFLOW_INGRESS_MQTT
    )

    val WORKFLOW_INGRESS_RABBIT = FeatureDescriptor(
        application = APP_NAME,
        featureId = "workflow-ingress-rabbit",
        displayName = "Workflow Engine RABBITMQ Ingress",
        description = "Provides RabbitMQ access to the workflow engine",
        configPrefix = CONFIG_PREFIX_WORKFLOW_INGRESS_RABBIT
    )

    val WORKFLOW_DEFINITION_SIMPLE = FeatureDescriptor(
        application = APP_NAME,
        featureId = "workflow-definition-simple",
        displayName = "Simple Workflow Definition",
        description = "Provides a simple workflow definition for the workflow engine",
        configPrefix = CONFIG_PREFIX_WORKFLOW_DEFINITION_SIMPLE
    )

    val WORKFLOW_DEFINITION_COMPLEX = FeatureDescriptor(
        application = APP_NAME,
        featureId = "workflow-definition-complex",
        displayName = "Complex Workflow Definition",
        description = "Provides a complex workflow definition for the workflow engine",
        configPrefix = CONFIG_PREFIX_WORKFLOW_DEFINITION_COMPLEX
    )

    val WORKFLOW_DEFINITION_RTL433 = FeatureDescriptor(
        application = APP_NAME,
        featureId = "workflow-definition-rtl433",
        displayName = "rtl433 Data Pipeline Workflow Definition",
        description = "Provides an rtl433 data pipeline workflow definition for the workflow engine",
        configPrefix = CONFIG_PREFIX_WORKFLOW_DEFINITION_RTL433
    )

}
