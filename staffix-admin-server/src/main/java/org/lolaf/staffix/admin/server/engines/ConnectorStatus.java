package org.lolaf.staffix.admin.server.engines;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * What an engine's HTTP admin connector answers to {@code GET status}, as it sends it: the live part of its
 * sessions, described by the {@link ConnectorSessions} of version {@link #sessionsVersion}.
 */
@Value
@Builder
@Jacksonized
class ConnectorStatus {
    String engineId;
    String sessionsVersion;
    @Singular
    List<Session> sessions;

    @Value
    @Builder
    @Jacksonized
    static class Session {
        String group;
        String name;
        String selectedConfig;
        String desiredState;
        String status;
        long incomingSeqNum;
        long outgoingSeqNum;
    }
}
