package org.lolaf.staffix.admin.simulator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoTelemetryTest {

    private static final Instant FROM = Instant.parse("2026-10-05T08:00:00Z");
    private static final Instant TO = FROM.plusSeconds(600);
    private static final Duration STEP = Duration.ofSeconds(5);

    private final List<SimulatedSession> sessions = SimulationScenario.engines().getFirst().runningConfigs();

    @Test
    void reportsTheRateWindowAskedForElseTheStep() {
        assertThat(DemoTelemetry.metrics(sessions, "MESSAGE_RATE", null, FROM, TO, STEP, Duration.ofSeconds(20)))
                .isNotEmpty()
                .extracting(DemoTelemetry.Series::rateWindow).containsOnly(20L);
        assertThat(DemoTelemetry.metrics(sessions, "MESSAGE_RATE", null, FROM, TO, STEP, null))
                .extracting(DemoTelemetry.Series::rateWindow).containsOnly(5L);
    }

    @Test
    void reportsNoRateWindowForTheClockOffset() {
        assertThat(DemoTelemetry.metrics(sessions, "CLOCK_OFFSET", null, FROM, TO, STEP, Duration.ofSeconds(20)))
                .isNotEmpty()
                .extracting(DemoTelemetry.Series::rateWindow).containsOnlyNulls();
    }

    @Test
    void aWiderRateWindowSmoothsTheChart() {
        double narrow = spread(DemoTelemetry.metrics(sessions, "ROUND_TRIP_TIME", null, FROM, TO, STEP, Duration.ofSeconds(5)));
        double wide = spread(DemoTelemetry.metrics(sessions, "ROUND_TRIP_TIME", null, FROM, TO, STEP, Duration.ofMinutes(1)));

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
