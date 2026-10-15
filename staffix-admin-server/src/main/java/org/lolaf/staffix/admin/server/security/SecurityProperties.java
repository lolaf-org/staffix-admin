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
package org.lolaf.staffix.admin.server.security;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

@Value
@ConfigurationProperties("staffix.admin.security")
public class SecurityProperties {
    /**
     * JAAS login configuration file; when unset, the JVM's own ({@code -Djava.security.auth.login.config}).
     */
    @Nullable
    Resource jaasConfig;
    /**
     * The login context of the JAAS configuration.
     */
    String loginContextName;
    /**
     * Roles granted to JAAS principals by name, e.g. an LDAP group: {@code cn=fix-ops: [WRITER]}. Principals of
     * {@link org.lolaf.staffix.admin.server.security.jaas.RolePrincipal} need no mapping.
     */
    Map<String, List<AdminRole>> roleMappings;

    public SecurityProperties(@Nullable Resource jaasConfig, @DefaultValue("staffix-admin") String loginContextName,
                              @DefaultValue Map<String, List<AdminRole>> roleMappings) {
        this.jaasConfig = jaasConfig;
        this.loginContextName = loginContextName;
        this.roleMappings = roleMappings;
    }
}
