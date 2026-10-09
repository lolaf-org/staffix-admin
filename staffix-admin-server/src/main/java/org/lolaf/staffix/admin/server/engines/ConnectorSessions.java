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
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What an engine's HTTP admin connector answers to {@code GET sessions}, as it sends it: what changes only with
 * its sessions or their settings.
 */
@Value
@Builder
@Jacksonized
class ConnectorSessions {
    String engineId;
    String version;
    @Singular
    List<Session> sessions;

    @Value
    @Builder
    @Jacksonized
    static class Session {
        String group;
        String name;
        String type;
        String instanceId;
        @Singular
        List<Config> configs;
        String messagesLoggerInstanceId;
        @Nullable
        String monitoringInstanceId;
        @Nullable
        String sessionSettingsStore;
    }

    @Value
    @Builder
    @Jacksonized
    static class Config {
        String name;
        @Singular
        List<String> connectAddresses;
        Identity identity;
        @Singular
        List<Dictionary> dictionaries;
    }

    @Value
    @Builder
    @Jacksonized
    static class Identity {
        String fixVersion;
        @Nullable
        String defaultApplVerId;
        CompIds sender;
        CompIds target;
    }

    @Value
    @Builder
    @Jacksonized
    static class CompIds {
        String compId;
        @Nullable
        String subId;
        @Nullable
        String locationId;
    }

    @Value
    @Builder
    @Jacksonized
    static class Dictionary {
        String id;
        String hash;
    }
}
