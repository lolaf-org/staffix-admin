package org.lolaf.staffix.admin.server.onboarding;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.lolaf.staffix.admin.server.web.NotImplemented.notImplemented;

@RestController
public class OnboardingController {

    /**
     * Called by an engine's HTTP admin connector, never by the UI. Repeated as a heartbeat; the latest wins.
     */
    @PostMapping("/api/engines/announce")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void announceEngine(@Valid @RequestBody EngineAnnouncement announcement) {
        throw notImplemented();
    }
}
