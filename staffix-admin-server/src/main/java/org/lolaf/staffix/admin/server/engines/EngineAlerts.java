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
package org.lolaf.staffix.admin.server.engines;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.alerts.ActiveAlert;
import org.lolaf.staffix.admin.server.alerts.AlertLevel;
import org.lolaf.staffix.admin.server.alerts.SessionProbe;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An engine's alerts and probes, as its admin API answers them, turned into the console's.
 */
@UtilityClass
class EngineAlerts {

    static ActiveAlert activeAlert(String engineId, ConnectorAlerts.Alert alert, boolean sessionListed) {
        return ActiveAlert.builder()
                .engineId(engineId)
                .component(alert.getComponent())
                .group(alert.getGroup())
                .sessionId(alert.getName())
                .configId(alert.getSelectedConfig())
                .sessionListed(sessionListed)
                .probe(alert.getProbe())
                .level(AlertLevel.valueOf(alert.getLevel()))
                .message(alert.getMessage())
                .since(Instant.parse(alert.getSince()))
                .runbookUrl(runbookUrl(alert.getRunbookUrl()))
                .build();
    }

    /**
     * The definitions in their order, each with its state; one the engine reports no state for has none.
     */
    static List<SessionProbe> probes(List<ConnectorProbes.Probe> definitions, ConnectorProbes.State[] states) {
        Map<String, ConnectorProbes.State> byProbe = new HashMap<>();
        for (ConnectorProbes.State state : states) {
            byProbe.put(state.getProbe(), state);
        }
        return definitions.stream().map(definition -> probe(definition, byProbe.get(definition.getName()))).toList();
    }

    /**
     * Only a web link is kept: the console renders it as one, and an engine is no authority on what a browser runs.
     */
    private static @Nullable URI runbookUrl(@Nullable String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = new URI(url);
            return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()) ? uri : null;
        } catch (URISyntaxException notAUri) {
            return null;
        }
    }

    private static SessionProbe probe(ConnectorProbes.Probe definition, ConnectorProbes.@Nullable State state) {
        SessionProbe.SessionProbeBuilder probe = SessionProbe.builder()
                .name(definition.getName())
                .description(definition.getDescription())
                .evaluationInterval(Duration.parse(definition.getEvaluationInterval()))
                .reminderInterval(definition.getReminderInterval() == null ? null : Duration.parse(definition.getReminderInterval()))
                .runbookUrl(runbookUrl(definition.getRunbookUrl()))
                .onlyWhileLoggedIn(Boolean.TRUE.equals(definition.getOnlyWhileActive()));
        if (state != null) {
            probe.level(state.getLevel() == null ? null : AlertLevel.valueOf(state.getLevel()))
                    .message(state.getMessage())
                    .since(state.getSince() == null ? null : Instant.parse(state.getSince()))
                    .lastEvaluation(state.getLastEvaluation() == null ? null : Instant.parse(state.getLastEvaluation()))
                    .suspended(state.isSuspended());
        }
        return probe.build();
    }
}
