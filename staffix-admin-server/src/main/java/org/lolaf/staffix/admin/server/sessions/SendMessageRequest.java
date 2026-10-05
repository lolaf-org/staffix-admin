package org.lolaf.staffix.admin.server.sessions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class SendMessageRequest {
    /**
     * Sent in order; the engine stops at the first it refuses.
     */
    @NotEmpty
    @Singular
    List<@NotBlank String> messages;
    /**
     * The field separator used in {@code messages}, so operators can paste {@code |} separated messages.
     */
    @NotNull
    @Builder.Default
    Character separator = '|';
    boolean possDup;
}
