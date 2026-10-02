package org.lolaf.staffix.admin.logs.loki;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

@Value
public class LokiSettings {
    /**
     * The Loki server, e.g. {@code http://localhost:3100}; required.
     */
    URI url;
    Duration timeout;
    /**
     * Loki needs a stream selector before any filter. The default scans every stream of the time range; narrow it,
     * e.g. {@code {service_name="fix-gateway"}}, or index {@code fix.eid} (see {@link #getIndexedLabels()}).
     */
    String streamSelector;
    /**
     * The {@code fix_*} labels Loki's {@code otlp_config} promotes to index labels, e.g. {@code fix_eid}; they are
     * matched in the stream selector, so Loki reads only their streams. Any other label is filtered after reading.
     */
    Set<String> indexedLabels;
    /**
     * Sent as {@code X-Scope-OrgID} to a multi-tenant Loki; null for none.
     */
    @Nullable
    String tenant;

    public LokiSettings(URI url,
                        @DefaultValue("10s") Duration timeout,
                        @DefaultValue("{service_name=~\".+\"}") String streamSelector,
                        @DefaultValue Set<String> indexedLabels,
                        @Nullable String tenant) {
        this.url = url;
        this.timeout = timeout;
        this.streamSelector = streamSelector;
        this.indexedLabels = indexedLabels;
        this.tenant = tenant;
    }
}
