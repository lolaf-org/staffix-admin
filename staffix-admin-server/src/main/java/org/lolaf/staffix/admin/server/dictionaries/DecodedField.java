package org.lolaf.staffix.admin.server.dictionaries;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

@Value
@Builder
public class DecodedField {
    int tag;
    /**
     * Null when the dictionary does not know the tag.
     */
    @Nullable
    String name;
    String value;
    /**
     * The enum description of the value, e.g. {@code BUY} for {@code 54=1}; space separated values are described
     * one by one.
     */
    @Nullable
    String meaning;
    MessageSection section;
    /**
     * 0 outside repeating groups, 1 inside one, 2 inside a nested one.
     */
    int depth;
    /**
     * True on the first field of each repetition of a group.
     */
    boolean groupStart;
}
