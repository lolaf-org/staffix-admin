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
package org.lolaf.staffix.admin.metrics;

import java.time.Duration;
import java.util.List;

/**
 * A metrics backend (Prometheus, ...) holding what staffix's monitoring exports, queried by the admin console
 * rather than the engines, which do not serve their own history.
 *
 * <p>Series are selected by the labels staffix tags its meters with: {@code fix.eid} (engine), {@code fix.sg}
 * (group), {@code fix.sn} (session name, unique per group only), {@code fix.mt} and {@code fix.md}.
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
