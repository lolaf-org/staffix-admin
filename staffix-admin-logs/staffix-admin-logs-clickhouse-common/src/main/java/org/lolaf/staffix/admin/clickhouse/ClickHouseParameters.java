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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Values ClickHouse binds by name on the server, so none is ever spliced into the SQL.
 */
public final class ClickHouseParameters {

    private final Map<String, String> values = new LinkedHashMap<>();

    /**
     * The placeholder of a new parameter, e.g. {@code {p0:String}}.
     */
    public String add(String type, Object value) {
        String name = "p" + values.size();
        values.put(name, escaped(String.valueOf(value)));
        return "{" + name + ":" + type + "}";
    }

    public String string(String value) {
        return add("String", value);
    }

    public String strings(Collection<String> values) {
        return values.stream().sorted().map(this::string).collect(Collectors.joining(", "));
    }

    Map<String, String> values() {
        return values;
    }

    /**
     * ClickHouse reads a parameter in its escaped text format, where a backslash starts an escape sequence.
     */
    private static String escaped(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }
}
