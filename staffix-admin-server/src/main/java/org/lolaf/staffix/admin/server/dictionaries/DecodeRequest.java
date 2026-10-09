/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
