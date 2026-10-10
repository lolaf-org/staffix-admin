/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lolaf.staffix.admin.server.engines;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.alerts.ActiveAlert;
import org.lolaf.staffix.admin.server.alerts.SessionProbe;
import org.lolaf.staffix.admin.server.dictionaries.DictionaryRef;
import org.lolaf.staffix.admin.server.sessions.ApplicationSetting;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionDetail;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.lolaf.staffix.admin.server.metrics.SessionMeters;
import org.lolaf.staffix.admin.server.sessions.SessionComponents;
import org.lolaf.staffix.admin.server.sessions.SessionSettingsStore;
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

    /**
     * The session's settings as a document of the engine's session settings schema, secret values masked.
     */
    JsonNode getSessionSettings(SessionRef session);

    /**
     * Replaces the session's settings; a value sent back masked keeps the session's.
     */
    void updateSessionSettings(SessionRef session, JsonNode settings);

    /**
     * Removes the session from the store holding its settings, which stops it.
     */
    void removeSession(SessionRef session);

    /**
     * Adds a session to one of the engine's session settings stores, which starts it.
     */
    void addSession(String engineId, String sessionSettingsStore, JsonNode settings);

    /**
     * The JSON Schema of the engine's session settings documents, each field described.
     */
    JsonNode getSessionSettingsSchema(String engineId);

    List<SessionSettingsStore> listSessionSettingsStores(String engineId);

    SessionComponents getSessionComponents(String engineId);

    /**
     * Every running session's meters, by the name of the config it runs.
     */
    List<SessionMeters> getMeters(String engineId);

    List<ApplicationSetting> getApplicationSettings(SessionRef session);

    /**
     * The engine's messages loggers and session plugins with their settings, as staffix's HTTP connector serves them
     * ({@code GET components}); static for the engine's life.
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

    /**
     * Sends the messages in order, stopping at the first the engine refuses: those before it are sent.
     */
    void sendMessages(SessionRef session, List<String> messages, char separator, boolean possDup);

    /**
     * The dictionaries a config decodes with: one, or for FIXT the transport's then the application's. Unlike the
     * other operations, {@code config} names a config (an initiator's backup included), not a session.
     */
    List<DictionaryRef> getDictionaries(SessionRef config);

    byte[] getDictionaryXml(String engineId, String dictionaryId);

    /**
     * The engine's ongoing alerts, the worst first then the oldest; read again only when the engine reports a change.
     */
    List<ActiveAlert> listAlerts(String engineId);

    /**
     * The session's probes in the order they were added, each with its live state.
     */
    List<SessionProbe> getProbes(SessionRef session);
}
