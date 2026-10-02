package org.lolaf.staffix.admin.logs.loki;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@Value
@ConfigurationProperties("staffix.admin.logs.loki")
public class LokiProperties {
    /**
     * The Loki server, e.g. {@code http://localhost:3100}; the source is only created when set.
     */
    URI url;
    Duration timeout;
    /**
     * Loki needs a stream selector before any filter. The default scans every stream of the time range; narrow it,
     * e.g. {@code {service_name="fix-gateway"}}, or promote {@code fix.eid} to an index label, when that is slow.
     */
    String streamSelector;
    /**
     * Sent as {@code X-Scope-OrgID} to a multi-tenant Loki; null for none.
     */
    @Nullable
    String tenant;

    public LokiProperties(URI url,
                          @DefaultValue("10s") Duration timeout,
                          @DefaultValue("{service_name=~\".+\"}") String streamSelector,
                          @Nullable String tenant) {
        this.url = url;
        this.timeout = timeout;
        this.streamSelector = streamSelector;
        this.tenant = tenant;
    }
}
