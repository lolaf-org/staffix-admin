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
