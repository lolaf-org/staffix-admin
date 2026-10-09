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
package org.lolaf.staffix.admin.server.connectors;

import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.metrics.MetricsSource;

import java.util.Collection;
import java.util.List;

/**
 * Where an engine's telemetry is read from. A backend only holds what its emitters send it, so the same query sent
 * to every source returned covers the sessions in scope; each source is returned once.
 * <p>
 * Every method throws {@link IllegalArgumentException} if a connector's stored settings cannot make a source.
 */
public interface EngineTelemetry {

    /**
     * Empty when none of these emitters' metrics is collected.
     */
    List<MetricsSource> metrics(String engineId, Collection<Emitters> emitters);

    /**
     * Empty when none of these emitters' logs is collected.
     */
    List<LogsSource> logs(String engineId, Collection<Emitters> emitters);

    /**
     * Every metrics source the engine may report to, for when its sessions' emitters are unknown.
     */
    List<MetricsSource> allMetrics(String engineId);

    /**
     * Every logs source the engine may report to, for when its sessions' emitters are unknown.
     */
    List<LogsSource> allLogs(String engineId);
}
