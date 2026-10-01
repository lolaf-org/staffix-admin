package org.lolaf.staffix.admin.server.sessions;

import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * A null value leaves that sequence number unchanged.
 */
@Value
@Builder
@Jacksonized
public class SeqNumsRequest {
    @Nullable
    @Positive
    Long incoming;
    @Nullable
    @Positive
    Long outgoing;
}
