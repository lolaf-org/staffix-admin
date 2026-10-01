package org.lolaf.staffix.admin.server.security.jaas;

import lombok.Value;

import java.security.Principal;

@Value
public class UserPrincipal implements Principal {
    String name;
}
