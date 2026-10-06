package org.lolaf.staffix.admin.metrics.elasticsearch;

import lombok.RequiredArgsConstructor;
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
        if (settings.getUrl() == null) {
            throw new IllegalArgumentException("url is required");
        }
        if (settings.getApiKey() != null && settings.getUsername() != null) {
            throw new IllegalArgumentException("apiKey and username are exclusive");
        }
        if (settings.getUsername() != null && settings.getPassword() == null) {
            throw new IllegalArgumentException("password is required with username");
        }
        return new ElasticsearchMetricsSource(settings, builder.clone(), jsonMapper);
    }
}
