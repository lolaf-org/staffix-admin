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
import org.lolaf.staffix.admin.server.engines.Engine;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.EngineRegistry;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AlertsController {

    private final EngineRegistry registry;
    private final EngineGateway gateway;

    /**
     * An engine that does not answer is listed as unavailable rather than failing the whole overview.
     */
    @GetMapping("/api/alerts")
    public AlertsOverview listAlerts() {
        AlertsOverview.AlertsOverviewBuilder overview = AlertsOverview.builder();
        List<ActiveAlert> alerts = new ArrayList<>();
        for (Engine engine : registry.list()) {
            try {
                alerts.addAll(gateway.listAlerts(engine.getEngineId()));
            } catch (GatewayException e) {
                overview.unavailableEngineId(engine.getEngineId());
            }
        }
        alerts.sort(ActiveAlert.WORST_FIRST);
        return overview.alerts(alerts).build();
    }

    @GetMapping("/api/engines/{engineId}/alerts")
    public List<ActiveAlert> listEngineAlerts(@PathVariable String engineId) {
        return gateway.listAlerts(engineId);
    }
}
