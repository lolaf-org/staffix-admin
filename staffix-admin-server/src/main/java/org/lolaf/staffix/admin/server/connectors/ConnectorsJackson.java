package org.lolaf.staffix.admin.server.connectors;

import lombok.experimental.UtilityClass;
import org.lolaf.staffix.admin.store.Connector;
import org.lolaf.staffix.admin.store.ConnectorsConfig;
import org.lolaf.staffix.admin.store.EngineConnectors;
import org.lolaf.staffix.admin.store.EngineMapping;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;
import tools.jackson.databind.module.SimpleModule;

/**
 * Reads the store's types through their builders, for the YAML file and the API alike; the store SPI stays free of
 * Jackson.
 */
@UtilityClass
class ConnectorsJackson {

    SimpleModule module() {
        SimpleModule module = new SimpleModule("staffix-admin-connectors");
        module.setMixInAnnotation(ConnectorsConfig.class, ConnectorsConfigMixIn.class);
        module.setMixInAnnotation(Connector.class, ConnectorMixIn.class);
        module.setMixInAnnotation(EngineConnectors.class, EngineConnectorsMixIn.class);
        module.setMixInAnnotation(EngineMapping.class, EngineMappingMixIn.class);
        module.setMixInAnnotation(ConnectorsConfig.ConnectorsConfigBuilder.class, BuilderMixIn.class);
        module.setMixInAnnotation(Connector.ConnectorBuilder.class, BuilderMixIn.class);
        module.setMixInAnnotation(EngineConnectors.EngineConnectorsBuilder.class, BuilderMixIn.class);
        module.setMixInAnnotation(EngineMapping.EngineMappingBuilder.class, BuilderMixIn.class);
        return module;
    }

    @JsonDeserialize(builder = ConnectorsConfig.ConnectorsConfigBuilder.class)
    private interface ConnectorsConfigMixIn {
    }

    @JsonDeserialize(builder = Connector.ConnectorBuilder.class)
    private interface ConnectorMixIn {
    }

    @JsonDeserialize(builder = EngineConnectors.EngineConnectorsBuilder.class)
    private interface EngineConnectorsMixIn {
    }

    @JsonDeserialize(builder = EngineMapping.EngineMappingBuilder.class)
    private interface EngineMappingMixIn {
    }

    @JsonPOJOBuilder(withPrefix = "")
    private interface BuilderMixIn {
    }
}
