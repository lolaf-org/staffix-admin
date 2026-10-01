package org.lolaf.staffix.admin.server.security;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.security.jaas.RolePrincipal;
import org.springframework.security.authentication.jaas.AuthorityGranter;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
class RoleAuthorityGranter implements AuthorityGranter {

    private final Map<String, List<AdminRole>> roleMappings;

    @Override
    public Set<String> grant(Principal principal) {
        if (principal instanceof RolePrincipal role) {
            return Set.of(AdminRole.valueOf(role.getName()).authority());
        }
        return roleMappings.getOrDefault(principal.getName(), List.of()).stream()
                .map(AdminRole::authority)
                .collect(Collectors.toSet());
    }
}
