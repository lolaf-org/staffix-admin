package org.lolaf.staffix.admin.server.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

import java.net.URI;

@Value
@Builder
@Jacksonized
public class EngineAnnouncement {
    @NotBlank
    String instanceId;
    /**
     * Where the engine's HTTP admin connector listens.
     */
    @NotNull
    URI baseUrl;
    /**
     * Bearer token the backend presents on every call to this engine.
     */
    @NotBlank
    String token;
    @Nullable
    String staffixVersion;
}
