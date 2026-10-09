/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lolaf.staffix.admin.metrics.prometheus;

import org.lolaf.staffix.api.monitoring.FixMonitoringAttributes;

/**
 * The labels the console queries meters on: the attributes staffix's monitoring sets, as Prometheus names them
 * through the collector's {@code UnderscoreEscapingWithoutSuffixes} (dots become underscores).
 */
enum PrometheusLabel {
    FIX_EID(FixMonitoringAttributes.FIX_ENGINE_ID),
    FIX_SG(FixMonitoringAttributes.FIX_SESSION_GROUP),
    FIX_SN(FixMonitoringAttributes.FIX_SESSION_NAME),
    FIX_MSG_TYPE(FixMonitoringAttributes.FIX_MESSAGE_TYPE),
    FIX_MSG_DIR(FixMonitoringAttributes.FIX_MESSAGE_DIRECTION);

    private final String label;

    PrometheusLabel(FixMonitoringAttributes attribute) {
        this.label = attribute.getKey().replace('.', '_');
    }

    String label() {
        return label;
    }
}
