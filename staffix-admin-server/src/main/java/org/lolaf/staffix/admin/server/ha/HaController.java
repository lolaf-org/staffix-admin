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
package org.lolaf.staffix.admin.server.ha;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.audit.AuditLog;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ha")
@RequiredArgsConstructor
public class HaController {

    private static final String ENGINE = "/engines/{engineId}";
    private static final String WRITER = "hasRole('WRITER')";

    private final HaPairs pairs;
    private final EngineGateway gateway;
    private final AuditLog auditLog;

    public record SwitchoverResult(SwitchoverOutcome outcome) {
    }

    public record CandidacyRequest(boolean candidate) {
    }

    @GetMapping("/pairs")
    public List<HaPair> listPairs() {
        return pairs.list();
    }

    /**
     * Audited with its outcome: a switchover the engine could not carry out still answers 200.
     */
    @PreAuthorize(WRITER)
    @PostMapping(ENGINE + "/switchover")
    public SwitchoverResult switchover(@PathVariable String engineId) {
        return new SwitchoverResult(auditLog.record(engineId, "switchover", () -> gateway.switchover(engineId),
                outcome -> "switchover: " + outcome));
    }

    @PreAuthorize(WRITER)
    @PostMapping(ENGINE + "/candidacy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setCandidate(@PathVariable String engineId, @RequestBody CandidacyRequest request) {
        String operation = request.candidate() ? "end maintenance" : "start maintenance";
        auditLog.record(engineId, operation, () -> {
            gateway.setCandidate(engineId, request.candidate());
            return operation;
        }, done -> done);
    }

    @PreAuthorize(WRITER)
    @PostMapping(ENGINE + "/resync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resync(@PathVariable String engineId) {
        auditLog.record(engineId, "resync all sessions", () -> {
            gateway.resync(engineId);
            return "resync all sessions";
        }, done -> done);
    }

    @PreAuthorize(WRITER)
    @PostMapping(ENGINE + "/groups/{group}/sessions/{sessionId}/resync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resyncSession(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId) {
        SessionRef session = SessionRef.of(engineId, group, sessionId);
        auditLog.record(session, "resync", () -> gateway.resyncSession(session));
    }
}
