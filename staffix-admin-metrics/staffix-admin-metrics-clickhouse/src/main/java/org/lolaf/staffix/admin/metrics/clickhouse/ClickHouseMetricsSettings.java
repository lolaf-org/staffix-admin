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
package org.lolaf.staffix.admin.metrics.clickhouse;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.clickhouse.ClickHouseConnection;
import org.lolaf.staffix.admin.connector.Description;
import org.lolaf.staffix.admin.connector.Secret;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Value
public class ClickHouseMetricsSettings implements ClickHouseConnection {
    @Description("ClickHouse's HTTP interface, e.g. http://localhost:8123.")
    URI url;
    @Description("How long a query may take, connecting included, before the console reports ClickHouse did not answer.")
    Duration timeout;
    @Description("The database holding the metrics tables.")
    String database;
    @Description("The table of the timers' histograms. The default is the one the OpenTelemetry collector's clickhouse "
            + "exporter creates.")
    String histogramTable;
    @Description("The table of the gauges. The default is the one the OpenTelemetry collector's clickhouse exporter creates.")
    String gaugeTable;
    @Description("The ClickHouse user the console queries as; it only needs to read the tables.")
    String username;
    @Description("The password of the username; leave empty for a user without one.")
    @Nullable
    @Secret
    String password;
    @Description("The engines' micrometer baseTimeUnit, for engines that do not report their monitoring plugin's. "
            + "Micrometer's OTLP registry defaults to milliseconds.")
    TimeUnit timerBaseUnit;
    @Description("The shortest window a rate is computed over, whatever a chart asks: at least two export intervals, so "
            + "every window holds enough samples.")
    Duration minRateWindow;

    public ClickHouseMetricsSettings(URI url,
                                     @DefaultValue("10s") Duration timeout,
                                     @DefaultValue("otel") String database,
                                     @DefaultValue("otel_metrics_histogram") String histogramTable,
                                     @DefaultValue("otel_metrics_gauge") String gaugeTable,
                                     @DefaultValue("default") String username,
                                     @Nullable String password,
                                     @DefaultValue("milliseconds") TimeUnit timerBaseUnit,
                                     @DefaultValue("10s") Duration minRateWindow) {
        this.url = url;
        this.timeout = timeout;
        this.database = database;
        this.histogramTable = histogramTable;
        this.gaugeTable = gaugeTable;
        this.username = username;
        this.password = password;
        this.timerBaseUnit = timerBaseUnit;
        this.minRateWindow = minRateWindow;
    }
}
