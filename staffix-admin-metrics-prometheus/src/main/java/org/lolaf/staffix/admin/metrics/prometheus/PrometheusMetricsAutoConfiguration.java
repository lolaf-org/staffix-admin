package org.lolaf.staffix.admin.metrics.prometheus;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
public class PrometheusMetricsAutoConfiguration {

    @Bean
    PrometheusMetricsSourceFactory prometheusMetricsSourceFactory(RestClient.Builder builder, JsonMapper jsonMapper) {
        return new PrometheusMetricsSourceFactory(builder, jsonMapper);
    }
}
