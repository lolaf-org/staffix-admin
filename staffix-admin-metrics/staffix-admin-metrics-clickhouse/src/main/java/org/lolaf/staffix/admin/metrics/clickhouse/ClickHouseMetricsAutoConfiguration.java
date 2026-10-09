package org.lolaf.staffix.admin.metrics.clickhouse;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
public class ClickHouseMetricsAutoConfiguration {

    @Bean
    ClickHouseMetricsSourceFactory clickHouseMetricsSourceFactory(RestClient.Builder builder, JsonMapper jsonMapper) {
        return new ClickHouseMetricsSourceFactory(builder, jsonMapper);
    }
}
