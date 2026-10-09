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
package org.lolaf.staffix.admin.server.telemetry;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricStatistic;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.server.connectors.TelemetrySources;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.logs.LogsController;
import org.lolaf.staffix.admin.server.metrics.Meter;
import org.lolaf.staffix.admin.server.metrics.MetricsController;
import org.lolaf.staffix.admin.server.metrics.MetricsResult;
import org.lolaf.staffix.admin.server.metrics.SessionMeters;
import org.springframework.web.ErrorResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryResolutionTest {

    private static final Instant TO = Instant.parse("2026-10-02T08:00:00Z");
    private static final Instant FROM = TO.minusSeconds(3600);

    private final TelemetrySources sources = mock(TelemetrySources.class);
    private final EngineGateway gateway = mock(EngineGateway.class);
    private final MetricsController metricsController = new MetricsController(sources, gateway);
    private final LogsController logsController = new LogsController(sources);

    private static LogEntry entry(int secondsBeforeTo) {
        return LogEntry.builder().timestamp(TO.minusSeconds(secondsBeforeTo)).line("line " + secondsBeforeTo).build();
    }

    private static MetricSeries series(String messageType, double... values) {
        MetricSeries.MetricSeriesBuilder series = MetricSeries.builder().label("fix.mt", messageType);
        for (int i = 0; i < values.length; i++) {
            series.point(new MetricPoint(FROM.plusSeconds(60L * i), values[i]));
        }
        return series.build();
    }

    private MetricsResult readLatency(MetricStatistic statistic, @Nullable Duration rateWindow) {
        return metricsController.queryMetrics("alpha", null, null, "messages.read.latency", statistic, 0.99, List.of("fix.mt"), null,
                FROM, TO, rateWindow);
    }

    private static MetricSeries rate(String messageType, Duration rateWindow) {
        return MetricSeries.builder()
                .label("fix.mt", messageType)
                .point(new MetricPoint(FROM, 1))
                .rateWindow(rateWindow)
                .build();
    }

    @Test
    void saysAScopeWithoutConnectorIsNotCollected() {
        when(sources.metrics("alpha", null, null)).thenReturn(new TelemetrySources.Resolved<>(List.of(), List.of(), Map.of()));
        when(sources.logs("alpha", "beta", "trading")).thenReturn(new TelemetrySources.Resolved<>(List.of(), List.of(), Map.of()));

        assertThatThrownBy(() -> readLatency(MetricStatistic.RATE, null))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(404);
                    assertThat(e.getBody().getDetail())
                            .isEqualTo("The metrics of engine alpha are not collected: no metrics connector is assigned to its emitters, "
                                    + "or they export nothing a backend reads");
                });
        assertThatThrownBy(() -> logsController.queryLogs("alpha", "beta", "trading", FROM, TO, null, List.of(), List.of(), List.of(), 10))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(404);
                    assertThat(e.getBody().getDetail()).startsWith("The logs of session beta.trading of engine alpha are not collected");
                });
    }

    @Test
    void namesTheSessionsOfTheScopeNoConnectorCollects() {
        LogsSource initiators = query -> List.of(entry(10));
        when(sources.logs("alpha", null, null)).thenReturn(new TelemetrySources.Resolved<>(List.of(initiators), List.of("beta.drop-copy"), Map.of()));

        assertThat(logsController.queryLogs("alpha", null, null, FROM, TO, null, List.of(), List.of(), List.of(), 10).getNotCollected())
                .containsExactly("beta.drop-copy");
    }

    @Test
    void reportsAConnectorWhoseStoredSettingsAreInvalid() {
        when(sources.logs("alpha", null, null)).thenThrow(new IllegalArgumentException("unknown setting tenat"));

        assertThatThrownBy(() -> logsController.queryLogs("alpha", null, null, FROM, TO, null, List.of(), List.of(), List.of(), 10))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(502);
                    assertThat(e.getBody().getDetail()).isEqualTo("The logs connector is invalid: unknown setting tenat");
                });
    }

    @Test
    void mergesTheLogsOfEverySourceNewestFirstUpToTheLimit() {
        LogsSource initiators = query -> List.of(entry(10), entry(30), entry(50));
        LogsSource clients = query -> List.of(entry(20), entry(40));
        when(sources.logs("alpha", null, null)).thenReturn(new TelemetrySources.Resolved<>(List.of(initiators, clients), List.of(), Map.of()));

        assertThat(logsController.queryLogs("alpha", null, null, FROM, TO, null, List.of(), List.of(), List.of(), 4).getEntries())
                .extracting(LogEntry::getLine).containsExactly("line 10", "line 20", "line 30", "line 40");
    }

    @Test
    void queriesEachSourceInTheTimerUnitOfItsEngines() {
        List<MetricQuery> queries = new ArrayList<>();
        MetricsSource source = query -> {
            queries.add(query);
            return List.of();
        };
        when(sources.metrics("alpha", null, null)).thenReturn(new TelemetrySources.Resolved<>(List.of(source), List.of(),
                Map.of(source, TimeUnit.MICROSECONDS)));

        readLatency(MetricStatistic.QUANTILE, null);

        assertThat(queries).singleElement().extracting(MetricQuery::getTimerBaseUnit).isEqualTo(TimeUnit.MICROSECONDS);
    }

    @Test
    void reportsTheWidestRateWindowOfTheSourcesItCombines() {
        List<MetricQuery> queries = new ArrayList<>();
        MetricsSource initiators = query -> {
            queries.add(query);
            return List.of(rate("D", Duration.ofSeconds(20)));
        };
        MetricsSource clients = query -> List.of(rate("D", Duration.ofMinutes(1)));
        when(sources.metrics("alpha", null, null)).thenReturn(new TelemetrySources.Resolved<>(List.of(initiators, clients), List.of(), Map.of()));

        MetricsResult result = readLatency(MetricStatistic.RATE, Duration.ofSeconds(20));

        assertThat(queries).singleElement().extracting(MetricQuery::getRateWindow).isEqualTo(Duration.ofSeconds(20));
        assertThat(result.getSeries()).singleElement().extracting(MetricSeries::getRateWindow).isEqualTo(Duration.ofMinutes(1));
        assertThat(result.getRateWindow()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void ratesOverTwoExportsAtLeastAndReportsTheNarrowestWindowTheDataAllows() {
        List<MetricQuery> queries = new ArrayList<>();
        MetricsSource prometheus = new MetricsSource() {
            @Override
            public List<MetricSeries> query(MetricQuery query) {
                queries.add(query);
                return List.of();
            }

            @Override
            public Duration getMinRateWindow() {
                return Duration.ofSeconds(20);
            }
        };
        when(sources.metrics("alpha", null, null)).thenReturn(new TelemetrySources.Resolved<>(List.of(prometheus), List.of(), Map.of(),
                Map.of(prometheus, Duration.ofSeconds(15))));

        MetricsResult result = readLatency(MetricStatistic.RATE, Duration.ofSeconds(2));

        assertThat(queries).singleElement().extracting(MetricQuery::getRateWindow).isEqualTo(Duration.ofSeconds(30));
        assertThat(result.getMinRateWindow()).isEqualTo(Duration.ofSeconds(30));
        assertThat(result.getStep()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void readsTheMessageRateFromTheReadTimerInAndTheWriteTimerOut() {
        List<MetricQuery> queries = new ArrayList<>();
        MetricsSource source = query -> {
            queries.add(query);
            return List.of();
        };
        when(sources.metrics("alpha", null, null)).thenReturn(new TelemetrySources.Resolved<>(List.of(source), List.of(), Map.of()));

        metricsController.queryMessageRates("alpha", null, null, FROM, TO, null);

        assertThat(queries).extracting(MetricQuery::getMeter, MetricQuery::getStatistic, MetricQuery::getTagFilters, MetricQuery::getSplitBy)
                .containsExactly(
                        tuple("messages.read.latency", MetricStatistic.RATE, Map.of("fix.md", "i"), List.of("fix.mt", "fix.md")),
                        tuple("messages.write.latency", MetricStatistic.RATE, Map.of("fix.md", "o"), List.of("fix.mt", "fix.md")));
    }

    @Test
    void readsAMessageTimerInTheOnlyDirectionStaffixRecordsItAndACustomOneAsAsked() {
        List<MetricQuery> queries = new ArrayList<>();
        MetricsSource source = query -> {
            queries.add(query);
            return List.of();
        };
        when(sources.metrics("alpha", null, null)).thenReturn(new TelemetrySources.Resolved<>(List.of(source), List.of(), Map.of()));

        metricsController.queryMetrics("alpha", null, null, "messages.encoding.latency", MetricStatistic.MEAN, 0.99, null, null, FROM, TO, null);
        metricsController.queryMetrics("alpha", null, null, "orders.pricing", MetricStatistic.QUANTILE, 0.9, List.of("venue"), null, FROM, TO, null);

        assertThat(queries).extracting(MetricQuery::getTagFilters, MetricQuery::getQuantile, MetricQuery::getSplitBy).containsExactly(
                tuple(Map.of("fix.md", "o"), null, List.of()),
                tuple(Map.of(), 0.9, List.of("venue")));
    }

    @Test
    void listsEachMeterOfTheScopesSessionsOnce() {
        Meter read = Meter.builder().name("messages.read.latency").type(Meter.Type.TIMER).description("Read").tagKey("fix.mt").build();
        Meter pricingAtAlpha = Meter.builder().name("orders.pricing").type(Meter.Type.TIMER).description("Pricing").tagKey("venue").custom(true).build();
        Meter pricingAtBeta = pricingAtAlpha.toBuilder().clearTagKeys().tagKey("desk").percentiles(true).build();
        when(gateway.getMeters("alpha")).thenReturn(List.of(
                SessionMeters.builder().group("beta").name("trading").meter(read).meter(pricingAtAlpha).build(),
                SessionMeters.builder().group("beta").name("drop-copy").meter(read).meter(pricingAtBeta).build(),
                SessionMeters.builder().group("gamma").name("trading").meter(read).build()));

        assertThat(metricsController.getMeters("alpha", "beta", null)).containsExactly(read,
                pricingAtAlpha.toBuilder().tagKey("desk").percentiles(true).build());
        assertThat(metricsController.getMeters("alpha", "beta", "trading")).containsExactly(read, pricingAtAlpha);
        assertThat(metricsController.getMeters("alpha", "delta", null)).isEmpty();
    }

    @Test
    void addsUpTheRatesOfTheSameMessageTypeAcrossSources() {
        MetricsSource initiators = query -> List.of(series("D", 1, 2), series("8", 5));
        MetricsSource clients = query -> List.of(series("D", 10, 20));
        when(sources.metrics("alpha", null, null)).thenReturn(new TelemetrySources.Resolved<>(List.of(initiators, clients), List.of(), Map.of()));

        List<MetricSeries> merged = readLatency(MetricStatistic.RATE, null).getSeries();

        assertThat(merged).containsExactly(series("D", 11, 22), series("8", 5));
    }

    @Test
    void keepsTheHighestQuantileOfTheSameMessageTypeAcrossSources() {
        MetricsSource initiators = query -> List.of(series("D", 0.002, 0.009));
        MetricsSource clients = query -> List.of(series("D", 0.005, 0.001));
        when(sources.metrics("alpha", null, null)).thenReturn(new TelemetrySources.Resolved<>(List.of(initiators, clients), List.of(), Map.of()));

        assertThat(readLatency(MetricStatistic.QUANTILE, null).getSeries())
                .containsExactly(series("D", 0.005, 0.009));
    }
}
