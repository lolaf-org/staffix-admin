package org.lolaf.staffix.admin.server.dictionaries;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Value
@Builder
public class DecodedMessage {
    @Nullable
    String msgType;
    /**
     * Null when the dictionary does not know the message type.
     */
    @Nullable
    String msgTypeName;
    @Singular
    List<String> dictionaryIds;
    @Singular
    List<DecodedField> fields;
}
