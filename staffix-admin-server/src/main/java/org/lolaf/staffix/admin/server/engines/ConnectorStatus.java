package org.lolaf.staffix.admin.server.engines;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What an engine's HTTP admin connector answers to {@code GET status}, as it sends it.
 */
@Value
@Builder
@Jacksonized
class ConnectorStatus {
    String instanceId;
    @Singular
    List<Session> sessions;

    @Value
    @Builder
    @Jacksonized
    static class Session {
        String group;
        String name;
        String type;
        String instanceId;
        @Singular
        List<Config> configs;
        String selectedConfig;
        Identity identity;
        boolean loggedIn;
        boolean connected;
        boolean withinSessionTime;
        String desiredState;
        long incomingSeqNum;
        long outgoingSeqNum;
        @Singular
        List<Dictionary> dictionaries;
        String messagesLoggerInstanceId;
        @Nullable
        String monitoringInstanceId;
    }

    @Value
    @Builder
    @Jacksonized
    static class Config {
        String name;
        @Singular
        List<String> connectAddresses;
    }

    @Value
    @Builder
    @Jacksonized
    static class Identity {
        String fixVersion;
        @Nullable
        String defaultApplVerId;
        CompIds sender;
        CompIds target;
    }

    @Value
    @Builder
    @Jacksonized
    static class CompIds {
        String compId;
        @Nullable
        String subId;
        @Nullable
        String locationId;
    }

    @Value
    @Builder
    @Jacksonized
    static class Dictionary {
        String id;
        String hash;
    }
}
