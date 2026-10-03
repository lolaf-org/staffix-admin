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
