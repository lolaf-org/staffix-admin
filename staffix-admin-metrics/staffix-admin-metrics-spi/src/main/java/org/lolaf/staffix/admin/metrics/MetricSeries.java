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
import java.util.List;
import java.util.Map;

@Value
@Builder
public class MetricSeries {
    /**
     * The values of the query's {@link MetricQuery#getSplitBy() split tags}, by tag key.
     */
    @Singular
    Map<String, String> labels;
    @Singular
    List<MetricPoint> points;
    /**
     * The window each point's rate was averaged over, which may be wider than the query's; null when not a rate.
     */
    @Nullable
    Duration rateWindow;
}
