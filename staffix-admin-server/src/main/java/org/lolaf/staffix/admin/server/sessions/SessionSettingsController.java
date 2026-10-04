package org.lolaf.staffix.admin.server.sessions;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.audit.AuditLog;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
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
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * What an engine needs to describe, store and add a session's settings, beyond the sessions it runs.
 */
@RestController
@RequestMapping("/api/engines/{engineId}")
@RequiredArgsConstructor
public class SessionSettingsController {

    /**
     * staffix's own: a session without a group belongs to it.
     */
    private static final String DEFAULT_GROUP = "default";

    private final EngineGateway gateway;
    private final AuditLog auditLog;

    /**
     * The engine's own, as its staffix version defines the settings it accepts.
     */
    @GetMapping("/schemas/session-settings")
    public JsonNode getSessionSettingsSchema(@PathVariable String engineId) {
        return gateway.getSessionSettingsSchema(engineId);
    }

    @GetMapping("/session-settings-stores")
    public List<SessionSettingsStore> listSessionSettingsStores(@PathVariable String engineId) {
        return gateway.listSessionSettingsStores(engineId);
    }

    /**
     * Acceptor sessions only: an initiator's sessions are the targets it was built with.
     */
    @PreAuthorize("hasRole('WRITER')")
    @PostMapping("/session-settings-stores/{sessionSettingsStore}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public void addSession(@PathVariable String engineId, @PathVariable String sessionSettingsStore, @RequestBody JsonNode settings) {
        JsonNode id = settings.path("fixSessionId");
        SessionRef session = SessionRef.of(engineId, id.path("group").asString(DEFAULT_GROUP), id.path("name").asString(""));
        auditLog.record(session, "add session to session settings store " + sessionSettingsStore, () -> {
            if (!SessionType.ACCEPTOR.name().equals(settings.path("fixSessionType").asString(""))) {
                throw GatewayException.invalid("Only an acceptor session can be added: an initiator's sessions are the targets it was built with");
            }
            gateway.addSession(engineId, sessionSettingsStore, settings);
        });
    }
}
