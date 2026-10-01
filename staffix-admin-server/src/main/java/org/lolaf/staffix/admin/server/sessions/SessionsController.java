package org.lolaf.staffix.admin.server.sessions;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.audit.AuditLog;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
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
@RequestMapping("/api/engines/{instanceId}/sessions")
@RequiredArgsConstructor
public class SessionsController {

    private static final String WRITER = "hasRole('WRITER')";

    private final EngineGateway gateway;
    private final AuditLog auditLog;

    @GetMapping
    public List<SessionGroup> listSessions(@PathVariable String instanceId) {
        return gateway.listSessions(instanceId);
    }

    @GetMapping("/{sessionId}")
    public SessionDetail getSession(@PathVariable String instanceId, @PathVariable String sessionId) {
        return gateway.getSession(instanceId, sessionId);
    }

    /**
     * The session's FixSessionSettings as JSON, free form until its format is settled.
     */
    @GetMapping("/{sessionId}/settings")
    public JsonNode getSessionSettings(@PathVariable String instanceId, @PathVariable String sessionId) {
        return gateway.getSessionSettings(instanceId, sessionId);
    }

    @PreAuthorize(WRITER)
    @PostMapping("/{sessionId}/logon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logonSession(@PathVariable String instanceId, @PathVariable String sessionId) {
        auditLog.record(instanceId, sessionId, "logon", () -> gateway.logon(instanceId, sessionId));
    }

    @PreAuthorize(WRITER)
    @PostMapping("/{sessionId}/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutSession(@PathVariable String instanceId, @PathVariable String sessionId) {
        auditLog.record(instanceId, sessionId, "logout", () -> gateway.logout(instanceId, sessionId));
    }

    @PreAuthorize(WRITER)
    @PostMapping("/{sessionId}/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetSession(@PathVariable String instanceId, @PathVariable String sessionId,
                             @Valid @RequestBody ResetRequest request) {
        auditLog.record(instanceId, sessionId, "reset " + request.getMode(),
                () -> gateway.reset(instanceId, sessionId, request.getMode()));
    }

    /**
     * Makes this session the active target of every initiator listing it.
     */
    @PreAuthorize(WRITER)
    @PostMapping("/{sessionId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activateInitiatorTarget(@PathVariable String instanceId, @PathVariable String sessionId) {
        auditLog.record(instanceId, sessionId, "activate initiator target",
                () -> gateway.activateInitiatorTarget(instanceId, sessionId));
    }

    @PreAuthorize(WRITER)
    @PutMapping("/{sessionId}/seqnums")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setSeqNums(@PathVariable String instanceId, @PathVariable String sessionId,
                           @Valid @RequestBody SeqNumsRequest request) {
        auditLog.record(instanceId, sessionId, "set seqnums incoming=" + request.getIncoming() + " outgoing=" + request.getOutgoing(),
                () -> gateway.setSeqNums(instanceId, sessionId, request.getIncoming(), request.getOutgoing()));
    }

    @PreAuthorize(WRITER)
    @PostMapping("/{sessionId}/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendMessage(@PathVariable String instanceId, @PathVariable String sessionId,
                            @Valid @RequestBody SendMessageRequest request) {
        auditLog.record(instanceId, sessionId, "send possDup=" + request.isPossDup() + " message=" + request.getMessage(),
                () -> gateway.sendMessage(instanceId, sessionId, request.getMessage(), request.getSeparator(), request.isPossDup()));
    }
}
