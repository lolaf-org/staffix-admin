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
    String engineId;
    URI baseUrl;
    @ToString.Exclude
    String token;
    @Nullable
    String staffixVersion;
    Instant lastAnnouncedAt;
}
