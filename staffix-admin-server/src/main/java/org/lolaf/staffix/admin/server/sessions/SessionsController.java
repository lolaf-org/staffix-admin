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
package org.lolaf.staffix.admin.server.sessions;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.alerts.SessionProbe;
import org.lolaf.staffix.admin.server.audit.AuditLog;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.List;

@RestController
@RequestMapping("/api/engines/{engineId}/groups")
@RequiredArgsConstructor
public class SessionsController {

    private static final String SESSION = "/{group}/sessions/{sessionId}";
    private static final String WRITER = "hasRole('WRITER')";

    private final EngineGateway gateway;
    private final AuditLog auditLog;

    @GetMapping
    public List<SessionGroup> listGroups(@PathVariable String engineId) {
        return gateway.listGroups(engineId);
    }

    @GetMapping(SESSION)
    public SessionDetail getSession(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId) {
        return gateway.getSession(SessionRef.of(engineId, group, sessionId));
    }

    /**
     * The session's probes with their live state; the console fetches it only while the session's alerts are shown.
     */
    @GetMapping(SESSION + "/probes")
    public List<SessionProbe> getProbes(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId) {
        return gateway.getProbes(SessionRef.of(engineId, group, sessionId));
    }

    /**
     * Audited whether the engine accepts it or not: a refusal is answered 409, as a probe whose condition is polled
     * cannot be cleared by hand.
     */
    @PreAuthorize(WRITER)
    @PostMapping(SESSION + "/probes/{probe}/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAlert(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId,
                           @PathVariable String probe) {
        SessionRef session = SessionRef.of(engineId, group, sessionId);
        auditLog.record(session, "clear alert " + probe, () -> gateway.clearAlert(session, probe));
    }

    /**
     * A document of the engine's session settings schema; secret values masked.
     */
    @GetMapping(SESSION + "/settings")
    public JsonNode getSessionSettings(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId) {
        return gateway.getSessionSettings(SessionRef.of(engineId, group, sessionId));
    }

    /**
     * A value sent back masked keeps the session's. Audited by the names of the fields that changed, never values.
     */
    @PreAuthorize(WRITER)
    @PutMapping(SESSION + "/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSessionSettings(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId,
                                      @RequestBody JsonNode settings) {
        SessionRef session = SessionRef.of(engineId, group, sessionId);
        String changes = SettingsChanges.describe(gateway.getSessionSettings(session), settings);
        auditLog.record(session, "update settings: " + changes, () -> gateway.updateSessionSettings(session, settings));
    }

    /**
     * Acceptor sessions only: an initiator's sessions are the targets it was built with.
     */
    @PreAuthorize(WRITER)
    @DeleteMapping(SESSION + "/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSession(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId) {
        SessionRef session = SessionRef.of(engineId, group, sessionId);
        auditLog.record(session, "remove session", () -> {
            if (gateway.getSession(session).getSummary().getType() != SessionType.ACCEPTOR) {
                throw GatewayException.rejected("Only an acceptor session can be removed: an initiator's sessions are the targets it was built with");
            }
            gateway.removeSession(session);
        });
    }

    @GetMapping(SESSION + "/application-settings")
    public List<ApplicationSetting> getApplicationSettings(@PathVariable String engineId, @PathVariable String group,
                                                           @PathVariable String sessionId) {
        return gateway.getApplicationSettings(SessionRef.of(engineId, group, sessionId));
    }

    @PreAuthorize(WRITER)
    @PostMapping(SESSION + "/logon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logonSession(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId) {
        SessionRef session = SessionRef.of(engineId, group, sessionId);
        auditLog.record(session, "logon", () -> gateway.logon(session));
    }

    @PreAuthorize(WRITER)
    @PostMapping(SESSION + "/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutSession(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId) {
        SessionRef session = SessionRef.of(engineId, group, sessionId);
        auditLog.record(session, "logout", () -> gateway.logout(session));
    }

    @PreAuthorize(WRITER)
    @PostMapping(SESSION + "/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetSession(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId,
                             @Valid @RequestBody ResetRequest request) {
        SessionRef session = SessionRef.of(engineId, group, sessionId);
        auditLog.record(session, "reset " + request.getMode(), () -> gateway.reset(session, request.getMode()));
    }

    /**
     * Makes an initiator run another of its configs.
     */
    @PreAuthorize(WRITER)
    @PostMapping(SESSION + "/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activateConfig(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId,
                               @Valid @RequestBody ActivateRequest request) {
        SessionRef session = SessionRef.of(engineId, group, sessionId);
        auditLog.record(session, "activate config " + request.getConfigId(), () -> gateway.activate(session, request.getConfigId()));
    }

    @PreAuthorize(WRITER)
    @PutMapping(SESSION + "/seqnums")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setSeqNums(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId,
                           @Valid @RequestBody SeqNumsRequest request) {
        SessionRef session = SessionRef.of(engineId, group, sessionId);
        auditLog.record(session, "set seqnums incoming=" + request.getIncoming() + " outgoing=" + request.getOutgoing(),
                () -> gateway.setSeqNums(session, request.getIncoming(), request.getOutgoing()));
    }

    @PreAuthorize(WRITER)
    @PostMapping(SESSION + "/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendMessages(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId,
                             @Valid @RequestBody SendMessageRequest request) {
        SessionRef session = SessionRef.of(engineId, group, sessionId);
        auditLog.record(session, "send possDup=" + request.isPossDup() + " messages=" + request.getMessages(),
                () -> gateway.sendMessages(session, request.getMessages(), request.getSeparator(), request.isPossDup()));
    }
}
