package org.lolaf.staffix.admin.server.engines;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Instant;

/**
 * An engine as the backend knows it, from its latest announcement. Never sent to the browser.
 */
@Value
@Builder
public class RegisteredEngine {
    String instanceId;
    URI baseUrl;
    @ToString.Exclude
    String token;
    @Nullable
    String staffixVersion;
    Instant lastAnnouncedAt;
}
