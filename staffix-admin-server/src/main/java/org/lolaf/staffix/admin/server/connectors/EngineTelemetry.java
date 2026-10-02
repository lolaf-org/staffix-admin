package org.lolaf.staffix.admin.server.connectors;

import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.metrics.MetricsSource;

import java.util.Optional;

/**
 * Where each engine's telemetry is read from.
 */
public interface EngineTelemetry {

    /**
     * Empty when the engine's metrics are not collected: no connector is assigned to it, or {@code none} is.
     *
     * @throws IllegalArgumentException if the assigned connector's stored settings cannot make a source
     */
    Optional<MetricsSource> metrics(String engineId);

    /**
     * Empty when the engine's logs are not collected: no connector is assigned to it, or {@code none} is.
     *
     * @throws IllegalArgumentException if the assigned connector's stored settings cannot make a source
     */
    Optional<LogsSource> logs(String engineId);
}
