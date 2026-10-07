package org.lolaf.staffix.admin.store;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * How the order trace follows an id and what it shows of each message. A null list means the console's defaults, an
 * empty one none: no linking tags, the trace stops at the messages carrying the id asked.
 */
@Value
@Builder(toBuilder = true)
public class TraceConfig {
    /**
     * The tags whose values tie the messages of one order or quote together, e.g. ClOrdID(11), OrderID(37).
     */
    @Nullable
    List<Integer> linkingTags;
    /**
     * The fields shown for each message, in this order.
     */
    @Nullable
    List<TraceField> keyFields;
}
