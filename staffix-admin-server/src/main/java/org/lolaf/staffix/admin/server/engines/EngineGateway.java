package org.lolaf.staffix.admin.server.engines;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionDetail;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Operations on a registered engine's sessions. Session operations act on the config the session runs: an
 * initiator's main or one of its backups. Failures are {@link GatewayException}s.
 */
public interface EngineGateway {

    /**
     * Sorted by group, then by session id within a group.
     */
    List<SessionGroup> listGroups(String engineId);

    SessionDetail getSession(SessionRef session);

    JsonNode getSessionSettings(SessionRef session);

    void logon(SessionRef session);

    void logout(SessionRef session);

    void reset(SessionRef session, ResetMode mode);

    /**
     * Makes an initiator run another of its configs, its main or a backup.
     */
    void activate(SessionRef session, String configId);

    void setSeqNums(SessionRef session, @Nullable Long incoming, @Nullable Long outgoing);

    void sendMessage(SessionRef session, String message, char separator, boolean possDup);
}
