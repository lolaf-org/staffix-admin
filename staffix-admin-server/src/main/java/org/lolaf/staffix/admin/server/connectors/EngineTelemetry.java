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
