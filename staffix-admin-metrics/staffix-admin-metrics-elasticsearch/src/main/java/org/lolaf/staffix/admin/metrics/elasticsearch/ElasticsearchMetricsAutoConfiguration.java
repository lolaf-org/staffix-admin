package org.lolaf.staffix.admin.metrics.elasticsearch;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
public class ElasticsearchMetricsAutoConfiguration {

    @Bean
    ElasticsearchMetricsSourceFactory elasticsearchMetricsSourceFactory(RestClient.Builder builder, JsonMapper jsonMapper) {
        return new ElasticsearchMetricsSourceFactory(builder, jsonMapper);
    }
}
