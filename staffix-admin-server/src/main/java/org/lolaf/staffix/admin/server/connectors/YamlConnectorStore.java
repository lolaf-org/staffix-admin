package org.lolaf.staffix.admin.server.connectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.lolaf.staffix.admin.store.Connector;
import org.lolaf.staffix.admin.store.ConnectorStore;
import org.lolaf.staffix.admin.store.ConnectorStoreException;
import org.lolaf.staffix.admin.store.ConnectorsConfig;
import org.lolaf.staffix.admin.store.EngineConnectors;
import org.lolaf.staffix.admin.store.StaleConnectorsException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Keeps the connectors in {@code connectors.yaml} under the data directory, so they can be read, diffed and edited by
 * hand. A hand edit that leaves the version unchanged is overwritten by the next save from the UI, and comments do
 * not survive one.
 */
class YamlConnectorStore implements ConnectorStore {

    static final String FILE_NAME = "connectors.yaml";

    private final Path file;
    private final YAMLMapper mapper;

    YamlConnectorStore(Path dataDir) {
        this.file = dataDir.resolve(FILE_NAME);
        this.mapper = YAMLMapper.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                .enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_EMPTY))
                .addMixIn(ConnectorsConfig.class, ConnectorsConfigMixIn.class)
                .addMixIn(Connector.class, ConnectorMixIn.class)
                .addMixIn(EngineConnectors.class, EngineConnectorsMixIn.class)
                .addMixIn(ConnectorsConfig.ConnectorsConfigBuilder.class, BuilderMixIn.class)
                .addMixIn(Connector.ConnectorBuilder.class, BuilderMixIn.class)
                .addMixIn(EngineConnectors.EngineConnectorsBuilder.class, BuilderMixIn.class)
                .build();
    }

    @Override
    public ConnectorsConfig load() {
        if (!Files.exists(file)) {
            return ConnectorsConfig.EMPTY;
        }
        try {
            return mapper.readValue(file, ConnectorsConfig.class);
        } catch (JacksonException e) {
            throw new ConnectorStoreException("Cannot read the connectors from " + file + ": " + e.getOriginalMessage(), e);
        }
    }

    @Override
    public synchronized ConnectorsConfig save(ConnectorsConfig config) {
        long storedVersion = load().getVersion();
        if (config.getVersion() != storedVersion) {
            throw new StaleConnectorsException(config.getVersion(), storedVersion);
        }
        ConnectorsConfig saved = config.toBuilder().version(storedVersion + 1).build();
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), FILE_NAME, ".tmp");
            try {
                mapper.writeValue(temp, saved);
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException | JacksonException e) {
            throw new ConnectorStoreException("Cannot write the connectors to " + file, e);
        }
        return saved;
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

    @JsonPOJOBuilder(withPrefix = "")
    private interface BuilderMixIn {
    }
}
