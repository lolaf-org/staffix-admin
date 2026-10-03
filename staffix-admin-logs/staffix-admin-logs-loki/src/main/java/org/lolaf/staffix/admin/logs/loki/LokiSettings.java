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
