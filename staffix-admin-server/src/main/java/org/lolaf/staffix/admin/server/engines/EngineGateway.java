package org.lolaf.staffix.admin.server.engines;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.dictionaries.DictionaryRef;
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

    /**
     * The engine's messages loggers and session plugins with their settings, as staffix's HTTP connector serves them
     * ({@code GET emitters}); static for the engine's life.
     */
    JsonNode getEmitters(String engineId);

    void logon(SessionRef session);

    void logout(SessionRef session);

    void reset(SessionRef session, ResetMode mode);

    /**
     * Makes an initiator run another of its configs, its main or a backup.
     */
    void activate(SessionRef session, String configId);

    void setSeqNums(SessionRef session, @Nullable Long incoming, @Nullable Long outgoing);

    void sendMessage(SessionRef session, String message, char separator, boolean possDup);

    /**
     * The dictionaries a config decodes with: one, or for FIXT the transport's then the application's. Unlike the
     * other operations, {@code config} names a config (an initiator's backup included), not a session.
     */
    List<DictionaryRef> getDictionaries(SessionRef config);

    byte[] getDictionaryXml(String engineId, String dictionaryId);
}
