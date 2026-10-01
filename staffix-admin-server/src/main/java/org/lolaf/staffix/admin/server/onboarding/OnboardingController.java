package org.lolaf.staffix.admin.server.onboarding;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.engines.EngineRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OnboardingController {

    private final EngineRegistry registry;

    /**
     * Called by an engine's HTTP admin connector, never by the UI. Repeated as a heartbeat; the latest wins.
     */
    @PostMapping("/api/engines/announce")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void announceEngine(@Valid @RequestBody EngineAnnouncement announcement) {
        registry.announce(announcement);
    }
}
