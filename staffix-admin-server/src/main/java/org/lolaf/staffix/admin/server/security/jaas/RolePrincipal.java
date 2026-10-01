package org.lolaf.staffix.admin.server.security.jaas;

import lombok.Value;

import java.security.Principal;

/**
 * Its name is an {@link org.lolaf.staffix.admin.server.security.AdminRole} granted as is.
 */
@Value
public class RolePrincipal implements Principal {
    String name;
}
