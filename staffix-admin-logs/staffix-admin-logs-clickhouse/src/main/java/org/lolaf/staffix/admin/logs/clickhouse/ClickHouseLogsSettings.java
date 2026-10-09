package org.lolaf.staffix.admin.logs.clickhouse;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.clickhouse.ClickHouseConnection;
import org.lolaf.staffix.admin.connector.Description;
import org.lolaf.staffix.admin.connector.Secret;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@Value
public class ClickHouseLogsSettings implements ClickHouseConnection {
    @Description("ClickHouse's HTTP interface, e.g. http://localhost:8123.")
    URI url;
    @Description("How long a query may take, connecting included, before the console reports ClickHouse did not answer.")
    Duration timeout;
    @Description("The database holding the logs table.")
    String database;
    @Description("The logs table. The default is the one the OpenTelemetry collector's clickhouse exporter creates.")
    String table;
    @Description("The ClickHouse user the console queries as; it only needs to read the table.")
    String username;
    @Description("The password of the username; leave empty for a user without one.")
    @Nullable
    @Secret
    String password;

    public ClickHouseLogsSettings(URI url,
                                  @DefaultValue("10s") Duration timeout,
                                  @DefaultValue("otel") String database,
                                  @DefaultValue("otel_logs") String table,
                                  @DefaultValue("default") String username,
                                  @Nullable String password) {
        this.url = url;
        this.timeout = timeout;
        this.database = database;
        this.table = table;
        this.username = username;
        this.password = password;
    }
}
