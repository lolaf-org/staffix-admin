package org.lolaf.staffix.admin.logs.loki;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LokiLogsAutoConfigurationTest {

    @Test
    void registersTheFactoryAndNoSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RestClientAutoConfiguration.class,
                        LokiLogsAutoConfiguration.class))
                .run(app -> assertThat(app).hasSingleBean(LokiLogsSourceFactory.class).doesNotHaveBean(LogsSource.class));
    }
}
