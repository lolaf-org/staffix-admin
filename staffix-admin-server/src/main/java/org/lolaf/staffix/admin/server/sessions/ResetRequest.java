package org.lolaf.staffix.admin.server.sessions;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ResetRequest {
    @NotNull
    ResetMode mode;
}
