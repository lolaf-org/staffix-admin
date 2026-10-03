package org.lolaf.staffix.admin.store.yaml;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.store.ConnectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class YamlConnectorStoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(YamlConnectorStoreAutoConfiguration.class));

    @Test
    void storesTheConnectorsInTheDataDirectory() {
        runner.withPropertyValues("staffix.admin.data-dir=/var/lib/staffix-admin")
                .run(context -> assertThat(context).getBean(ConnectorStore.class).isInstanceOf(YamlConnectorStore.class));
    }

    @Test
    void stepsAsideForAnotherStore() {
        ConnectorStore teamStore = mock(ConnectorStore.class);

        runner.withBean(ConnectorStore.class, () -> teamStore)
                .run(context -> assertThat(context).getBean(ConnectorStore.class).isSameAs(teamStore));
    }
}
