package org.lolaf.staffix.admin.metrics.prometheus;

import lombok.Value;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Value
public class PrometheusSettings {
    /**
     * The Prometheus server, e.g. {@code http://localhost:9090}; required.
     */
    URI url;
    Duration timeout;
    /**
     * The engines' micrometer {@code baseTimeUnit}. The exported timers carry no unit Prometheus keeps, so it must
     * match the engines' registry; micrometer's OTLP registry defaults to milliseconds.
     */
    TimeUnit timerBaseUnit;
    /**
     * The shortest window a rate is computed over: a few scrape intervals, so every window holds enough samples.
     */
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
