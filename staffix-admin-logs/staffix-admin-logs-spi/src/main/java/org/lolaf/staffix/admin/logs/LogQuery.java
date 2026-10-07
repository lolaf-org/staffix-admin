package org.lolaf.staffix.admin.logs;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

@Value
@Builder
public class LogQuery {
    String engineId;
    /**
     * Null for every group of the engine.
     */
    @Nullable
    String group;
    /**
     * A config id within {@link #getGroup()}, which it requires (ids are unique per group only); null for every
     * session of the group.
     */
    @Nullable
    String sessionId;
    Instant from;
    Instant to;
    /**
     * Text the entry must contain, case sensitive as most log backends are; null for any.
     */
    @Nullable
    String text;
    /**
     * Empty for every type.
     */
    @Singular
    Set<LogType> types;
    /**
     * MsgType(35) values the message must have, e.g. {@code D} and {@code 8}; empty for any. Session events have
     * no MsgType and never match a non-empty set.
     */
    @Singular
    Set<String> msgTypes;
    /**
     * Values some field of the message, whatever its tag, must equal exactly, e.g. a ClOrdID; empty for any. Fields
     * are separated by {@code |} or SOH, as staffix's logger leaves SOH unless told to replace it.
     */
    @Singular
    Set<String> fieldValues;
    int limit;

    /**
     * Whether {@code line} has a field equal to one of {@link #getFieldValues()}, for the backends that can only
     * approximate that match; true when there are none.
     */
    public boolean hasFieldValue(String line) {
        if (fieldValues.isEmpty()) {
            return true;
        }
        for (int start = 0; start <= line.length(); ) {
            int end = fieldEnd(line, start);
            int equals = line.indexOf('=', start);
            if (equals > start && equals < end && isTag(line, start, equals) && isFieldValue(line, equals + 1, end)) {
                return true;
            }
            start = end + 1;
        }
        return false;
    }

    private boolean isFieldValue(String line, int start, int end) {
        for (String value : fieldValues) {
            if (value.length() == end - start && line.startsWith(value, start)) {
                return true;
            }
        }
        return false;
    }

    private static int fieldEnd(String line, int start) {
        for (int i = start; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '|' || c == '\u0001') {
                return i;
            }
        }
        return line.length();
    }

    private static boolean isTag(String line, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!Character.isDigit(line.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
