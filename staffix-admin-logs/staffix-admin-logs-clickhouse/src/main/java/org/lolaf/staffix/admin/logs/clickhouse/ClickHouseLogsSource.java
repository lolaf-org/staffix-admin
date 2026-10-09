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
package org.lolaf.staffix.admin.logs.clickhouse;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.clickhouse.ClickHouseClient;
import org.lolaf.staffix.admin.clickhouse.ClickHouseParameters;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceException;
import org.lolaf.staffix.api.monitoring.FixMessageDirection;
import org.lolaf.staffix.api.monitoring.FixMonitoringAttributes;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.lolaf.staffix.admin.clickhouse.ClickHouseQueries.GROUP;
import static org.lolaf.staffix.admin.clickhouse.ClickHouseQueries.SESSION;
import static org.lolaf.staffix.admin.clickhouse.ClickHouseQueries.attribute;
import static org.lolaf.staffix.admin.clickhouse.ClickHouseQueries.scope;

/**
 * Reads the FIX message logs staffix's OTLP logger sends to ClickHouse through the OpenTelemetry collector's
 * {@code clickhouse} exporter. Text and ids are first looked up by their whole tokens, which the exporter's
 * {@code tokenbf_v1} index on {@code lower(Body)} answers without reading every line, then matched exactly.
 */
public class ClickHouseLogsSource implements LogsSource {

    private static final String ATTRIBUTES = "LogAttributes";
    private static final String LOWER_BODY = "lower(Body)";
    private static final String DIRECTION = FixMonitoringAttributes.FIX_MESSAGE_DIRECTION.getKey();
    private static final String MSG_TYPE = FixMonitoringAttributes.FIX_MESSAGE_TYPE.getKey();
    private static final String REGEX_SPECIALS = "\\.+*?()|[]{}^$";

    private final ClickHouseClient client;
    private final String table;

    public ClickHouseLogsSource(ClickHouseLogsSettings settings, RestClient.Builder builder, JsonMapper jsonMapper) {
        this.client = new ClickHouseClient(settings, builder, jsonMapper, LogsSourceException::new);
        this.table = settings.getTable();
    }

    @Override
    public void testConnection() {
        client.testConnection(Set.of(table));
    }

    @Override
    public List<LogEntry> query(LogQuery query) {
        ClickHouseParameters parameters = new ClickHouseParameters();
        String sql = "SELECT toUnixTimestamp64Nano(Timestamp) AS nanos, "
                + attribute(ATTRIBUTES, GROUP, parameters) + " AS sg, "
                + attribute(ATTRIBUTES, SESSION, parameters) + " AS sn, "
                + attribute(ATTRIBUTES, DIRECTION, parameters) + " AS md, "
                + "Body AS line FROM " + parameters.add("Identifier", table)
                + " WHERE " + String.join(" AND ", conditions(query, parameters))
                + " ORDER BY Timestamp DESC LIMIT " + parameters.add("UInt32", query.getLimit());
        return client.query(sql, parameters).stream().map(ClickHouseLogsSource::entry).toList();
    }

    private static List<String> conditions(LogQuery query, ClickHouseParameters parameters) {
        List<String> conditions = new ArrayList<>();
        conditions.add("Timestamp >= fromUnixTimestamp64Nano(" + parameters.add("Int64", nanos(query.getFrom())) + ")");
        conditions.add("Timestamp < fromUnixTimestamp64Nano(" + parameters.add("Int64", nanos(query.getTo())) + ")");
        conditions.addAll(scope(ATTRIBUTES, query.getEngineId(), query.getGroup(), query.getSessionId(), parameters));
        if (!query.getTypes().isEmpty()) {
            conditions.add(attribute(ATTRIBUTES, DIRECTION, parameters) + " IN ("
                    + parameters.strings(query.getTypes().stream().map(ClickHouseLogsSource::directionValue).toList()) + ")");
        }
        if (!query.getMsgTypes().isEmpty()) {
            conditions.add(attribute(ATTRIBUTES, MSG_TYPE, parameters) + " IN (" + parameters.strings(query.getMsgTypes()) + ")");
        }
        if (query.getText() != null) {
            tokenLookup(query.getText(), false, parameters).ifPresent(conditions::add);
            conditions.add("position(Body, " + parameters.string(query.getText()) + ") > 0");
        }
        if (!query.getFieldValues().isEmpty()) {
            fieldValuesLookup(query.getFieldValues(), parameters).ifPresent(conditions::add);
            conditions.add("match(Body, " + parameters.string(fieldValuesPattern(query.getFieldValues())) + ")");
        }
        return conditions;
    }

