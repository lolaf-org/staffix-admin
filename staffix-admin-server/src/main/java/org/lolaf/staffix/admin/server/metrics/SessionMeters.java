package org.lolaf.staffix.admin.server.metrics;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * A running session's meters, by the name of the config it runs.
 */
@Value
@Builder
@Jacksonized
public class SessionMeters {
    String group;
    String name;
    @Singular
    List<Meter> meters;
}
