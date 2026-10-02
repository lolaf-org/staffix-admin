package org.lolaf.staffix.admin.metrics.prometheus;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusMetricsAutoConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RestClientAutoConfiguration.class,
                    PrometheusMetricsAutoConfiguration.class));

    @Test
    void createsTheSourceOnlyWhenPrometheusIsConfigured() {
        context.run(app -> assertThat(app).doesNotHaveBean(MetricsSource.class));
        context.withPropertyValues("staffix.admin.metrics.prometheus.url=http://localhost:9090",
                        "staffix.admin.metrics.prometheus.timer-base-unit=microseconds")
                .run(app -> {
                    assertThat(app).hasSingleBean(PrometheusMetricsSource.class);
                    assertThat(app.getBean(PrometheusProperties.class).getTimerBaseUnit()).hasToString("MICROSECONDS");
                });
    }

    @Test
    void backsOffBeforeTheApplicationsOwnSource() {
        context.withPropertyValues("staffix.admin.metrics.prometheus.url=http://localhost:9090")
                .withBean(MetricsSource.class, () -> query -> List.of())
                .run(app -> assertThat(app).hasSingleBean(MetricsSource.class).doesNotHaveBean(PrometheusMetricsSource.class));
    }
}
