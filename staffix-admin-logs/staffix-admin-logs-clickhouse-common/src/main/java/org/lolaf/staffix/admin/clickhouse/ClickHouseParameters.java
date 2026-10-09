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
