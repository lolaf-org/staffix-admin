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
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * What a session's settings can name in its engine, so the console offers choices the engine resolves.
 */
@Value
@Builder
@Jacksonized
public class SessionComponents {
    @Singular
    List<ApplicationFactory> applicationFactories;
    @Singular
    List<String> messagesStores;
    @Singular
    List<String> messagesLoggers;
    @Singular
    List<SessionsPlugin> sessionsPlugins;

    @Value
    @Builder
    @Jacksonized
    public static class ApplicationFactory {
        String instanceId;
        @Singular
        List<String> applicationIds;
    }

    @Value
    @Builder
    @Jacksonized
    public static class SessionsPlugin {
        String instanceId;
        /**
         * Full class names, the keys of a session's {@code fixSessionPluginsInstanceIds}.
         */
        @Singular
        List<String> pluginTypes;
    }
}
