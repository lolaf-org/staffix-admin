package org.lolaf.staffix.admin.server.connectors;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.secrets.SecretCipher;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorsConfigurationTest {

    @Test
    void refusesToStartWithoutAConnectorStore() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConnectorsConfiguration.class)
                .withBean(SecretCipher.class, () -> new SecretCipher(null, List.of()))
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("No connector store"));
    }
}
