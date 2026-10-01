package org.lolaf.staffix.admin.server.dictionaries;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * What decoding needs from a dictionary: field names and the meaning of their values, message names, and which
 * tags open a repeating group.
 */
@Value
@Builder
public class FixDictionary {
    String id;
    @Singular
    Map<Integer, Field> fields;
    @Singular
    Map<String, Message> messages;
    Structure header;
    Structure trailer;

    @Value
    @Builder
    public static class Field {
        int tag;
        String name;
        String type;
        /**
         * Enum value to its description, e.g. {@code 1 -> BUY} for Side(54).
         */
        @Singular
        Map<String, String> values;
    }

    @Value
    public static class Message {
        String msgType;
        String name;
        Structure body;
    }

    /**
     * The fields of a message, header, trailer or group, components already expanded.
     */
    @Value
    @Builder
    public static class Structure {
        @Singular
        Set<Integer> tags;
        /**
         * Keyed by the group's counter tag, e.g. NoPartyIDs(453).
         */
        @Singular
        Map<Integer, Group> groups;
    }

    @Value
    public static class Group {
        int counterTag;
        /**
         * The first field of each repetition; seeing it again starts the next one.
         */
        int delimiterTag;
        Structure members;
    }

    public @Nullable Field field(int tag) {
        return fields.get(tag);
    }
}
