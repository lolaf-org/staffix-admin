package org.lolaf.staffix.admin.metrics.elasticsearch;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchMetricsAutoConfigurationTest {

    @Test
    void registersTheFactoryAndNoSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RestClientAutoConfiguration.class,
                        ElasticsearchMetricsAutoConfiguration.class))
                .run(app -> assertThat(app).hasSingleBean(ElasticsearchMetricsSourceFactory.class).doesNotHaveBean(MetricsSource.class));
    }
}
