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
package org.lolaf.staffix.admin.clickhouse;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.api.monitoring.FixMonitoringAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * The SQL clauses the logs and the metrics queries share, over the OpenTelemetry collector's {@code clickhouse}
 * exporter's tables. staffix's attributes are matched in the record's attributes or the resource's: logs carry them on
 * the resource, metrics on each data point.
 */
public final class ClickHouseQueries {

    public static final String GROUP = FixMonitoringAttributes.FIX_SESSION_GROUP.getKey();
    public static final String SESSION = FixMonitoringAttributes.FIX_SESSION_NAME.getKey();
    private static final String ENGINE = FixMonitoringAttributes.FIX_ENGINE_ID.getKey();
    private static final String RESOURCE_ATTRIBUTES = "ResourceAttributes";

    private ClickHouseQueries() {
    }

    /**
     * The conditions selecting an engine, one of its groups or one session of a group, to add more to.
     */
    public static List<String> scope(String attributes, String engineId, @Nullable String group, @Nullable String sessionId,
                                     ClickHouseParameters parameters) {
        List<String> conditions = new ArrayList<>();
        conditions.add(attributeEquals(attributes, ENGINE, engineId, parameters));
        if (group != null) {
            conditions.add(attributeEquals(attributes, GROUP, group, parameters));
        }
        if (sessionId != null) {
            conditions.add(attributeEquals(attributes, SESSION, sessionId, parameters));
        }
        return conditions;
    }

    /**
     * Written as two equalities rather than through {@link #attribute}, so the maps' bloom filter indexes apply.
     */
    public static String attributeEquals(String attributes, String key, String value, ClickHouseParameters parameters) {
        String keyParameter = parameters.string(key);
        String valueParameter = parameters.string(value);
        return "(" + attributes + "[" + keyParameter + "] = " + valueParameter + " OR " + RESOURCE_ATTRIBUTES + "["
                + keyParameter + "] = " + valueParameter + ")";
    }

    public static String attribute(String attributes, String key, ClickHouseParameters parameters) {
        String keyParameter = parameters.string(key);
        return "if(mapContains(" + attributes + ", " + keyParameter + "), " + attributes + "[" + keyParameter + "], "
                + RESOURCE_ATTRIBUTES + "[" + keyParameter + "])";
    }
}
