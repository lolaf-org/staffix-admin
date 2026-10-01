package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SessionSummary {
    String id;
    String group;
    /**
     * Full staffix FixSessionId, e.g. {@code ID:FIX.4.4:SENDER->TARGET}.
     */
    String fixSessionId;
    SessionType type;
    SessionHealth health;
    boolean loggedIn;
    boolean connected;
    boolean withinSessionTime;
    SessionState desiredState;
}
