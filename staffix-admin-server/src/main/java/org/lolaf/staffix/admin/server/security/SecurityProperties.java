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
