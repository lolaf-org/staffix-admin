package org.lolaf.staffix.admin.metrics.elasticsearch;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.elasticsearch.ElasticsearchClient;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public class ElasticsearchMetricsSourceFactory implements MetricsSourceFactory<ElasticsearchMetricsSettings> {

    public static final String KIND = "elasticsearch-metrics";

    private final RestClient.Builder builder;
    private final JsonMapper jsonMapper;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public Class<ElasticsearchMetricsSettings> getSettingsClass() {
        return ElasticsearchMetricsSettings.class;
    }

    @Override
    public MetricsSource create(ElasticsearchMetricsSettings settings) {
        ElasticsearchClient.check(settings);
        return new ElasticsearchMetricsSource(settings, builder.clone(), jsonMapper);
    }
}
