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

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * One meter of staffix's monitoring, read for an engine, a group or a session. Tags are named as staffix's
 * monitoring names them ({@code fix.mt}, {@code fix.sg}...), whatever the backend calls them.
 */
@Value
@Builder(toBuilder = true)
public class MetricQuery {
    String engineId;
    /**
     * Null for every group of the engine.
     */
    @Nullable
    String group;
    /**
     * A config id within {@link #getGroup()}, which it requires (ids are unique per group only); null for every
     * session of the group.
     */
    @Nullable
    String sessionId;
    /**
     * As the monitoring publishes it, such as {@code messages.read.latency}.
     */
    String meter;
    MetricStatistic statistic;
    /**
     * For {@link MetricStatistic#QUANTILE}, e.g. 0.99.
     */
    @Nullable
    Double quantile;
    /**
     * The tags giving one series per combination of their values; none for a single series over the scope.
     */
    @Singular("splitBy")
    List<String> splitBy;
    /**
     * Only the meter's series with these tag values, such as {@code fix.md=i}.
     */
    @Singular
    Map<String, String> tagFilters;
    /**
     * A gauge's unit, its values then read in seconds; null for a gauge read as is.
     */
    @Nullable
    TimeUnit gaugeUnit;
    /**
     * For {@link MetricStatistic#SHARE}, the gauge value counted.
     */
    @Nullable
    Double value;
    /**
     * For {@link MetricStatistic#SHARE}, how often the engines export the gauge, telling how many samples a full step
     * holds; null when unknown, a share then being of the samples found, so missing ones go unnoticed.
     */
    @Nullable
    Duration sampleInterval;
    Instant from;
    Instant to;
    Duration step;
    /**
     * The window rates are averaged over, as asked; null for the step. A source may widen it to hold enough samples.
     */
    @Nullable
    Duration rateWindow;
    /**
     * The unit the engines' timers are exported in, when the console knows it from their monitoring plugin; null to
     * use the source's own setting.
     */
    @Nullable
    TimeUnit timerBaseUnit;
}
