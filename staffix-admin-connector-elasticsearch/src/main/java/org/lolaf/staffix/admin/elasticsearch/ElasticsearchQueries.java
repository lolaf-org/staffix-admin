package org.lolaf.staffix.admin.elasticsearch;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.api.monitoring.FixMonitoringAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The query clauses the logs and the metrics searches share. The OTel mapping's attributes and resource attributes
 * are passthrough objects, so staffix's attributes are matched by their own names wherever staffix puts them.
 */
final class ElasticsearchQueries {

    static final String TIMESTAMP = "@timestamp";
    static final String GROUP = FixMonitoringAttributes.FIX_SESSION_GROUP.getKey();
    static final String SESSION = FixMonitoringAttributes.FIX_SESSION_NAME.getKey();
    private static final String ENGINE = FixMonitoringAttributes.FIX_ENGINE_ID.getKey();

    private ElasticsearchQueries() {
    }

    /**
     * The filters selecting an engine, one of its groups or one session of a group, to add more to.
     */
    static List<Object> scope(String engineId, @Nullable String group, @Nullable String sessionId) {
        List<Object> filters = new ArrayList<>();
        filters.add(term(ENGINE, engineId));
        if (group != null) {
            filters.add(term(GROUP, group));
        }
        if (sessionId != null) {
            filters.add(term(SESSION, sessionId));
        }
        return filters;
    }

    static Map<String, Object> term(String field, String value) {
        return Map.of("term", Map.of(field, value));
    }

    /**
     * From {@code from} included to {@code to} excluded, both in {@code format}.
     */
    static Map<String, Object> timeRange(Object from, Object to, String format) {
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("gte", from);
        range.put("lt", to);
        range.put("format", format);
        return Map.of("range", Map.of(TIMESTAMP, range));
    }
}
