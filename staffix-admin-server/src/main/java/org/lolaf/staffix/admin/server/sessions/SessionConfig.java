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
package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * One of the configs a session can run: an acceptor's only one, an initiator's main or a backup.
 */
@Value
@Builder
public class SessionConfig {
    String id;
    /**
     * {@code host:port} an initiator dials, tried in turn; empty for an acceptor.
     */
    @Singular
    List<String> connectAddresses;
}
