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
package org.lolaf.staffix.admin.logs.loki;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.connector.Description;
import org.lolaf.staffix.admin.connector.Secret;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

@Value
public class LokiSettings {
    @Description("The Loki server, e.g. http://localhost:3100.")
    URI url;
    @Description("How long a query may take, connecting included, before the console reports Loki did not answer.")
    Duration timeout;
    @Description("Loki needs a stream selector before any filter. The default scans every stream of the time range; "
            + "narrow it, e.g. {service_name=\"fix-gateway\"}, or index fix_eid (see indexedLabels).")
    String streamSelector;
    @Description("The labels Loki's otlp_config promotes to index labels (fix_eid for fix.eid, ...). They are matched in "
            + "the stream selector, so Loki reads only their streams; the others are filtered after reading. Indexing "
            + "fix_eid, fix_sg and fix_sn keeps engine, group and session queries fast.")
    Set<LokiLabel> indexedLabels;
    @Description("Sent as X-Scope-OrgID to a multi-tenant Loki; leave empty for a single-tenant one.")
    @Nullable
    @Secret
    String tenant;

    public LokiSettings(URI url,
                        @DefaultValue("10s") Duration timeout,
                        @DefaultValue("{service_name=~\".+\"}") String streamSelector,
                        @DefaultValue Set<LokiLabel> indexedLabels,
                        @Nullable String tenant) {
        this.url = url;
        this.timeout = timeout;
        this.streamSelector = streamSelector;
        this.indexedLabels = indexedLabels;
        this.tenant = tenant;
    }
}
