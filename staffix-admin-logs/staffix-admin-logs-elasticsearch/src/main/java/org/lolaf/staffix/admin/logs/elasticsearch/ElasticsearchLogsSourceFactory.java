package org.lolaf.staffix.admin.logs.elasticsearch;

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
        if (settings.getUrl() == null) {
            throw new IllegalArgumentException("url is required");
        }
        if (settings.getApiKey() != null && settings.getUsername() != null) {
            throw new IllegalArgumentException("apiKey and username are exclusive");
        }
        if (settings.getUsername() != null && settings.getPassword() == null) {
            throw new IllegalArgumentException("password is required with username");
        }
        return new ElasticsearchLogsSource(settings, builder.clone(), jsonMapper);
    }
}
