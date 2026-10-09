/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lolaf.staffix.admin.server.connectors;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.secrets.SecretCipher;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorsConfigurationTest {

    @Test
    void refusesToStartWithoutAConsoleConfigStore() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConnectorsConfiguration.class)
                .withBean(SecretCipher.class, () -> new SecretCipher(null, List.of()))
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("No console configuration store"));
    }
}
