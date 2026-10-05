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
    void reportsTheRateWindowAskedForElseTheStep() {
        assertThat(DemoTelemetry.metrics(sessions, READ, "RATE", null, BY_MESSAGE, IN, FROM, TO, STEP, Duration.ofSeconds(20)))
                .isNotEmpty()
                .extracting(DemoTelemetry.Series::rateWindow).containsOnly(20L);
        assertThat(DemoTelemetry.metrics(sessions, READ, "RATE", null, BY_MESSAGE, IN, FROM, TO, STEP, null))
                .extracting(DemoTelemetry.Series::rateWindow).containsOnly(5L);
    }

    @Test
    void splitsByTheTagsAskedKeepingOnlyTheFilteredValues() {
        assertThat(DemoTelemetry.metrics(sessions, READ, "QUANTILE", 0.99, BY_MESSAGE, IN, FROM, TO, STEP, null))
                .extracting(DemoTelemetry.Series::labels)
                .containsExactly(Map.of("fix.msg.type", "0", "fix.msg.dir", "in"), Map.of("fix.msg.type", "8", "fix.msg.dir", "in"),
                        Map.of("fix.msg.type", "D", "fix.msg.dir", "in"));
        assertThat(DemoTelemetry.metrics(sessions, "orders.pricing", "MEAN", null, List.of(), Map.of(), FROM, TO, STEP, null))
                .singleElement().satisfies(one -> assertThat(one.labels()).isEmpty());
    }

    @Test
    void reportsNoRateWindowForTheClockOffset() {
        assertThat(DemoTelemetry.metrics(sessions, "session.clock.offset", "VALUE", null, BY_SESSION, Map.of(), FROM, TO, STEP, Duration.ofSeconds(20)))
                .isNotEmpty()
                .extracting(DemoTelemetry.Series::rateWindow).containsOnlyNulls();
    }

    @Test
    void aWiderRateWindowSmoothsTheChart() {
        double narrow = spread(DemoTelemetry.metrics(sessions, "session.rtt", "MEAN", null, BY_SESSION, Map.of(), FROM, TO, STEP, Duration.ofSeconds(5)));
        double wide = spread(DemoTelemetry.metrics(sessions, "session.rtt", "MEAN", null, BY_SESSION, Map.of(), FROM, TO, STEP, Duration.ofMinutes(1)));

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
