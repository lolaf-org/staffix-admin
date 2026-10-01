package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * The parts of a staffix FixSessionId other than its group and id, sent apart rather than as its
 * {@code toString()} so the UI shows them without repeating the session id or parsing a format.
 */
@Value
@Builder
public class FixIdentity {
    /**
     * BeginString, e.g. {@code FIX.4.4} or {@code FIXT.1.1}.
     */
    String fixVersion;
    /**
     * DefaultApplVerID of a FIXT session, null otherwise.
     */
    @Nullable
    String defaultApplVerId;
    /**
     * Seen from this engine: the sender is the engine's side.
     */
    CompIds sender;
    CompIds target;
}
