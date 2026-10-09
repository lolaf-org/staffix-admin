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
package org.lolaf.staffix.admin.logs.elasticsearch;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.elasticsearch.ElasticsearchConnection;
import org.lolaf.staffix.admin.connector.Description;
import org.lolaf.staffix.admin.connector.Secret;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@Value
public class ElasticsearchLogsSettings implements ElasticsearchConnection {
    @Description("The Elasticsearch server, e.g. http://localhost:9200.")
    URI url;
    @Description("How long a query may take, connecting included, before the console reports Elasticsearch did not answer.")
    Duration timeout;
    @Description("The indices, data streams or patterns holding the logs. The default matches what the OpenTelemetry "
            + "collector's elasticsearch exporter writes with its OTel mapping, e.g. logs-generic.otel-default.")
    String index;
    @Description("An Elasticsearch API key (the encoded form), sent as Authorization: ApiKey; leave empty to use a "
            + "username, or for a cluster without security.")
    @Nullable
    @Secret
    String apiKey;
    @Description("A user for basic authentication, with its password; leave empty to use an API key, or for a cluster "
            + "without security.")
    @Nullable
    String username;
    @Description("The password of the username.")
    @Nullable
    @Secret
    String password;

    public ElasticsearchLogsSettings(URI url,
                                     @DefaultValue("10s") Duration timeout,
                                     @DefaultValue("logs-*.otel-*") String index,
                                     @Nullable String apiKey,
                                     @Nullable String username,
                                     @Nullable String password) {
        this.url = url;
        this.timeout = timeout;
        this.index = index;
        this.apiKey = apiKey;
        this.username = username;
        this.password = password;
    }
}
