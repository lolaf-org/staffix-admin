package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

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
     * Of the selected config: a backup may connect with other comp ids.
     */
    FixIdentity identity;
    SessionStatus status;
    SessionDesiredState desiredState;
    /**
     * The engine's messages logger instance the session's logs go through, the same for all its configs; it selects
     * the connector they are read from.
     */
    String messagesLoggerInstanceId;
    /**
     * The engine's monitoring plugin instance the session's metrics go through, null when it is not monitored; it
     * selects the connector they are read from.
     */
    @Nullable
    String monitoringInstanceId;
}
