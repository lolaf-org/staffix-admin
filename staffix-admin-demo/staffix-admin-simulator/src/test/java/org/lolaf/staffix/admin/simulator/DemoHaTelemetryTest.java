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

import static org.assertj.core.api.Assertions.assertThat;

class DemoHaTelemetryTest {

    private final List<SimulatedEngine> engines = SimulationScenario.engines();
    private final SimulatedHaPair.Node leader = node("geneva-a");
    private final SimulatedHaPair.Node follower = node("geneva-b");
    private final Instant from = Instant.now().minus(Duration.ofMinutes(4));
    private final Instant to = Instant.now();

    private SimulatedHaPair.Node node(String engineId) {
        return engines.stream().filter(engine -> engine.getInstanceId().equals(engineId)).findFirst().orElseThrow().haNode().orElseThrow();
    }

    private List<DemoTelemetry.Series> read(SimulatedHaPair.Node node, String meter, List<String> splitBy) {
        return DemoHaTelemetry.metrics(node, meter, splitBy, null, from, to, Duration.ofSeconds(1));
    }

    @Test
    void theCurrentLagRisesDuringTheStallOnTheLeaderOnly() {
        assertThat(read(leader, "ha.follower.lag.pending", List.of()).getFirst().points())
                .extracting(DemoTelemetry.Point::value).contains(15.0, 0.0);
        assertThat(read(follower, "ha.follower.lag.pending", List.of()).getFirst().points())
                .extracting(DemoTelemetry.Point::value).containsOnly(0.0);
    }

    @Test
    void aTimerRecordsNothingOnANodeThatDoesNotRunWhatItTimes() {
        assertThat(read(follower, "ha.follower.lag", List.of()).getFirst().points()).isEmpty();
        assertThat(read(leader, "ha.replica.received.write.latency", List.of()).getFirst().points()).isEmpty();
    }

    @Test
    void theLagTimerIsSilentDuringTheStallAndCatchesUpAfterwards() {
        List<DemoTelemetry.Point> lag = read(leader, "ha.follower.lag", List.of()).getFirst().points();

        assertThat(lag.size()).isLessThan(241 - 15);
        assertThat(lag).anyMatch(point -> point.value() > 1);
    }

    @Test
    void aQuantileReadsAboveTheMeanAndIsEmptyForATimerWithoutPercentiles() {
        double mean = DemoHaTelemetry.metrics(leader, "ha.sync.ack.latency", List.of(), null, from, to, Duration.ofSeconds(1))
                .getFirst().points().getFirst().value();
        double p99 = DemoHaTelemetry.metrics(leader, "ha.sync.ack.latency", List.of(), 0.99, from, to, Duration.ofSeconds(1))
                .getFirst().points().getFirst().value();

        assertThat(p99).isGreaterThan(mean);
        assertThat(DemoHaTelemetry.metrics(follower, "ha.replica.received.write.latency", List.of(), 0.99, from, to, Duration.ofSeconds(1))
                .getFirst().points()).isEmpty();
    }

    @Test
    void aSplitMeterGivesOneSeriesPerValue() {
        assertThat(read(leader, "ha.sync.ack.latency", List.of("mode"))).extracting(series -> series.labels().get("mode"))
                .containsExactly("sync_received", "sync_applied");
    }

    @Test
    void theRoleFollowsLeadership() {
        assertThat(read(leader, "ha.role", List.of()).getFirst().points()).extracting(DemoTelemetry.Point::value).containsOnly(2.0);
        assertThat(read(follower, "ha.role", List.of()).getFirst().points()).extracting(DemoTelemetry.Point::value).containsOnly(1.0);

        leader.switchover();

        assertThat(follower.roleAt(Instant.now().plusSeconds(1))).isEqualTo(2.0);
        assertThat(leader.roleAt(Instant.now().plusSeconds(1))).isEqualTo(1.0);
    }
}
