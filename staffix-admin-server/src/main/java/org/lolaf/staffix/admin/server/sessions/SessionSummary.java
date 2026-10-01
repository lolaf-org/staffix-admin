package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * One box of the overview: a FIX session of a group, with the state of the config it runs.
 */
@Value
@Builder
public class SessionSummary {
    String group;
    /**
     * Unique within the group only; an initiator's main config id.
     */
    String id;
    SessionType type;
    /**
     * The initiator or acceptor instance the session is bound to.
     */
    String instanceId;
    /**
     * The main config first, then an initiator's backups; an acceptor has only its own.
     */
    @Singular
    List<String> configIds;
    String runningConfigId;
    /**
     * Full staffix FixSessionId of the running config, e.g. {@code trading:FIX.4.4:SENDER->TARGET}.
     */
    String fixSessionId;
    SessionHealth health;
    boolean loggedIn;
    boolean connected;
    boolean withinSessionTime;
    SessionState desiredState;
}
