package org.lolaf.staffix.admin.server.sessions;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * What a session's settings can name in its engine, so the console offers choices the engine resolves.
 */
@Value
@Builder
@Jacksonized
public class SessionComponents {
    @Singular
    List<ApplicationFactory> applicationFactories;
    @Singular
    List<String> messagesStores;
    @Singular
    List<String> messagesLoggers;
    @Singular
    List<SessionsPlugin> sessionsPlugins;

    @Value
    @Builder
    @Jacksonized
    public static class ApplicationFactory {
        String instanceId;
        @Singular
        List<String> applicationIds;
    }

    @Value
    @Builder
    @Jacksonized
    public static class SessionsPlugin {
        String instanceId;
        /**
         * Full class names, the keys of a session's {@code fixSessionPluginsInstanceIds}.
         */
        @Singular
        List<String> pluginTypes;
    }
}
