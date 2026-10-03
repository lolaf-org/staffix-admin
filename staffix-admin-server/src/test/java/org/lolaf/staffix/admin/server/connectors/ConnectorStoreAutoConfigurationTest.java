package org.lolaf.staffix.admin.server.connectors;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.lolaf.staffix.admin.store.ConnectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorStoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConnectorStoreAutoConfiguration.class))
            .withBean(AdminProperties.class, () -> new AdminProperties(Duration.ofSeconds(90), Duration.ofSeconds(5), Path.of("data")));

    @Test
    void storesTheConnectorsInAYamlFileByDefault() {
        runner.run(context -> assertThat(context).getBean(ConnectorStore.class).isInstanceOf(YamlConnectorStore.class));
    }

    @Test
    void stepsAsideForAnotherStore() {
        ConnectorStore teamStore = new ConnectorsTest.MemoryStore();

        runner.withBean(ConnectorStore.class, () -> teamStore)
                .run(context -> assertThat(context).getBean(ConnectorStore.class).isSameAs(teamStore));
    }
}
