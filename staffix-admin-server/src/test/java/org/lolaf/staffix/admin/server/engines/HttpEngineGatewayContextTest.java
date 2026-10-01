package org.lolaf.staffix.admin.server.engines;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "staffix.admin.security.jaas-config=classpath:jaas/dev.conf")
class HttpEngineGatewayContextTest {

    @Autowired
    EngineGateway gateway;

    @Test
    void withoutTheMockProfileTheEnginesAreCalledOverHttp() {
        assertThat(gateway).isInstanceOf(HttpEngineGateway.class);
    }
}
