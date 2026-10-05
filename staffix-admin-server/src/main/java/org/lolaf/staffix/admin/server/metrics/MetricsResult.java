package org.lolaf.staffix.admin.server.metrics;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.metrics.MetricSeries;

import java.time.Duration;
import java.util.List;

@Value
@Builder
public class MetricsResult {
    @Singular("series")
    List<MetricSeries> series;
    /**
     * Sessions of the scope, as {@code group.session}, whose metrics no connector collects; the series leave them out.
     */
    @Singular("notCollected")
    List<String> notCollected;
    /**
     * The widest window the series' rates were averaged over, for the chart's caption; null when none is a rate.
     */
    @Nullable
    Duration rateWindow;
    /**
     * The narrowest window the data allows, twice the engines' export interval or the backend's minimum; null when
     * unknown.
     */
    @Nullable
    Duration minRateWindow;
    /**
     * The time between two points.
     */
    Duration step;
}
