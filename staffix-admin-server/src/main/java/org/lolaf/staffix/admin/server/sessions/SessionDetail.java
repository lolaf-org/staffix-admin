package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SessionDetail {
    SessionSummary summary;
    long incomingSeqNum;
    long outgoingSeqNum;
    /**
     * Initiators listing this session as a target; empty when it has no backup configuration.
     */
    @Singular
    List<InitiatorTargets> initiators;
}
