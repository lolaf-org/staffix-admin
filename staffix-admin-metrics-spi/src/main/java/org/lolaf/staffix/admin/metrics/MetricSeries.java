package org.lolaf.staffix.admin.metrics;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class MetricSeries {
    public static final String MESSAGE_TYPE = "messageType";
    public static final String DIRECTION = "direction";
    public static final String SESSION_ID = "sessionId";

    /**
     * Among {@link #MESSAGE_TYPE}, {@link #DIRECTION} ({@code in} or {@code out}) and {@link #SESSION_ID}.
     */
    @Singular
    Map<String, String> labels;
    @Singular
    List<MetricPoint> points;
}
