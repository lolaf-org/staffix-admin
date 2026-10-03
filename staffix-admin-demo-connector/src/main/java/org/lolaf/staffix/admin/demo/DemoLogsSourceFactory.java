package org.lolaf.staffix.admin.demo;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class DemoLogsSourceFactory implements LogsSourceFactory<DemoSettings> {

    public static final String KIND = "demo-logs";

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
    public LogsSource create(DemoSettings settings) {
        if (settings.getUrl() == null) {
            throw new IllegalArgumentException("url is required");
        }
        return new DemoLogsSource(new DemoClient(settings, builder.clone()));
    }
}
