package org.lolaf.staffix.admin.logs.elasticsearch;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchLogsAutoConfigurationTest {

    @Test
    void registersTheFactoryAndNoSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RestClientAutoConfiguration.class,
                        ElasticsearchLogsAutoConfiguration.class))
                .run(app -> assertThat(app).hasSingleBean(ElasticsearchLogsSourceFactory.class).doesNotHaveBean(LogsSource.class));
    }
}
