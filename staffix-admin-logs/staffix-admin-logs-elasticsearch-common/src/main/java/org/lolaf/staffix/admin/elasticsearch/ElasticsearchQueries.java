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
public final class ElasticsearchQueries {

    public static final String TIMESTAMP = "@timestamp";
    public static final String GROUP = FixMonitoringAttributes.FIX_SESSION_GROUP.getKey();
    public static final String SESSION = FixMonitoringAttributes.FIX_SESSION_NAME.getKey();
    private static final String ENGINE = FixMonitoringAttributes.FIX_ENGINE_ID.getKey();

    private ElasticsearchQueries() {
    }

    /**
     * The filters selecting an engine, one of its groups or one session of a group, to add more to.
     */
    public static List<Object> scope(String engineId, @Nullable String group, @Nullable String sessionId) {
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

    public static Map<String, Object> term(String field, String value) {
        return Map.of("term", Map.of(field, value));
    }

    /**
     * From {@code from} included to {@code to} excluded, both in {@code format}.
     */
    public static Map<String, Object> timeRange(Object from, Object to, String format) {
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("gte", from);
        range.put("lt", to);
        range.put("format", format);
        return Map.of("range", Map.of(TIMESTAMP, range));
    }
}
