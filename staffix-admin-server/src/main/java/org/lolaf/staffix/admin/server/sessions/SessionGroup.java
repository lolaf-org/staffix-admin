package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SessionGroup {
    String group;
    @Singular
    List<SessionSummary> sessions;
}
