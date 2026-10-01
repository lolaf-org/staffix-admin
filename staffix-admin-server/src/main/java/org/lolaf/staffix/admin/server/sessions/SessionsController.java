package org.lolaf.staffix.admin.server.sessions;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import static org.lolaf.staffix.admin.server.web.NotImplemented.notImplemented;

@RestController
@RequestMapping("/api/engines/{instanceId}/sessions")
public class SessionsController {

    @GetMapping
    public List<SessionGroup> listSessions(@PathVariable String instanceId) {
        throw notImplemented();
    }

    @GetMapping("/{sessionId}")
    public SessionDetail getSession(@PathVariable String instanceId, @PathVariable String sessionId) {
        throw notImplemented();
    }

    /**
     * The session's FixSessionSettings as JSON, free form until its format is settled.
     */
    @GetMapping("/{sessionId}/settings")
    public JsonNode getSessionSettings(@PathVariable String instanceId, @PathVariable String sessionId) {
        throw notImplemented();
    }

    @PostMapping("/{sessionId}/logon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logonSession(@PathVariable String instanceId, @PathVariable String sessionId) {
        throw notImplemented();
    }

    @PostMapping("/{sessionId}/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutSession(@PathVariable String instanceId, @PathVariable String sessionId) {
        throw notImplemented();
    }

    @PostMapping("/{sessionId}/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetSession(@PathVariable String instanceId, @PathVariable String sessionId,
                             @Valid @RequestBody ResetRequest request) {
        throw notImplemented();
    }

    /**
     * Makes this session the active target of every initiator listing it.
     */
    @PostMapping("/{sessionId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activateInitiatorTarget(@PathVariable String instanceId, @PathVariable String sessionId) {
        throw notImplemented();
    }

    @PutMapping("/{sessionId}/seqnums")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setSeqNums(@PathVariable String instanceId, @PathVariable String sessionId,
                           @Valid @RequestBody SeqNumsRequest request) {
        throw notImplemented();
    }

    @PostMapping("/{sessionId}/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendMessage(@PathVariable String instanceId, @PathVariable String sessionId,
                            @Valid @RequestBody SendMessageRequest request) {
        throw notImplemented();
    }
}
