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
package org.lolaf.staffix.admin.simulator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoTelemetryTest {

    private static final Instant FROM = Instant.parse("2026-10-05T08:00:00Z");
    private static final Instant TO = FROM.plusSeconds(600);
    private static final Duration STEP = Duration.ofSeconds(5);
    private static final String READ = "messages.read.latency";
    private static final List<String> BY_MESSAGE = List.of("fix.msg.type", "fix.msg.dir");
    private static final List<String> BY_SESSION = List.of("fix.sg", "fix.sn");
    private static final Map<String, String> IN = Map.of("fix.msg.dir", "in");

    private final List<SimulatedSession> sessions = SimulationScenario.engines().getFirst().runningConfigs();

    @Test
    void sharesTheLogonStatusesOfEachStepWithAnOutageEveryFewHours() {
        Instant to = FROM.plus(Duration.ofHours(6));
        Duration step = Duration.ofMinutes(1);
        List<List<DemoTelemetry.Series>> byStatus = List.of(0.0, 1.0, 2.0, 3.0).stream()
                .map(status -> DemoTelemetry.metrics(sessions, "session.logon.status", "SHARE", null, status, BY_SESSION, Map.of(), FROM, to, step, null))
                .toList();

        List<DemoTelemetry.Point> loggedOut = byStatus.getFirst().getFirst().points();
        for (int i = 0; i < loggedOut.size(); i++) {
            int point = i;
            assertThat(byStatus.stream().mapToDouble(series -> series.getFirst().points().get(point).value()).sum()).isEqualTo(1.0);
        }
        assertThat(loggedOut).anyMatch(point -> point.value() > 0);
        assertThat(byStatus.get(3).getFirst().points()).anyMatch(point -> point.value() > 0);
    }

    @Test
    void reportsTheRateWindowAskedForElseTheStep() {
        assertThat(DemoTelemetry.metrics(sessions, READ, "RATE", null, null, BY_MESSAGE, IN, FROM, TO, STEP, Duration.ofSeconds(20)))
                .isNotEmpty()
                .extracting(DemoTelemetry.Series::rateWindow).containsOnly(20L);
        assertThat(DemoTelemetry.metrics(sessions, READ, "RATE", null, null, BY_MESSAGE, IN, FROM, TO, STEP, null))
                .extracting(DemoTelemetry.Series::rateWindow).containsOnly(5L);
    }

    @Test
    void splitsByTheTagsAskedKeepingOnlyTheFilteredValues() {
        assertThat(DemoTelemetry.metrics(sessions, READ, "QUANTILE", 0.99, null, BY_MESSAGE, IN, FROM, TO, STEP, null))
                .extracting(DemoTelemetry.Series::labels)
                .containsExactly(Map.of("fix.msg.type", "0", "fix.msg.dir", "in"), Map.of("fix.msg.type", "8", "fix.msg.dir", "in"),
                        Map.of("fix.msg.type", "D", "fix.msg.dir", "in"));
        assertThat(DemoTelemetry.metrics(sessions, "orders.pricing", "MEAN", null, null, List.of(), Map.of(), FROM, TO, STEP, null))
                .singleElement().satisfies(one -> assertThat(one.labels()).isEmpty());
    }

    @Test
    void reportsNoRateWindowForTheClockOffset() {
        assertThat(DemoTelemetry.metrics(sessions, "session.clock.offset", "VALUE", null, null, BY_SESSION, Map.of(), FROM, TO, STEP, Duration.ofSeconds(20)))
                .isNotEmpty()
                .extracting(DemoTelemetry.Series::rateWindow).containsOnlyNulls();
    }

    @Test
    void aWiderRateWindowSmoothsTheChart() {
        double narrow = spread(DemoTelemetry.metrics(sessions, "session.rtt", "MEAN", null, null, BY_SESSION, Map.of(), FROM, TO, STEP, Duration.ofSeconds(5)));
        double wide = spread(DemoTelemetry.metrics(sessions, "session.rtt", "MEAN", null, null, BY_SESSION, Map.of(), FROM, TO, STEP, Duration.ofMinutes(1)));

        assertThat(wide).isLessThan(narrow / 2);
    }

    private static double spread(List<DemoTelemetry.Series> series) {
        return series.stream()
                .mapToDouble(one -> {
                    double[] values = one.points().stream().mapToDouble(DemoTelemetry.Point::value).toArray();
                    double sum = 0;
                    for (int i = 1; i < values.length; i++) {
                        sum += Math.abs(values[i] - values[i - 1]);
                    }
                    return sum / (values.length - 1);
                })
                .average()
                .orElseThrow();
    }
}
