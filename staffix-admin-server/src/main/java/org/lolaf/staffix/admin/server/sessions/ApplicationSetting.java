package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * An application setting a session's application declares.
 */
@Value
@Builder
@Jacksonized
public class ApplicationSetting {
    String id;
    @Nullable
    String description;
    /**
     * Its value is masked in the session's settings, and kept when sent back masked.
     */
    boolean secret;
}
