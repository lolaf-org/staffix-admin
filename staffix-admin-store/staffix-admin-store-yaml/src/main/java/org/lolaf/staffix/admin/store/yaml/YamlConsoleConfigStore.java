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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Keeps the console configuration in {@code console.yaml} under the data directory, so it can be read, diffed and
 * edited by hand. A save from the UI based on content changed since, by hand or from another console, is refused.
 * Comments do not survive a save, which writes back the line pointing an editor at the distribution's schema.
 */
class YamlConsoleConfigStore implements ConsoleConfigStore {

    static final String FILE_NAME = "console.yaml";
    static final Path SCHEMA = Path.of("config", "console.schema.json");
    private static final HexFormat HEX = HexFormat.of();

    private final Path file;
    private final Path schema;
    private final String schemaComment;
    private final YAMLMapper mapper;

    YamlConsoleConfigStore(Path dataDir) {
        this(dataDir, SCHEMA);
    }

    YamlConsoleConfigStore(Path dataDir, Path schema) {
        this.file = dataDir.resolve(FILE_NAME);
        this.schema = schema;
        this.schemaComment = "# yaml-language-server: $schema=" + schemaLocation(file, schema) + "\n";
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
        byte[] content = read();
        if (content == null) {
            return ConsoleConfig.EMPTY;
        }
        try {
            return mapper.readValue(content, ConsoleConfig.class).toBuilder().revision(revision(content)).build();
        } catch (JacksonException e) {
            throw new ConsoleConfigStoreException("Cannot read the console configuration from " + file + ": " + e.getOriginalMessage(), e);
        }
    }

    @Override
    public synchronized ConsoleConfig save(ConsoleConfig config) {
        byte[] stored = read();
        if (!Objects.equals(config.getRevision(), stored == null ? null : revision(stored))) {
            throw new StaleConsoleConfigException();
        }
        try {
            byte[] content = serialize(config);
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), FILE_NAME, ".tmp");
            try {
                Files.write(temp, content);
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            return config.toBuilder().revision(revision(content)).build();
        } catch (IOException | JacksonException e) {
            throw new ConsoleConfigStoreException("Cannot write the console configuration to " + file, e);
        }
    }

    private byte @Nullable [] read() {
        try {
            return Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            throw new ConsoleConfigStoreException("Cannot read the console configuration from " + file, e);
        }
    }

    private byte[] serialize(ConsoleConfig config) {
        String yaml = mapper.writeValueAsString(config);
        return (Files.isRegularFile(schema) ? schemaComment + yaml : yaml).getBytes(StandardCharsets.UTF_8);
    }

    private static String revision(byte[] content) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM provides SHA-256", e);
        }
    }

    private static String schemaLocation(Path file, Path schema) {
        Path absoluteSchema = schema.toAbsolutePath().normalize();
        try {
            return file.toAbsolutePath().normalize().getParent().relativize(absoluteSchema).toString().replace(File.separatorChar, '/');
        } catch (IllegalArgumentException e) {
            return absoluteSchema.toUri().toString();
        }
    }

    @JsonDeserialize(builder = ConsoleConfig.ConsoleConfigBuilder.class)
    @JsonIgnoreProperties("revision")
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
