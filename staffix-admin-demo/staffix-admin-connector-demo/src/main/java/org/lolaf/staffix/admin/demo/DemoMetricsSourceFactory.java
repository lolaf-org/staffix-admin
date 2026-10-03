package org.lolaf.staffix.admin.demo;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class DemoMetricsSourceFactory implements MetricsSourceFactory<DemoSettings> {

    public static final String KIND = "demo-metrics";

    private final RestClient.Builder builder;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public Class<DemoSettings> getSettingsClass() {
        return DemoSettings.class;
    }

    @Override
    public MetricsSource create(DemoSettings settings) {
        if (settings.getUrl() == null) {
            throw new IllegalArgumentException("url is required");
        }
        return new DemoMetricsSource(new DemoClient(settings, builder.clone()));
    }
}
