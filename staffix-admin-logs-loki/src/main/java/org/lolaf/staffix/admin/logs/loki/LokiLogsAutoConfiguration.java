package org.lolaf.staffix.admin.logs.loki;

import org.lolaf.staffix.admin.logs.LogsSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Backs off when the application defines its own {@link LogsSource}, such as the simulator of the {@code mock}
 * profile.
 */
@AutoConfiguration
@ConditionalOnProperty("staffix.admin.logs.loki.url")
@EnableConfigurationProperties(LokiProperties.class)
public class LokiLogsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LogsSource.class)
    LokiLogsSource lokiLogsSource(LokiProperties properties, RestClient.Builder builder) {
        return new LokiLogsSource(properties, builder);
    }
}
