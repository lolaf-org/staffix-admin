package org.lolaf.staffix.admin.logs.clickhouse;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.clickhouse.ClickHouseClient;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public class ClickHouseLogsSourceFactory implements LogsSourceFactory<ClickHouseLogsSettings> {

    public static final String KIND = "clickhouse-logs";

    private final RestClient.Builder builder;
    private final JsonMapper jsonMapper;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public Class<ClickHouseLogsSettings> getSettingsClass() {
        return ClickHouseLogsSettings.class;
    }

    @Override
    public LogsSource create(ClickHouseLogsSettings settings) {
        ClickHouseClient.check(settings);
        return new ClickHouseLogsSource(settings, builder.clone(), jsonMapper);
    }
}
