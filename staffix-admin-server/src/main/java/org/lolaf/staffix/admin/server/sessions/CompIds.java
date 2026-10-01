package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * One side of a FIX session: SenderCompID(49) / TargetCompID(56) and their optional sub and location ids.
 */
@Value
@Builder
public class CompIds {
    String compId;
    @Nullable
    String subId;
    @Nullable
    String locationId;
}
