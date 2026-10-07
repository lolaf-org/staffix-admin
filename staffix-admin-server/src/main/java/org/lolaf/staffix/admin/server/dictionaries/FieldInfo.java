package org.lolaf.staffix.admin.server.dictionaries;

import lombok.Value;

import java.util.Map;

@Value
public class FieldInfo {
    int tag;
    String name;
    /**
     * Enum value to its description, e.g. {@code 2 -> FILLED} for OrdStatus(39); empty for a free value.
     */
    Map<String, String> values;
}
