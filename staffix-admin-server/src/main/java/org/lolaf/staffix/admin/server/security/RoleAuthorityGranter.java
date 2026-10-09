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
