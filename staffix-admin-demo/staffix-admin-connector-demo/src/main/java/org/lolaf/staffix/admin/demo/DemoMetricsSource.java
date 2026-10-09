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
import java.util.stream.Collectors;
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
        parameters.put("meter", query.getMeter());
        parameters.put("statistic", query.getStatistic().name());
        parameters.put("splitBy", String.join(",", query.getSplitBy()));
        parameters.put("tagFilters", query.getTagFilters().entrySet().stream()
                .map(filter -> filter.getKey() + "=" + filter.getValue())
                .collect(Collectors.joining(",")));
        parameters.put("quantile", query.getQuantile());
        parameters.put("value", query.getValue());
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
