package org.lolaf.staffix.admin.server.connectors;

import lombok.experimental.UtilityClass;
import org.lolaf.staffix.admin.store.Connector;
import org.lolaf.staffix.admin.store.ConsoleConfig;
import org.lolaf.staffix.admin.store.EngineConnectors;
import org.lolaf.staffix.admin.store.EngineMapping;
import org.lolaf.staffix.admin.store.TraceConfig;
import org.lolaf.staffix.admin.store.TraceField;
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
        module.setMixInAnnotation(ConsoleConfig.class, ConsoleConfigMixIn.class);
        module.setMixInAnnotation(Connector.class, ConnectorMixIn.class);
        module.setMixInAnnotation(EngineConnectors.class, EngineConnectorsMixIn.class);
        module.setMixInAnnotation(EngineMapping.class, EngineMappingMixIn.class);
        module.setMixInAnnotation(ConsoleConfig.ConsoleConfigBuilder.class, BuilderMixIn.class);
        module.setMixInAnnotation(Connector.ConnectorBuilder.class, BuilderMixIn.class);
        module.setMixInAnnotation(EngineConnectors.EngineConnectorsBuilder.class, BuilderMixIn.class);
        module.setMixInAnnotation(EngineMapping.EngineMappingBuilder.class, BuilderMixIn.class);
        module.setMixInAnnotation(TraceConfig.class, TraceConfigMixIn.class);
        module.setMixInAnnotation(TraceField.class, TraceFieldMixIn.class);
        module.setMixInAnnotation(TraceConfig.TraceConfigBuilder.class, BuilderMixIn.class);
        module.setMixInAnnotation(TraceField.TraceFieldBuilder.class, BuilderMixIn.class);
        return module;
    }

    @JsonDeserialize(builder = ConsoleConfig.ConsoleConfigBuilder.class)
    private interface ConsoleConfigMixIn {
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

    @JsonDeserialize(builder = TraceConfig.TraceConfigBuilder.class)
    private interface TraceConfigMixIn {
    }

    @JsonDeserialize(builder = TraceField.TraceFieldBuilder.class)
    private interface TraceFieldMixIn {
    }

    @JsonPOJOBuilder(withPrefix = "")
    private interface BuilderMixIn {
    }
}
