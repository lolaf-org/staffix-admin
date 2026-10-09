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

/**
 * What a query reads of a meter. Timers are read in seconds, rates per second.
 */
public enum MetricStatistic {
    /**
     * Recordings per second, from a timer's count.
     */
    RATE,
    /**
     * A timer's {@link MetricQuery#getQuantile() quantile}, from its histogram.
     */
    QUANTILE,
    /**
     * A timer's mean, its sum over its count.
     */
    MEAN,
    /**
     * A gauge's value.
     */
    VALUE,
    /**
     * The share, 0 to 1, of each step a gauge held {@link MetricQuery#getValue()}. The shares of every value add up to
     * less than 1 where samples are missing.
     */
    SHARE
}
