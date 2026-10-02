package org.lolaf.staffix.admin.logs.loki;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads the FIX message logs staffix's OTLP logger sends to Loki. Its {@code fix.*} attributes arrive as structured
 * metadata, filtered in the pipeline, unless the deployment promotes them to index labels, which are matched in the
 * stream selector instead.
 */
public class LokiLogsSource implements LogsSource {

    private static final Pattern REGEX_METACHARACTER = Pattern.compile("[\\\\.+*?()|\\[\\]{}^$]");
    private static final Map<LogType, String> LOG_TYPES = Map.of(LogType.IN, "in", LogType.OUT, "out", LogType.EVENT, "event");

    private final RestClient client;
    private final URI queryRangeUrl;
    private final String streamSelectorMatchers;
    private final Set<String> indexedLabels;

    public LokiLogsSource(LokiSettings properties, RestClient.Builder builder) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build());
        requestFactory.setReadTimeout(properties.getTimeout());
        if (properties.getTenant() != null) {
            builder.defaultHeader("X-Scope-OrgID", properties.getTenant());
        }
        this.client = builder.requestFactory(requestFactory).build();
        this.queryRangeUrl = URI.create(properties.getUrl().toString().replaceAll("/+$", "") + "/loki/api/v1/query_range");
        String streamSelector = properties.getStreamSelector().strip();
        this.streamSelectorMatchers = streamSelector.substring(1, streamSelector.length() - 1).strip();
        this.indexedLabels = properties.getIndexedLabels();
    }

    @Override
    public List<LogEntry> query(LogQuery query) {
        URI uri = UriComponentsBuilder.fromUri(queryRangeUrl)
                .queryParam("query", "{query}")
                .queryParam("start", nanos(query.getFrom()))
                .queryParam("end", nanos(query.getTo()))
                .queryParam("limit", query.getLimit())
                .queryParam("direction", "backward")
                .build(Map.of("query", logQl(query)));
        JsonNode response;
        try {
            response = client.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, failure) -> {
                        throw new LogsSourceException("Loki answered " + failure.getStatusCode().value() + ": "
                                + new String(failure.getBody().readAllBytes(), StandardCharsets.UTF_8).strip(), null);
                    })
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new LogsSourceException("Loki at " + queryRangeUrl + " did not answer", e);
        }
        return entries(Objects.requireNonNull(response, "Loki answered no body"), query.getLimit());
    }

    private String logQl(LogQuery query) {
        List<String> selector = new ArrayList<>();
        if (!streamSelectorMatchers.isEmpty()) {
            selector.add(streamSelectorMatchers);
        }
        StringBuilder pipeline = new StringBuilder();
        if (query.getText() != null) {
            pipeline.append(" |= ").append(quote(query.getText()));
        }
        match(selector, pipeline, "fix_eid", "=", query.getEngineId());
        if (query.getGroup() != null) {
            match(selector, pipeline, "fix_sg", "=", query.getGroup());
        }
        if (query.getSessionId() != null) {
            match(selector, pipeline, "fix_sn", "=", query.getSessionId());
        }
        if (!query.getTypes().isEmpty()) {
            match(selector, pipeline, "fix_log_type", "=~", anyOf(query.getTypes().stream().map(LOG_TYPES::get).toList()));
        }
        if (!query.getMsgTypes().isEmpty()) {
            match(selector, pipeline, "fix_msg_type", "=~", anyOf(query.getMsgTypes()));
        }
        return "{" + String.join(", ", selector) + "}" + pipeline;
    }

    private void match(List<String> selector, StringBuilder pipeline, String label, String operator, String value) {
        String matcher = label + operator + quote(value);
        if (indexedLabels.contains(label)) {
            selector.add(matcher);
        } else {
            pipeline.append(" | ").append(matcher);
        }
    }

    private static String anyOf(Collection<String> values) {
        return values.stream()
                .sorted()
                .map(value -> REGEX_METACHARACTER.matcher(value).replaceAll("\\\\$0"))
                .collect(Collectors.joining("|"));
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }

    private static String nanos(Instant instant) {
        return instant.getEpochSecond() + String.format("%09d", instant.getNano());
    }

    private static List<LogEntry> entries(JsonNode response, int limit) {
        return response.path("data").path("result").valueStream()
                .flatMap(stream -> {
                    JsonNode labels = stream.path("stream");
                    return stream.path("values").valueStream().map(value -> LogEntry.builder()
                            .timestamp(instant(value.get(0).asString()))
                            .group(text(labels, "fix_sg"))
                            .sessionId(text(labels, "fix_sn"))
                            .type(type(text(labels, "fix_log_type")))
                            .line(value.get(1).asString())
                            .build());
                })
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .limit(limit)
                .toList();
    }

    private static @Nullable String text(JsonNode labels, String name) {
        JsonNode value = labels.get(name);
        return value == null ? null : value.asString();
    }

    private static @Nullable LogType type(@Nullable String logType) {
        return switch (logType) {
            case "in" -> LogType.IN;
            case "out" -> LogType.OUT;
            case "event" -> LogType.EVENT;
            case null, default -> null;
        };
    }

    private static Instant instant(String epochNanos) {
        int split = epochNanos.length() - 9;
        return Instant.ofEpochSecond(Long.parseLong(epochNanos.substring(0, split)), Long.parseLong(epochNanos.substring(split)));
    }
}
