package org.lolaf.staffix.admin.metrics;

import java.time.Duration;
import java.util.List;

/**
 * A metrics backend (Prometheus, ...) holding what staffix's monitoring exports, queried by the admin console
 * rather than the engines, which do not serve their own history.
 *
 * <p>Series are selected by the labels staffix tags its meters with: {@code fix.eid} (engine), {@code fix.sg}
 * (group), {@code fix.sn} (session name, unique per group only), {@code fix.msg.type} and {@code fix.msg.dir}.
 */
public interface MetricsSource {

    /**
     * One series per message type and direction for the per-message kinds, a single series otherwise. Empty when
     * the backend holds nothing for the query.
     *
     * @throws MetricsSourceException if the backend cannot be queried
     */
    List<MetricSeries> query(MetricQuery query);

    /**
     * Probes the backend with a cheap request, so an admin knows a connector works before relying on it.
     *
     * @throws MetricsSourceException if the backend does not answer as expected, or cannot be tested
     */
    default void testConnection() {
        throw new MetricsSourceException("This metrics backend cannot be tested", null);
    }

    /**
     * The narrowest window this backend rates over, set by how often it gets a sample (e.g. twice a scrape
     * interval); a narrower {@link MetricQuery#getRateWindow()} is widened to it. Zero when the backend does not know.
     */
    default Duration getMinRateWindow() {
        return Duration.ZERO;
    }
}
