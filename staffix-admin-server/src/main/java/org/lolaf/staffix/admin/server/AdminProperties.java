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
package org.lolaf.staffix.admin.server;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;

@Value
@ConfigurationProperties("staffix.admin")
public class AdminProperties {
    /**
     * An engine silent for longer is shown unreachable. Several times the engines' announce interval, so one lost
     * announcement does not flag it.
     */
    Duration engineHeartbeatTimeout;
    /**
     * How long a call to an engine may take, connecting included, before the operator is told it did not answer.
     */
    Duration engineRequestTimeout;
    /**
     * Where the console keeps what admins configure, such as its connectors; relative to the working directory.
     */
    Path dataDir;
    /**
     * A Spring Boot SSL bundle whose truststore trusts the engines' HTTPS certificates; unset uses the JVM's default
     * truststore.
     */
    @Nullable
    String engineSslBundle;
    /**
     * Refuses engines announcing a plain {@code http} URL, as the console would send them their token in clear.
     * Off by default so a local demo works without certificates.
     */
    boolean requireEngineTls;

    public AdminProperties(@DefaultValue("90s") Duration engineHeartbeatTimeout,
                           @DefaultValue("5s") Duration engineRequestTimeout,
                           @DefaultValue("data") Path dataDir,
                           @Nullable String engineSslBundle,
                           @DefaultValue("false") boolean requireEngineTls) {
        this.engineHeartbeatTimeout = engineHeartbeatTimeout;
        this.engineRequestTimeout = engineRequestTimeout;
        this.dataDir = dataDir;
        this.engineSslBundle = engineSslBundle;
        this.requireEngineTls = requireEngineTls;
    }
}
