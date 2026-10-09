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
