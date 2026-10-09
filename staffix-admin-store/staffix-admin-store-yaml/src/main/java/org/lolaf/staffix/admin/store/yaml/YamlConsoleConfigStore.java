/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lolaf.staffix.admin.store.yaml;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.lolaf.staffix.admin.store.Connector;
import org.lolaf.staffix.admin.store.ConsoleConfig;
import org.lolaf.staffix.admin.store.ConsoleConfigStore;
import org.lolaf.staffix.admin.store.ConsoleConfigStoreException;
import org.lolaf.staffix.admin.store.EngineConnectors;
import org.lolaf.staffix.admin.store.EngineMapping;
import org.lolaf.staffix.admin.store.StaleConsoleConfigException;
import org.lolaf.staffix.admin.store.TraceConfig;
import org.lolaf.staffix.admin.store.TraceField;
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
 * Keeps the console configuration in {@code console.yaml} under the data directory, so it can be read, diffed and
 * edited by hand. A hand edit that leaves the version unchanged is overwritten by the next save from the UI, and
 * comments do not survive one.
 */
class YamlConsoleConfigStore implements ConsoleConfigStore {

    static final String FILE_NAME = "console.yaml";

    private final Path file;
    private final YAMLMapper mapper;

    YamlConsoleConfigStore(Path dataDir) {
        this.file = dataDir.resolve(FILE_NAME);
        this.mapper = YAMLMapper.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                .enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_EMPTY))
                .addMixIn(ConsoleConfig.class, ConsoleConfigMixIn.class)
                .addMixIn(Connector.class, ConnectorMixIn.class)
                .addMixIn(EngineConnectors.class, EngineConnectorsMixIn.class)
                .addMixIn(EngineMapping.class, EngineMappingMixIn.class)
                .addMixIn(ConsoleConfig.ConsoleConfigBuilder.class, BuilderMixIn.class)
                .addMixIn(Connector.ConnectorBuilder.class, BuilderMixIn.class)
                .addMixIn(EngineConnectors.EngineConnectorsBuilder.class, BuilderMixIn.class)
                .addMixIn(EngineMapping.EngineMappingBuilder.class, BuilderMixIn.class)
                .addMixIn(TraceConfig.class, TraceConfigMixIn.class)
                .addMixIn(TraceField.class, TraceFieldMixIn.class)
                .addMixIn(TraceConfig.TraceConfigBuilder.class, BuilderMixIn.class)
                .addMixIn(TraceField.TraceFieldBuilder.class, BuilderMixIn.class)
                .build();
    }

    @Override
    public ConsoleConfig load() {
        if (!Files.exists(file)) {
            return ConsoleConfig.EMPTY;
        }
        try {
            return mapper.readValue(file, ConsoleConfig.class);
        } catch (JacksonException e) {
            throw new ConsoleConfigStoreException("Cannot read the console configuration from " + file + ": " + e.getOriginalMessage(), e);
        }
    }

    @Override
    public synchronized ConsoleConfig save(ConsoleConfig config) {
        long storedVersion = load().getVersion();
        if (config.getVersion() != storedVersion) {
            throw new StaleConsoleConfigException(config.getVersion(), storedVersion);
        }
        ConsoleConfig saved = config.toBuilder().version(storedVersion + 1).build();
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
            throw new ConsoleConfigStoreException("Cannot write the console configuration to " + file, e);
        }
        return saved;
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

    /**
     * Keeps an empty list, which means none where an absent one means the defaults.
     */
    @JsonDeserialize(builder = TraceConfig.TraceConfigBuilder.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private interface TraceConfigMixIn {
    }

    @JsonDeserialize(builder = TraceField.TraceFieldBuilder.class)
    private interface TraceFieldMixIn {
    }

    @JsonPOJOBuilder(withPrefix = "")
    private interface BuilderMixIn {
    }

    @JsonDeserialize(builder = EngineMapping.EngineMappingBuilder.class)
    private interface EngineMappingMixIn {
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = NoConnectors.class)
        EngineConnectors getDefaults();
    }

    @SuppressWarnings("EqualsHashCode")
    private static final class NoConnectors {
        private static final EngineConnectors UNSET = EngineConnectors.builder().build();

        @Override
        public boolean equals(Object value) {
            return UNSET.equals(value);
        }
    }
}
