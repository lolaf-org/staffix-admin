package org.lolaf.staffix.admin.metrics.clickhouse;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.clickhouse.ClickHouseClient;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public class ClickHouseMetricsSourceFactory implements MetricsSourceFactory<ClickHouseMetricsSettings> {

    public static final String KIND = "clickhouse-metrics";

    private final RestClient.Builder builder;
    private final JsonMapper jsonMapper;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public Class<ClickHouseMetricsSettings> getSettingsClass() {
        return ClickHouseMetricsSettings.class;
    }

    @Override
    public MetricsSource create(ClickHouseMetricsSettings settings) {
        ClickHouseClient.check(settings);
        return new ClickHouseMetricsSource(settings, builder.clone(), jsonMapper);
    }
}
