package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * One of the configs a session can run: an acceptor's only one, an initiator's main or a backup.
 */
@Value
@Builder
public class SessionConfig {
    String id;
    /**
     * {@code host:port} an initiator dials, tried in turn; empty for an acceptor.
     */
    @Singular
    List<String> connectAddresses;
}
