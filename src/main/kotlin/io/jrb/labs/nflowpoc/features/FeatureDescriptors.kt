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
    const val CONFIG_PREFIX_WORKFLOW_INGRESS = "workflow.ingress"

    val WORKFLOW_ENGINE = FeatureDescriptor(
        application = APP_NAME,
        featureId = "workflow-engine",
        displayName = "Workflow Engine",
        description = "Provides the core workflow engine",
        configPrefix = CONFIG_PREFIX_WORKFLOW_ENGINE
    )

    val WORKFLOW_INGRESS = FeatureDescriptor(
        application = APP_NAME,
        featureId = "workflow-ingress",
        displayName = "Workflow Engine Ingress",
        description = "Provides REST and messaging access to the workflow engine",
        configPrefix = CONFIG_PREFIX_WORKFLOW_INGRESS
    )

}