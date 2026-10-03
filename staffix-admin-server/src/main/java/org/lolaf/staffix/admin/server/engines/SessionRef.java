package org.lolaf.staffix.admin.server.engines;

import lombok.Value;

/**
 * A connection of an engine. Its id is unique only within its group: {@code beta.trading} and {@code alpha.trading}
 * are two sessions.
 */
@Value(staticConstructor = "of")
public class SessionRef {
    String engineId;
    String group;
    String sessionId;

    @Override
    public String toString() {
        return engineId + "/" + group + "." + sessionId;
    }
}
