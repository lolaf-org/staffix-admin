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
package org.lolaf.staffix.admin.logs.loki;

import org.lolaf.staffix.api.monitoring.FixMonitoringAttributes;

/**
 * The labels the console filters logs on: the attributes staffix's OTLP messages logger sets, as Loki names them
 * (dots become underscores). A constant's name is its label in upper case, as the settings bind it.
 */
public enum LokiLabel {
    FIX_EID(FixMonitoringAttributes.FIX_ENGINE_ID),
    FIX_SG(FixMonitoringAttributes.FIX_SESSION_GROUP),
    FIX_SN(FixMonitoringAttributes.FIX_SESSION_NAME),
    FIX_LOG_TYPE(FixMonitoringAttributes.FIX_LOG_TYPE),
    FIX_MSG_TYPE(FixMonitoringAttributes.FIX_MESSAGE_TYPE);

    private final String label;

    LokiLabel(FixMonitoringAttributes attribute) {
        this.label = attribute.getKey().replace('.', '_');
    }

    public String label() {
        return label;
    }
}
