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
