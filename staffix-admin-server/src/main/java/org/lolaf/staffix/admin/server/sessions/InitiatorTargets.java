package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class InitiatorTargets {
    String initiatorId;
    String activeSessionId;
    /**
     * Main target first, then the backups.
     */
    @Singular
    List<InitiatorTarget> targets;
}
