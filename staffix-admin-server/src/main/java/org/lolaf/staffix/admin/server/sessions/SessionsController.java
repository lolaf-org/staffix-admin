package org.lolaf.staffix.admin.server.sessions;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.audit.AuditLog;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
     * The running config's FixSessionSettings as JSON, free form until its format is settled.
     */
    @GetMapping(SESSION + "/settings")
    public JsonNode getSessionSettings(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId) {
        return gateway.getSessionSettings(SessionRef.of(engineId, group, sessionId));
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
    public void sendMessage(@PathVariable String engineId, @PathVariable String group, @PathVariable String sessionId,
                            @Valid @RequestBody SendMessageRequest request) {
        SessionRef session = SessionRef.of(engineId, group, sessionId);
        auditLog.record(session, "send possDup=" + request.isPossDup() + " message=" + request.getMessage(),
                () -> gateway.sendMessage(session, request.getMessage(), request.getSeparator(), request.isPossDup()));
    }
}
