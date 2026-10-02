package org.lolaf.staffix.admin.store;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class ConnectorsConfig {
    public static final ConnectorsConfig EMPTY = ConnectorsConfig.builder().build();

    /**
     * Raised by one on every save; a save based on another version than the stored one is refused, so two admins
     * editing at once cannot silently overwrite each other.
     */
    long version;
    @Singular
    List<Connector> connectors;
    /**
     * For every engine without its own entry in {@link #getEngines()}, and for a role that entry leaves null.
     */
    @Builder.Default
    EngineConnectors defaults = EngineConnectors.builder().build();
    /**
     * By engine id, kept for engines currently absent: the registry only knows engines that announced themselves.
     */
    @Singular
    Map<String, EngineConnectors> engines;
}
