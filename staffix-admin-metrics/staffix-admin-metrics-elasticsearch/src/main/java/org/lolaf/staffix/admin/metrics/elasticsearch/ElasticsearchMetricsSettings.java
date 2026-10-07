package org.lolaf.staffix.admin.metrics.elasticsearch;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.elasticsearch.ElasticsearchConnection;
import org.lolaf.staffix.admin.connector.Description;
import org.lolaf.staffix.admin.connector.Secret;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Value
public class ElasticsearchMetricsSettings implements ElasticsearchConnection {
    @Description("The Elasticsearch server, e.g. http://localhost:9200.")
    URI url;
    @Description("How long a query may take, connecting included, before the console reports Elasticsearch did not answer.")
    Duration timeout;
    @Description("The indices, data streams or patterns holding the metrics. The default matches what the OpenTelemetry "
            + "collector's elasticsearch exporter writes with its OTel mapping, e.g. metrics-generic.otel-default.")
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
    @Description("The engines' micrometer baseTimeUnit, for engines that do not report their monitoring plugin's: the "
            + "exported timers carry no unit Elasticsearch keeps. Micrometer's OTLP registry defaults to milliseconds.")
    TimeUnit timerBaseUnit;
    @Description("The shortest window a rate is computed over, whatever a chart asks: at least two export intervals, so "
            + "every window holds enough samples.")
    Duration minRateWindow;

    public ElasticsearchMetricsSettings(URI url,
                                        @DefaultValue("10s") Duration timeout,
                                        @DefaultValue("metrics-*.otel-*") String index,
                                        @Nullable String apiKey,
                                        @Nullable String username,
                                        @Nullable String password,
                                        @DefaultValue("milliseconds") TimeUnit timerBaseUnit,
                                        @DefaultValue("10s") Duration minRateWindow) {
        this.url = url;
        this.timeout = timeout;
        this.index = index;
        this.apiKey = apiKey;
        this.username = username;
        this.password = password;
        this.timerBaseUnit = timerBaseUnit;
        this.minRateWindow = minRateWindow;
    }
}