    /**
     * Any value's tokens, all of a field value's being whole as fields are separated by non-token characters.
     */
    private static Optional<String> fieldValuesLookup(Set<String> values, ClickHouseParameters parameters) {
        List<String> lookups = new ArrayList<>();
        for (String value : values.stream().sorted().toList()) {
            Optional<String> lookup = tokenLookup(value, true, parameters);
            if (lookup.isEmpty()) {
                return Optional.empty();
            }
            lookups.add(lookup.get());
        }
        return Optional.of(lookups.size() == 1 ? lookups.getFirst() : "(" + String.join(" OR ", lookups) + ")");
    }

    /**
     * The tokens the text holds whole: one at an edge of a text searched as a substring may be part of a longer token
     * of the line.
     */
    private static Optional<String> tokenLookup(String text, boolean edgesWhole, ClickHouseParameters parameters) {
        List<String> tokens = tokens(text.toLowerCase(Locale.ROOT));
        if (!edgesWhole && !tokens.isEmpty() && isTokenChar(text.charAt(0))) {
            tokens = tokens.subList(1, tokens.size());
        }
        if (!edgesWhole && !tokens.isEmpty() && isTokenChar(text.charAt(text.length() - 1))) {
            tokens = tokens.subList(0, tokens.size() - 1);
        }
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tokens.stream()
                .map(token -> "hasToken(" + LOWER_BODY + ", " + parameters.string(token) + ")")
                .collect(Collectors.joining(" AND ", "(", ")")));
    }

    /**
     * As ClickHouse splits a string into tokens: on every ASCII character that is not a letter or a digit.
     */
    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= text.length(); i++) {
            boolean inToken = i < text.length() && isTokenChar(text.charAt(i));
            if (inToken && start < 0) {
                start = i;
            } else if (!inToken && start >= 0) {
                tokens.add(text.substring(start, i));
                start = -1;
            }
        }
        return tokens;
    }

    private static boolean isTokenChar(char c) {
        return c >= 128 || Character.isLetterOrDigit(c);
    }

    /**
     * A field equal to one of the values, between separators ({@code |} or SOH), as {@link LogQuery#hasFieldValue}.
     */
    private static String fieldValuesPattern(Set<String> values) {
        return "(^|[|\\x01])[0-9]+=(" + values.stream().sorted().map(ClickHouseLogsSource::regexQuoted).collect(Collectors.joining("|"))
                + ")([|\\x01]|$)";
    }

    private static String regexQuoted(String value) {
        StringBuilder quoted = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (REGEX_SPECIALS.indexOf(c) >= 0) {
                quoted.append('\\');
            }
            quoted.append(c);
        }
        return quoted.toString();
    }

    private static long nanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    private static LogEntry entry(JsonNode row) {
        return LogEntry.builder()
                .timestamp(Instant.ofEpochSecond(0, row.path("nanos").asLong()))
                .group(blankAsNull(row.path("sg").asString()))
                .sessionId(blankAsNull(row.path("sn").asString()))
                .type(LogType.ofDirectionValue(row.path("md").asString()))
                .line(row.path("line").asString())
                .build();
    }

    /**
     * A map answers an empty string for a key it does not hold.
     */
    private static @Nullable String blankAsNull(String value) {
        return value.isEmpty() ? null : value;
    }

    // an event has no fix.md, which the map lookup answers as the empty string
    private static String directionValue(LogType type) {
        FixMessageDirection direction = type.getDirection();
        return direction == null ? "" : direction.getValue();
    }
}
