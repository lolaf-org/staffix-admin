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
