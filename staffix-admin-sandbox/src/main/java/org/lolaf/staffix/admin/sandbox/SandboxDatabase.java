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
package org.lolaf.staffix.admin.sandbox;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One engine's schema in the sandbox's Postgres, which its JDBC stores write to. The messages tables are dropped and
 * created again at start from the DDL the build generated, so they always match the store; the sessions reset their
 * sequence numbers on logon, so nothing worth keeping is lost. The sessions settings table is kept, so a session edited
 * from the console survives a restart.
 */
final class SandboxDatabase implements AutoCloseable {

    /**
     * Postgres's {@code duplicate_table}: the sessions settings table is already there from an earlier run.
     */
    private static final String DUPLICATE_TABLE = "42P07";

    private final HikariDataSource dataSource;

    SandboxDatabase(SandboxSettings settings, String schema) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.jdbcUsername());
        config.setPassword(settings.jdbcPassword());
        config.setSchema(schema);
        config.setPoolName("sandbox-" + schema);
        // short, so a stopped database fails the store's calls, and raises its alert, within seconds
        config.setConnectionTimeout(2000);
        dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("create schema if not exists " + schema);
            statement.execute("set search_path to " + schema);
            for (String sql : ddl("postgres.sql")) {
                statement.execute(sql);
            }
            createSessionsSettingsTable(statement);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the " + schema + " tables in " + dataSource.getJdbcUrl()
                    + ": is the sandbox's Postgres up?", e);
        }
    }

    HikariDataSource dataSource() {
        return dataSource;
    }

    private static void createSessionsSettingsTable(Statement statement) throws SQLException {
        try {
            for (String sql : ddl("sessions-postgres.sql")) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            if (!DUPLICATE_TABLE.equals(e.getSQLState())) {
                throw e;
            }
        }
    }

    private static List<String> ddl(String file) {
        try (InputStream ddl = SandboxDatabase.class.getResourceAsStream("/ddl/" + file)) {
            if (ddl == null) {
                throw new IllegalStateException("No ddl/" + file + ": build the sandbox first");
            }
            return Arrays.stream(new String(ddl.readAllBytes(), StandardCharsets.UTF_8).split(";"))
                    .filter(sql -> !sql.isBlank())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
