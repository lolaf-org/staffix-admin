package org.lolaf.staffix.admin.server.web;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TimeRangeTest {

    private static final Instant FROM = Instant.parse("2026-10-05T08:00:00Z");

    private static Duration stepOver(Duration period) {
        return TimeRange.of(FROM, FROM.plus(period)).chartStep();
    }

    @Test
    void stepsBySecondOverAFewMinutes() {
        assertThat(stepOver(Duration.ofMinutes(2))).isEqualTo(Duration.ofSeconds(1));
        assertThat(stepOver(Duration.ofMinutes(4))).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void roundsTheStepUpToAWholeSecond() {
        assertThat(stepOver(Duration.ofMinutes(7))).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void keepsTheChartToAboutTwoHundredFortyPoints() {
        assertThat(stepOver(Duration.ofHours(1))).isEqualTo(Duration.ofSeconds(15));
    }
}
