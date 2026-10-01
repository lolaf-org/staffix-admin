package org.lolaf.staffix.admin.server.engines;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionDetail;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Operations on a registered engine's sessions. Failures are {@link GatewayException}s.
 */
public interface EngineGateway {

    /**
     * Sorted by group, then by session id within a group.
     */
    List<SessionGroup> listSessions(String instanceId);

    SessionDetail getSession(String instanceId, String sessionId);

    JsonNode getSessionSettings(String instanceId, String sessionId);

    void logon(String instanceId, String sessionId);

    void logout(String instanceId, String sessionId);

    void reset(String instanceId, String sessionId, ResetMode mode);

    /**
     * Unlike the other operations, {@code sessionId} names an initiator target, which is not a managed session
     * until it becomes the active one.
     */
    void activateInitiatorTarget(String instanceId, String sessionId);

    void setSeqNums(String instanceId, String sessionId, @Nullable Long incoming, @Nullable Long outgoing);

    void sendMessage(String instanceId, String sessionId, String message, char separator, boolean possDup);
}
