package org.lolaf.staffix.admin.server.user;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class CurrentUser {
    String username;
    @Singular
    List<Role> roles;
}
