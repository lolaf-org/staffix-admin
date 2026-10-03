package org.lolaf.staffix.admin.demo;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
public class DemoConnectorAutoConfiguration {

    @Bean
    DemoMetricsSourceFactory demoMetricsSourceFactory(RestClient.Builder builder) {
        return new DemoMetricsSourceFactory(builder);
    }

    @Bean
    DemoLogsSourceFactory demoLogsSourceFactory(RestClient.Builder builder) {
        return new DemoLogsSourceFactory(builder);
    }
}
