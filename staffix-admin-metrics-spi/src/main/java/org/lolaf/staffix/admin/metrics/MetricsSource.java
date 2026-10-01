package org.lolaf.staffix.admin.metrics;

import java.util.List;

/**
 * A metrics backend (Prometheus, ...) holding what staffix's monitoring exports, queried by the admin console
 * rather than the engines, which do not serve their own history.
 *
 * <p>Series are selected by the labels staffix tags its meters with: {@code fix.iid} (instance), {@code fix.sid}
 * (session), {@code fix.msg.type} and {@code fix.msg.dir}.
 */
public interface MetricsSource {

    /**
     * One series per message type and direction for the per-message kinds, a single series otherwise. Empty when
     * the backend holds nothing for the query.
     *
     * @throws MetricsSourceException if the backend cannot be queried
     */
    List<MetricSeries> query(MetricQuery query);
}
