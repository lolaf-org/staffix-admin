package org.lolaf.staffix.admin.metrics.prometheus;

import lombok.Value;
import org.lolaf.staffix.admin.connector.Description;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Value
public class PrometheusSettings {
    @Description("The Prometheus server, e.g. http://localhost:9090.")
    URI url;
    @Description("How long a query may take, connecting included, before the console reports Prometheus did not answer.")
    Duration timeout;
    @Description("The engines' micrometer baseTimeUnit, for engines that do not report their monitoring plugin's: the "
            + "exported timers carry no unit Prometheus keeps. Micrometer's OTLP registry defaults to milliseconds.")
    TimeUnit timerBaseUnit;
    @Description("The shortest window a rate is computed over: a few scrape intervals, so every window holds enough samples.")
    Duration minRateWindow;

    public PrometheusSettings(URI url,
                              @DefaultValue("10s") Duration timeout,
                              @DefaultValue("milliseconds") TimeUnit timerBaseUnit,
                              @DefaultValue("1m") Duration minRateWindow) {
        this.url = url;
        this.timeout = timeout;
        this.timerBaseUnit = timerBaseUnit;
        this.minRateWindow = minRateWindow;
    }
}
