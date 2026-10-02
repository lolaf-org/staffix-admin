package org.lolaf.staffix.admin.logs.loki;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LokiLogsAutoConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RestClientAutoConfiguration.class,
                    LokiLogsAutoConfiguration.class));

    @Test
    void createsTheSourceOnlyWhenLokiIsConfigured() {
        context.run(app -> assertThat(app).doesNotHaveBean(LogsSource.class));
        context.withPropertyValues("staffix.admin.logs.loki.url=http://localhost:3100")
                .run(app -> {
                    assertThat(app).hasSingleBean(LokiLogsSource.class);
                    assertThat(app.getBean(LokiProperties.class).getStreamSelector()).isEqualTo("{service_name=~\".+\"}");
                });
    }

    @Test
    void backsOffBeforeTheApplicationsOwnSource() {
        context.withPropertyValues("staffix.admin.logs.loki.url=http://localhost:3100")
                .withBean(LogsSource.class, () -> query -> List.of())
                .run(app -> assertThat(app).hasSingleBean(LogsSource.class).doesNotHaveBean(LokiLogsSource.class));
    }
}
