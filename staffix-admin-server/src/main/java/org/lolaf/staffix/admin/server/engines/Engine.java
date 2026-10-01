package org.lolaf.staffix.admin.server.engines;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An engine as the UI sees it: its URL and token stay in the backend.
 */
@Value
@Builder
public class Engine {
    String instanceId;
    @Nullable
    String staffixVersion;
    boolean reachable;
    Instant lastAnnouncedAt;
}
