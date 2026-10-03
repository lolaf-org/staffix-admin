package org.lolaf.staffix.admin.audit;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A change an operator made, or tried to make, through the console. Never holds a secret: connector changes name
 * what changed, not the values.
 */
@Value
@Builder
public class AuditEntry {
    Instant at;
    /**
     * The operator's login, {@code ?} when unauthenticated.
     */
    String user;
    /**
     * What was done, e.g. {@code logon} or {@code connectors: added connector x}.
     */
    String operation;
    AuditOutcome outcome;
    /**
     * Why it failed; null when it succeeded.
     */
    @Nullable
    String reason;
    /**
     * The session acted on, null for a change that is not a session's, such as the connectors.
     */
    @Nullable
    String engineId;
    @Nullable
    String group;
    @Nullable
    String sessionId;
}
