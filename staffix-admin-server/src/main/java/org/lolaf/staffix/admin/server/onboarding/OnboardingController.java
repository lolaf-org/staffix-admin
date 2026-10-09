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
