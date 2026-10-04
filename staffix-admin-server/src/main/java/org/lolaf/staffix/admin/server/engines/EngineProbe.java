package org.lolaf.staffix.admin.server.engines;

import java.net.URI;

/**
 * Asks an engine's HTTP admin connector who it is, so an announcement cannot point an engine id at another server.
 */
public interface EngineProbe {

    /**
     * @return whether the connector at {@code baseUrl} accepts {@code token} and calls itself {@code engineId}; false
     * when it does not answer
     */
    boolean answersAs(URI baseUrl, String token, String engineId);
}
