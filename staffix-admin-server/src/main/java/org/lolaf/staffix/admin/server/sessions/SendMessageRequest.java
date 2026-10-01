package org.lolaf.staffix.admin.server.sessions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class SendMessageRequest {
    @NotBlank
    String message;
    /**
     * The field separator used in {@code message}, so operators can paste {@code |} separated messages.
     */
    @NotNull
    @Builder.Default
    Character separator = '|';
    boolean possDup;
}
