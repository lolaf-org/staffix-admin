package org.lolaf.staffix.admin.elasticsearch;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public class ElasticsearchLogsSourceFactory implements LogsSourceFactory<ElasticsearchLogsSettings> {

    public static final String KIND = "elasticsearch-logs";

    private final RestClient.Builder builder;
    private final JsonMapper jsonMapper;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public Class<ElasticsearchLogsSettings> getSettingsClass() {
        return ElasticsearchLogsSettings.class;
    }

    @Override
    public LogsSource create(ElasticsearchLogsSettings settings) {
        ElasticsearchClient.check(settings);
        return new ElasticsearchLogsSource(settings, builder.clone(), jsonMapper);
    }
}
