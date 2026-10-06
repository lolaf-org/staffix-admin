package org.lolaf.staffix.admin.logs.elasticsearch;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceException;
import org.lolaf.staffix.api.monitoring.FixMonitoringAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads the FIX message logs staffix's OTLP logger sends to Elasticsearch through the OpenTelemetry collector's
 * {@code elasticsearch} exporter, OTel mapping. Its attributes and resource attributes are passthrough objects, so the
 * {@code fix.*} attributes are queried by their own names wherever staffix puts them.
 */
public class ElasticsearchLogsSource implements LogsSource {

    private static final String TIMESTAMP = "@timestamp";
    private static final String BODY = "body.text";
    private static final String ENGINE = FixMonitoringAttributes.FIX_ENGINE_ID.getKey();
    private static final String GROUP = FixMonitoringAttributes.FIX_SESSION_GROUP.getKey();
    private static final String SESSION = FixMonitoringAttributes.FIX_SESSION_NAME.getKey();
    private static final String LOG_TYPE = FixMonitoringAttributes.FIX_LOG_TYPE.getKey();
    private static final String MSG_TYPE = FixMonitoringAttributes.FIX_MESSAGE_TYPE.getKey();
    private static final String NANOS = "strict_date_optional_time_nanos";
    private static final Map<LogType, String> LOG_TYPES = Map.of(LogType.IN, "in", LogType.OUT, "out", LogType.EVENT, "event");

    private final RestClient client;
    private final URI rootUrl;
    private final URI searchUrl;
    private final URI resolveUrl;
    private final String index;
    private final JsonMapper jsonMapper;

    public ElasticsearchLogsSource(ElasticsearchLogsSettings properties, RestClient.Builder builder, JsonMapper jsonMapper) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build());
        requestFactory.setReadTimeout(properties.getTimeout());
        if (properties.getApiKey() != null) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + properties.getApiKey());
        } else if (properties.getUsername() != null) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(properties.getUsername(), Objects.requireNonNullElse(properties.getPassword(), "")));
        }
        this.client = builder.requestFactory(requestFactory).build();
        String url = properties.getUrl().toString().replaceAll("/+$", "");
        this.index = properties.getIndex();
        this.rootUrl = URI.create(url + "/");
        this.searchUrl = UriComponentsBuilder.fromUriString(url).pathSegment(index, "_search").encode().build().toUri();
        this.resolveUrl = UriComponentsBuilder.fromUriString(url).pathSegment("_resolve", "index", index).encode().build().toUri();
        this.jsonMapper = jsonMapper;
    }

    /**
     * Reads the cluster's info, which also checks the credentials, then makes sure the index matches something.
     */
    @Override
    public void testConnection() {
        get(rootUrl);
        JsonNode resolved = get(resolveUrl);
        if (resolved.path("indices").isEmpty() && resolved.path("aliases").isEmpty() && resolved.path("data_streams").isEmpty()) {
            throw new LogsSourceException("Elasticsearch has no index or data stream matching " + index, null);
        }
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
        JsonNode response;
        try {
            response = client.post()
                    .uri(searchUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonMapper.writeValueAsString(search))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, failure) -> {
                        throw new LogsSourceException("Elasticsearch answered " + failure.getStatusCode().value() + ": "
                                + error(failure.getBody().readAllBytes()), null);
                    })
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new LogsSourceException("Elasticsearch at " + searchUrl + " did not answer", e);
        }
        return entries(Objects.requireNonNull(response, "Elasticsearch answered no body"));
    }

    private static List<Object> filters(LogQuery query) {
        List<Object> filters = new ArrayList<>();
        filters.add(term(ENGINE, query.getEngineId()));
        if (query.getGroup() != null) {
            filters.add(term(GROUP, query.getGroup()));
        }
        if (query.getSessionId() != null) {
            filters.add(term(SESSION, query.getSessionId()));
        }
        if (!query.getTypes().isEmpty()) {
            filters.add(terms(LOG_TYPE, query.getTypes().stream().map(LOG_TYPES::get).toList()));
        }
        if (!query.getMsgTypes().isEmpty()) {
            filters.add(terms(MSG_TYPE, query.getMsgTypes()));
        }
        if (query.getText() != null) {
            filters.add(Map.of("match_phrase", Map.of(BODY, query.getText())));
        }
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("gte", query.getFrom().toString());
        range.put("lt", query.getTo().toString());
        range.put("format", NANOS);
        filters.add(Map.of("range", Map.of(TIMESTAMP, range)));
        return filters;
    }

    private static Map<String, Object> term(String field, String value) {
        return Map.of("term", Map.of(field, value));
    }

    private static Map<String, Object> terms(String field, Collection<String> values) {
        return Map.of("terms", Map.of(field, values.stream().sorted().toList()));
    }

    private JsonNode get(URI uri) {
        try {
            return Objects.requireNonNull(client.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, failure) -> {
                        throw new LogsSourceException("Elasticsearch answered " + failure.getStatusCode().value() + ": "
                                + error(failure.getBody().readAllBytes()), null);
                    })
                    .body(JsonNode.class), "Elasticsearch answered no body");
        } catch (ResourceAccessException e) {
            throw new LogsSourceException("Elasticsearch at " + uri + " did not answer", e);
        }
    }

    /**
     * The first root cause's reason, which tells more than a search's "all shards failed".
     */
    private String error(byte[] body) {
        try {
            JsonNode error = jsonMapper.readTree(body).path("error");
            JsonNode rootCause = error.path("root_cause").path(0).path("reason");
            return rootCause.isString() ? rootCause.asString() : error.path("reason").asString();
        } catch (RuntimeException notJson) {
            return new String(body, StandardCharsets.UTF_8).strip();
        }
    }

    private static List<LogEntry> entries(JsonNode response) {
        return response.path("hits").path("hits").valueStream()
                .map(hit -> hit.path("fields"))
                .map(fields -> LogEntry.builder()
                        .timestamp(Instant.parse(Objects.requireNonNull(text(fields, TIMESTAMP), "a hit without @timestamp")))
                        .group(text(fields, GROUP))
                        .sessionId(text(fields, SESSION))
                        .type(type(text(fields, LOG_TYPE)))
                        .line(Objects.requireNonNullElse(text(fields, BODY), ""))
                        .build())
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
