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
package org.lolaf.staffix.admin.server.alerts;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.audit.AuditLog;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The probes of an engine component, such as {@code ha}, which belong to no session.
 */
@RestController
@RequestMapping("/api/engines/{engineId}/components/{component}/probes")
@RequiredArgsConstructor
public class ComponentAlertsController {

    private final EngineGateway gateway;
    private final AuditLog auditLog;

    @GetMapping
    public List<SessionProbe> getProbes(@PathVariable String engineId, @PathVariable String component) {
        return gateway.getComponentProbes(engineId, component);
    }

    /**
     * Audited whether the engine accepts it or not: a polled condition is refused, answered 409.
     */
    @PreAuthorize("hasRole('WRITER')")
    @PostMapping("/{probe}/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAlert(@PathVariable String engineId, @PathVariable String component, @PathVariable String probe) {
        String operation = "clear alert " + probe + " of " + component;
        auditLog.record(engineId, operation, () -> {
            gateway.clearComponentAlert(engineId, component, probe);
            return operation;
        }, done -> done);
    }
}
