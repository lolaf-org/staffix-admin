package org.lolaf.staffix.admin.server.connectors;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * The emitters an engine's sessions use, so an admin sees what a connector assignment covers. Sessions are named
 * {@code group.session}.
 */
@Value
@Builder
public class EngineEmitters {
    String engineId;
    /**
     * False for an engine known from the stored connectors only.
     */
    boolean announced;
    /**
     * Whether its sessions could be read; its emitters are empty otherwise.
     */
    boolean reachable;
    @Singular
    List<Emitter> loggers;
    @Singular
    List<Emitter> monitors;
    /**
     * Sessions without a monitoring plugin, whose metrics are not collected.
     */
    @Singular
    List<String> unmonitoredSessions;

    @Value
    @Builder
    public static class Emitter {
        String instanceId;
        @Singular
        List<String> sessions;
    }
}
