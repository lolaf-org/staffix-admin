package org.lolaf.staffix.admin.server.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final RoleHierarchy roleHierarchy;

    /**
     * Roles include the implied ones, so a writer is also listed as a reader.
     */
    @GetMapping("/api/me")
    public CurrentUser getCurrentUser(Authentication authentication) {
        Set<String> authorities = roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities()).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return CurrentUser.builder()
                .username(authentication.getName())
                .roles(Arrays.stream(Role.values()).filter(r -> authorities.contains("ROLE_" + r.name())).toList())
                .build();
    }
}
