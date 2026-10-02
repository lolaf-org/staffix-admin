package org.lolaf.staffix.admin.logs.loki;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
public class LokiLogsAutoConfiguration {

    @Bean
    LokiLogsSourceFactory lokiLogsSourceFactory(RestClient.Builder builder) {
        return new LokiLogsSourceFactory(builder);
    }
}
