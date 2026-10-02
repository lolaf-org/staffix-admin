package org.lolaf.staffix.admin.metrics.prometheus;

import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Backs off when the application defines its own {@link MetricsSource}, such as the simulator of the {@code mock}
 * profile.
 */
@AutoConfiguration
@ConditionalOnProperty("staffix.admin.metrics.prometheus.url")
@EnableConfigurationProperties(PrometheusProperties.class)
public class PrometheusMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MetricsSource.class)
    PrometheusMetricsSource prometheusMetricsSource(PrometheusProperties properties, RestClient.Builder builder,
                                                    JsonMapper jsonMapper) {
        return new PrometheusMetricsSource(properties, builder, jsonMapper);
    }
}
