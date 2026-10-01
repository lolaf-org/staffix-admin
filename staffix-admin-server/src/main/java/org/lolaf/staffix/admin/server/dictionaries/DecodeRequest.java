package org.lolaf.staffix.admin.server.dictionaries;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * A message to decode with the dictionaries of one session config.
 */
@Value
@Builder
@Jacksonized
public class DecodeRequest {
    @NotBlank
    String engineId;
    @NotBlank
    String group;
    /**
     * A config id of the group: an acceptor session's, an initiator's main or one of its backups.
     */
    @NotBlank
    String sessionId;
    @NotBlank
    String message;
    @NotNull
    @Builder.Default
    Character separator = '|';
}
