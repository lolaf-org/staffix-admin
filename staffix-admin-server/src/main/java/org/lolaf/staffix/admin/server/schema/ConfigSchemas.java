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
package org.lolaf.staffix.admin.server.schema;

import lombok.experimental.UtilityClass;
import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.lolaf.staffix.admin.server.connectors.ConnectorKind;
import org.lolaf.staffix.admin.server.connectors.ConnectorRole;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes the JSON schemas of {@code application.yml} and {@code console.yaml} from what is on the class path, as the
 * distribution's build does for its {@code config/}.
 */
@UtilityClass
public class ConfigSchemas {

    public static final String APPLICATION = "application.schema.json";
    public static final String CONSOLE = "console.schema.json";

    private static final String METADATA = "META-INF/spring-configuration-metadata.json";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * @param args the directory to write both schemas to
     */
    public static void main(String[] args) throws IOException {
        Path directory = Files.createDirectories(Path.of(args[0]));
        ClassLoader classLoader = ConfigSchemas.class.getClassLoader();
        write(directory.resolve(APPLICATION), ApplicationSchema.of(metadataProperties(classLoader), classLoader));
        write(directory.resolve(CONSOLE), ConsoleSchema.of(connectorKinds()));
    }

    static List<JsonNode> metadataProperties(ClassLoader classLoader) throws IOException {
        List<JsonNode> properties = new ArrayList<>();
        for (URL metadata : Collections.list(classLoader.getResources(METADATA))) {
            try (InputStream in = metadata.openStream()) {
                MAPPER.readTree(in).path("properties").forEach(properties::add);
            }
        }
        return properties;
    }

    /**
     * Starts the plugins' auto-configurations lazily, so only the factories are built.
     */
    static List<ConnectorKind> connectorKinds() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Plugins.class)
                .web(WebApplicationType.NONE)
                .lazyInitialization(true)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .properties("logging.level.root=warn")
                .run()) {
            Stream<ConnectorKind> metrics = context.getBeansOfType(MetricsSourceFactory.class).values().stream()
                    .map(factory -> ConnectorKind.of(factory.getKind(), ConnectorRole.METRICS, factory.getSettingsClass()));
            Stream<ConnectorKind> logs = context.getBeansOfType(LogsSourceFactory.class).values().stream()
                    .map(factory -> ConnectorKind.of(factory.getKind(), ConnectorRole.LOGS, factory.getSettingsClass()));
            return Stream.concat(metrics, logs).sorted(Comparator.comparing(ConnectorKind::getKind)).toList();
        }
    }

    private static void write(Path file, ObjectNode schema) {
        try {
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(schema) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Plugins {
    }
}
