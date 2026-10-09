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
package org.lolaf.staffix.admin.logs.elasticsearch;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.elasticsearch.ElasticsearchClient;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceException;
import org.lolaf.staffix.api.monitoring.FixMonitoringAttributes;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.lolaf.staffix.admin.elasticsearch.ElasticsearchQueries.GROUP;
import static org.lolaf.staffix.admin.elasticsearch.ElasticsearchQueries.SESSION;
import static org.lolaf.staffix.admin.elasticsearch.ElasticsearchQueries.TIMESTAMP;
import static org.lolaf.staffix.admin.elasticsearch.ElasticsearchQueries.scope;
import static org.lolaf.staffix.admin.elasticsearch.ElasticsearchQueries.timeRange;

/**
 * Reads the FIX message logs staffix's OTLP logger sends to Elasticsearch through the OpenTelemetry collector's
 * {@code elasticsearch} exporter, OTel mapping.
 */
public class ElasticsearchLogsSource implements LogsSource {

    private static final String BODY = "body.text";
    private static final String LOG_TYPE = FixMonitoringAttributes.FIX_LOG_TYPE.getKey();
    private static final String MSG_TYPE = FixMonitoringAttributes.FIX_MESSAGE_TYPE.getKey();
    private static final String NANOS = "strict_date_optional_time_nanos";
    private static final Map<LogType, String> LOG_TYPES = Map.of(LogType.IN, "in", LogType.OUT, "out", LogType.EVENT, "event");

    private final ElasticsearchClient client;

    public ElasticsearchLogsSource(ElasticsearchLogsSettings settings, RestClient.Builder builder, JsonMapper jsonMapper) {
        this.client = new ElasticsearchClient(settings, builder, jsonMapper, LogsSourceException::new);
    }

    @Override
    public void testConnection() {
        client.testConnection();
    }

    @Override
    public List<LogEntry> query(LogQuery query) {
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("size", query.getLimit());
        search.put("track_total_hits", false);
        search.put("_source", false);
        search.put("fields", List.of(Map.of("field", TIMESTAMP, "format", NANOS), GROUP, SESSION, LOG_TYPE, BODY));
        search.put("sort", List.of(Map.of(TIMESTAMP, Map.of("order", "desc"))));
        search.put("query", Map.of("bool", Map.of("filter", filters(query))));
        return entries(client.search(search), query);
    }

    private static List<Object> filters(LogQuery query) {
        List<Object> filters = scope(query.getEngineId(), query.getGroup(), query.getSessionId());
        if (!query.getTypes().isEmpty()) {
            filters.add(terms(LOG_TYPE, query.getTypes().stream().map(LOG_TYPES::get).toList()));
        }
        if (!query.getMsgTypes().isEmpty()) {
            filters.add(terms(MSG_TYPE, query.getMsgTypes()));
        }
        if (query.getText() != null) {
            filters.add(Map.of("match_phrase", Map.of(BODY, query.getText())));
        }
        if (!query.getFieldValues().isEmpty()) {
            // body.text is analyzed: a phrase finds the value whatever its case or tag, the hits are checked exactly
            filters.add(Map.of("bool", Map.of("minimum_should_match", 1, "should", query.getFieldValues().stream()
                    .sorted()
                    .map(value -> Map.of("match_phrase", Map.of(BODY, value)))
                    .toList())));
        }
        filters.add(timeRange(query.getFrom().toString(), query.getTo().toString(), NANOS));
        return filters;
    }

    private static Map<String, Object> terms(String field, Collection<String> values) {
        return Map.of("terms", Map.of(field, values.stream().sorted().toList()));
    }

    private static List<LogEntry> entries(JsonNode response, LogQuery query) {
        return response.path("hits").path("hits").valueStream()
                .map(hit -> hit.path("fields"))
                .map(fields -> LogEntry.builder()
                        .timestamp(Instant.parse(Objects.requireNonNull(text(fields, TIMESTAMP), "a hit without @timestamp")))
                        .group(text(fields, GROUP))
                        .sessionId(text(fields, SESSION))
                        .type(type(text(fields, LOG_TYPE)))
                        .line(Objects.requireNonNullElse(text(fields, BODY), ""))
                        .build())
                .filter(entry -> query.hasFieldValue(entry.getLine()))
                .toList();
    }

    /**
     * The fields API answers every field as an array.
     */
    private static @Nullable String text(JsonNode fields, String name) {
        JsonNode value = fields.path(name).path(0);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private static @Nullable LogType type(@Nullable String logType) {
        return switch (logType) {
            case "in" -> LogType.IN;
            case "out" -> LogType.OUT;
            case "event" -> LogType.EVENT;
            case null, default -> null;
        };
    }
}
