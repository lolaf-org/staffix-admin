package org.lolaf.staffix.admin.store.yaml;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.store.ConsoleConfigStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class YamlConsoleConfigStoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(YamlConsoleConfigStoreAutoConfiguration.class));

    @Test
    void storesTheConnectorsInTheDataDirectory() {
        runner.withPropertyValues("staffix.admin.data-dir=/var/lib/staffix-admin")
                .run(context -> assertThat(context).getBean(ConsoleConfigStore.class).isInstanceOf(YamlConsoleConfigStore.class));
    }

    @Test
    void stepsAsideForAnotherStore() {
        ConsoleConfigStore teamStore = mock(ConsoleConfigStore.class);

        runner.withBean(ConsoleConfigStore.class, () -> teamStore)
                .run(context -> assertThat(context).getBean(ConsoleConfigStore.class).isSameAs(teamStore));
    }
}
