package org.lolaf.staffix.admin.server.connectors;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;

/**
 * The engine instances a session's telemetry goes through, which select the connectors it is read from.
 *
 * @param monitoring null when the session is not monitored
 */
public record Emitters(String messagesLogger, @Nullable String monitoring) {

    public static Emitters of(SessionSummary session) {
        return new Emitters(session.getMessagesLoggerInstanceId(), session.getMonitoringInstanceId());
    }
}
