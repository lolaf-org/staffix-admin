package org.lolaf.staffix.admin.elasticsearch;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Duration;

/**
 * What the logs and the metrics settings both say about reaching Elasticsearch.
 */
interface ElasticsearchConnection {

    URI getUrl();

    Duration getTimeout();

    String getIndex();

    @Nullable
    String getApiKey();

    @Nullable
    String getUsername();

    @Nullable
    String getPassword();
}
