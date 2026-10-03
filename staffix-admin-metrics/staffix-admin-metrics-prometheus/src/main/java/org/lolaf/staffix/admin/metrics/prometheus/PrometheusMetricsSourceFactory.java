package org.lolaf.staffix.admin.metrics.prometheus;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public class PrometheusMetricsSourceFactory implements MetricsSourceFactory<PrometheusSettings> {

    public static final String KIND = "prometheus";

    private final RestClient.Builder builder;
    private final JsonMapper jsonMapper;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public Class<PrometheusSettings> getSettingsClass() {
        return PrometheusSettings.class;
    }

    @Override
    public MetricsSource create(PrometheusSettings settings) {
        if (settings.getUrl() == null) {
            throw new IllegalArgumentException("url is required");
        }
        return new PrometheusMetricsSource(settings, builder.clone(), jsonMapper);
    }
}
