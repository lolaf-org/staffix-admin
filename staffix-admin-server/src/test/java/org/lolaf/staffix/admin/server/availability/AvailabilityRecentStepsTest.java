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
package org.lolaf.staffix.admin.server.availability;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.metrics.MetricPoint;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricSeries;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.server.connectors.TelemetrySources;
import org.lolaf.staffix.api.session.FixSessionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvailabilityRecentStepsTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final Duration EXPORT_STEP = Duration.ofSeconds(5);

    @Test
    void leavesOutRecentStepsWhoseExportsAreNotStoredYet() {
        Availability availability = availabilityStoredUntil(NOW.minusSeconds(10));

        assertThat(availability.getSteps().getLast().getEnd()).isEqualTo(NOW.minusSeconds(10));
        assertThat(availability.getTotal().getNoData()).isZero();
    }

    @Test
    void showsACrashedEngineAsMissingDataOnceTheBackendHadTimeToStoreItsExports() {
        Availability availability = availabilityStoredUntil(NOW.minusSeconds(60));

        assertThat(availability.getSteps().getLast().getEnd()).isEqualTo(NOW.minusSeconds(30));
        assertThat(availability.getSteps().getLast().getShares().getNoData()).isEqualTo(1.0);
    }

    private static Availability availabilityStoredUntil(Instant storedUntil) {
        MetricsSource source = mock(MetricsSource.class);
        when(source.query(any())).thenAnswer(invocation -> loggedIn(invocation.getArgument(0), storedUntil));
        TelemetrySources telemetrySources = mock(TelemetrySources.class);
        when(telemetrySources.metrics("alpha", "beta", "trading"))
                .thenReturn(new TelemetrySources.Resolved<>(List.of(source), List.of(), Map.of(), Map.of(source, EXPORT_STEP)));
        AvailabilityController controller = new AvailabilityController(telemetrySources, Clock.fixed(NOW, ZoneOffset.UTC));
        return controller.getAvailability("alpha", "beta", "trading", NOW.minus(Duration.ofMinutes(10)), NOW);
    }

    private static List<MetricSeries> loggedIn(MetricQuery query, Instant storedUntil) {
        if (query.getValue() != FixSessionStatus.LOGGED_IN.getCode()) {
            return List.of();
        }
        List<MetricPoint> points = new ArrayList<>();
        for (Instant end = query.getFrom().plus(query.getStep()); !end.isAfter(storedUntil); end = end.plus(query.getStep())) {
            points.add(new MetricPoint(end, 1.0));
        }
        return List.of(MetricSeries.builder().points(points).build());
    }
}
