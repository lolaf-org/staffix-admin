package org.lolaf.staffix.admin.server.metrics;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class MetricSeries {
    @Singular
    Map<String, String> labels;
    @Singular
    List<MetricPoint> points;
}
