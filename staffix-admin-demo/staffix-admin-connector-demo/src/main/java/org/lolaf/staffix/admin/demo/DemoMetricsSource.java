package org.lolaf.staffix.admin.demo;

import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

final class DemoMetricsSource implements MetricsSource {

    private final DemoClient client;

    DemoMetricsSource(DemoClient client) {
        this.client = client;
    }

    @Override
    public List<MetricSeries> query(MetricQuery query) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("engineId", query.getEngineId());
        parameters.put("group", query.getGroup());
        parameters.put("sessionId", query.getSessionId());
        parameters.put("kind", query.getKind().name());
        parameters.put("quantile", query.getQuantile());
        parameters.put("from", query.getFrom());
        parameters.put("to", query.getTo());
        parameters.put("step", query.getStep().toSeconds());
        parameters.put("rateWindow", query.getRateWindow() == null ? null : query.getRateWindow().toSeconds());
        JsonNode response = client.get("/demo/metrics", parameters, MetricsSourceException::new);
        return StreamSupport.stream(response.spliterator(), false).map(DemoMetricsSource::series).toList();
    }

    @Override
    public void testConnection() {
        client.ping(MetricsSourceException::new);
    }

    private static MetricSeries series(JsonNode json) {
        MetricSeries.MetricSeriesBuilder series = MetricSeries.builder();
        if (json.path("rateWindow").isNumber()) {
            series.rateWindow(Duration.ofSeconds(json.path("rateWindow").asLong()));
        }
        json.path("labels").properties().forEach(label -> series.label(label.getKey(), label.getValue().asString()));
        json.path("points").forEach(point -> series.point(new MetricPoint(Instant.parse(point.path("timestamp").asString()),
                point.path("value").asDouble())));
        return series.build();
    }
}
