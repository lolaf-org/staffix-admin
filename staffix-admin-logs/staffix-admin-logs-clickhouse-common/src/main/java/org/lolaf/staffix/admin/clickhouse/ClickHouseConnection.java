package org.lolaf.staffix.admin.clickhouse;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Duration;

/**
 * What the logs and the metrics settings both say about reaching ClickHouse.
 */
public interface ClickHouseConnection {

    URI getUrl();

    Duration getTimeout();

    String getDatabase();

    String getUsername();

    @Nullable
    String getPassword();
}
