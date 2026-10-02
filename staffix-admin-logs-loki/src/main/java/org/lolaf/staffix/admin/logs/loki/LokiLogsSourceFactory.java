package org.lolaf.staffix.admin.logs.loki;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class LokiLogsSourceFactory implements LogsSourceFactory<LokiProperties> {

    public static final String KIND = "loki";

    private final RestClient.Builder builder;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public Class<LokiProperties> getSettingsClass() {
        return LokiProperties.class;
    }

    @Override
    public LogsSource create(LokiProperties settings) {
        if (settings.getUrl() == null) {
            throw new IllegalArgumentException("url is required");
        }
        return new LokiLogsSource(settings, builder.clone());
    }
}
