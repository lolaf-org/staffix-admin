package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class InitiatorTarget {
    String sessionId;
    /**
     * {@code host:port}, tried in turn.
     */
    @Singular
    List<String> connectAddresses;
}
