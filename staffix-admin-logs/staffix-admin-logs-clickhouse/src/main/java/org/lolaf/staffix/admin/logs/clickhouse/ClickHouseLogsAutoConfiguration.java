package org.lolaf.staffix.admin.logs.clickhouse;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
public class ClickHouseLogsAutoConfiguration {

    @Bean
    ClickHouseLogsSourceFactory clickHouseLogsSourceFactory(RestClient.Builder builder, JsonMapper jsonMapper) {
        return new ClickHouseLogsSourceFactory(builder, jsonMapper);
    }
}
