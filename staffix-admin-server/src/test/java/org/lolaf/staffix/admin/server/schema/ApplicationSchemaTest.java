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
package org.lolaf.staffix.admin.server.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSchemaTest {

    private static final SchemaValidator VALIDATOR = validator();

    @Test
    void acceptsTheDistributionsApplicationYml() {
        assertThat(VALIDATOR.errors(Path.of("../staffix-admin-dist/src/config/application.yml"))).isEmpty();
    }

    @Test
    void acceptsWhatSpringBinds() {
        assertThat(VALIDATOR.errors("""
                server:
                  port: 8443
                staffix:
                  admin:
                    dataDir: /data
                    engine-heartbeat-timeout: PT2M
                    engine-request-timeout: 5000
                    require-engine-tls: ${REQUIRE_TLS:true}
                    security:
                      role-mappings:
                        cn=fix-ops: [ WRITER ]
                        cn=fix-desk: reader
                    secrets:
                      previous-master-keys: alpha, beta
                """)).isEmpty();
    }

    @Test
    void refusesAMisspelledSetting() {
        assertThat(VALIDATOR.errors("staffix:\n  admin:\n    data-dirr: data")).first().asString().contains("'data-dirr' is not defined");
    }

    @Test
    void refusesAMalformedValue() {
        assertThat(VALIDATOR.errors("staffix:\n  admin:\n    engine-request-timeout: soon")).first().asString().startsWith("/staffix/admin/engine-request-timeout:");
        assertThat(VALIDATOR.errors("staffix:\n  admin:\n    security:\n      role-mappings:\n        cn=fix-ops: [ ADMIN ]")).first().asString().startsWith("/staffix/admin/security/role-mappings/cn=fix-ops/0:");
    }

    @Test
    void showsJavadocTagsAsText() {
        assertThat(ApplicationSchema.plain("Instead of {@link #getMasterKey()}, see {@code -Dkey} and {@link a.b.RolePrincipal}."))
                .isEqualTo("Instead of master-key, see -Dkey and RolePrincipal.");
    }

    private static SchemaValidator validator() {
        try {
            ClassLoader classLoader = ApplicationSchemaTest.class.getClassLoader();
            return new SchemaValidator(ApplicationSchema.of(ConfigSchemas.metadataProperties(classLoader), classLoader));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
