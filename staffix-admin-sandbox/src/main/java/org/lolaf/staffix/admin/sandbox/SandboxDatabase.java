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

/**
 * One engine's schema in the sandbox's Postgres, which its JDBC store writes to. The tables are dropped and created
 * again at start from the DDL the build generated, so they always match the store; the sessions reset their sequence
 * numbers on logon, so nothing worth keeping is lost.
 */
final class SandboxDatabase implements AutoCloseable {

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
        recreateTables(schema);
    }

    HikariDataSource dataSource() {
        return dataSource;
    }

    private void recreateTables(String schema) {
        try (InputStream ddl = SandboxDatabase.class.getResourceAsStream("/ddl/postgres.sql");
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (ddl == null) {
                throw new IllegalStateException("No ddl/postgres.sql: build the sandbox first");
            }
            statement.execute("create schema if not exists " + schema);
            statement.execute("set search_path to " + schema);
            for (String sql : new String(ddl.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the " + schema + " tables in " + dataSource.getJdbcUrl()
                    + ": is the sandbox's Postgres up?", e);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
