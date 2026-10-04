package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Value
@Builder
public class SessionDetail {
    SessionSummary summary;
    /**
     * Of the running config; each config keeps its own.
     */
    long incomingSeqNum;
    long outgoingSeqNum;
    /**
     * In the order of {@link SessionSummary#getConfigIds()}.
     */
    @Singular
    List<SessionConfig> configs;
    /**
     * The engine's session settings store holding the session's settings; null when none does.
     */
    @Nullable
    String sessionSettingsStore;
}
