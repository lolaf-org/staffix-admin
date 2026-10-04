package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * One of an engine's session settings stores.
 */
@Value
@Builder
@Jacksonized
public class SessionSettingsStore {
    String instanceId;
    /**
     * Whether a change made through the console survives the engine's restart.
     */
    boolean persistent;
}
