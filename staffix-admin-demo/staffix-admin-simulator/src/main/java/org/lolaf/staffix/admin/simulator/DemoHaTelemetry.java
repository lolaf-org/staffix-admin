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

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.api.monitoring.FixMeterDescriptor;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code ha.*} meters of a simulated node, following the pair's script: what the stalled follower and the
 * snapshot do to the lag shows on the charts. A timer records nothing while the node does not run what it times.
 */
@UtilityClass
class DemoHaTelemetry {

    private static final double MICROS = 1e-6;
    private static final Map<String, List<String>> SPLIT_VALUES = Map.of(
            "mode", List.of("sync_received", "sync_applied"),
            "reason", List.of("timed_out", "no_follower"),
            "follower", List.of("1", "2"));
    /**
     * The store write timer without percentiles, as a node configured to skip its histogram lists it.
     */
    static final List<FixMeterDescriptor> TIMERS = List.of(
            timer("ha.follower.lag", "follower", true),
            timer("ha.follower.ack.latency", "follower", true),
            timer("ha.sync.ack.latency", "mode", true),
            timer("ha.replica.received.write.latency", null, false));

    /**
     * A quantile of a timer without percentiles has no points, as a backend holds no histogram for it.
     */
    List<DemoTelemetry.Series> metrics(SimulatedHaPair.Node node, String meter, List<String> splitBy, @Nullable Double quantile,
                                       Instant from, Instant to, Duration step) {
        List<String> values = splitBy.isEmpty() ? List.of("") : SPLIT_VALUES.getOrDefault(splitBy.getFirst(), List.of("default"));
        List<DemoTelemetry.Series> series = new ArrayList<>();
        for (String value : values) {
            Map<String, String> labels = new LinkedHashMap<>();
            if (!splitBy.isEmpty()) {
                labels.put(splitBy.getFirst(), value);
            }
            if (splitBy.isEmpty() || !splitBy.getFirst().equals("follower") || !value.equals(node.nodeId())) {
                series.add(new DemoTelemetry.Series(labels, quantile != null && !hasPercentiles(meter) ? List.of()
                        : points(node, meter, value, tail(meter, quantile), from, to, step), null));
            }
        }
        return series;
    }

    private static FixMeterDescriptor timer(String name, @Nullable String tagKey, boolean percentiles) {
        FixMeterDescriptor.FixMeterDescriptorBuilder timer = FixMeterDescriptor.builder().name(name).type(FixMeterDescriptor.Type.TIMER)
                .description(name).percentiles(percentiles);
        if (tagKey != null) {
            timer.tagKey(tagKey);
        }
        return timer.build();
    }

    private static boolean hasPercentiles(String meter) {
        return TIMERS.stream().anyMatch(timer -> timer.getName().equals(meter) && timer.isPercentiles());
    }

    private static double tail(String meter, @Nullable Double quantile) {
        return quantile == null || !hasPercentiles(meter) ? 1 : 0.5 + 2.5 * quantile * quantile;
    }

    private static List<DemoTelemetry.Point> points(SimulatedHaPair.Node node, String meter, String split, double tail,
                                                    Instant from, Instant to, Duration step) {
        List<DemoTelemetry.Point> points = new ArrayList<>();
        long seconds = step.toSeconds();
        for (long second = from.getEpochSecond() / seconds * seconds; second <= to.getEpochSecond(); second += seconds) {
            Instant t = Instant.ofEpochSecond(second);
            Double value = value(node, meter, split, t);
            if (value != null) {
                points.add(new DemoTelemetry.Point(t, value * tail));
            }
        }
        return points;
    }

    private static @Nullable Double value(SimulatedHaPair.Node node, String meter, String split, Instant t) {
        int phase = node.phaseAt(t);
        boolean leads = node.ledAt(t);
        boolean stalled = phase >= SimulatedHaPair.STALL_TICK && phase < SimulatedHaPair.SNAPSHOT_TICK;
        boolean snapshot = phase >= SimulatedHaPair.SNAPSHOT_TICK && phase < SimulatedHaPair.STEADY_AGAIN_TICK;
        int stalledFor = stalled ? phase - SimulatedHaPair.STALL_TICK + 1 : 0;
        double wobble = 0.8 + 0.4 * (Math.floorMod((t.getEpochSecond() * 2654435761L) ^ meter.hashCode() ^ split.hashCode(), 1000L) / 1000.0);
        return switch (meter) {
            case "ha.role" -> node.roleAt(t);
            case "ha.follower.lag.pending" -> leads ? (double) stalledFor : 0;
            case "ha.follower.unsent" -> leads ? stalledFor * 65_536.0 : 0;
            case "ha.log.written" -> leads ? 400_000 * wobble : null;
            case "ha.follower.lag" -> !leads || stalled ? null
                    : snapshot ? (SimulatedHaPair.STEADY_AGAIN_TICK - phase) * 1.5 : 150 * MICROS * wobble;
            case "ha.follower.ack.latency" -> !leads || stalled ? null : (snapshot ? 2_000 : 300) * MICROS * wobble;
            case "ha.sync.ack.latency" -> !leads || stalled ? null : (split.equals("sync_received") ? 120 : 320) * MICROS * wobble;
            case "ha.sync.unacknowledged" -> leads ? unacknowledged(split, stalled, snapshot) : 0;
            case "ha.snapshots" -> leads && snapshot ? 0.5 : 0;
            case "ha.replica.received.write.latency" -> leads ? null : 80 * MICROS * wobble;
            default -> 0.0;
        };
    }

    private static double unacknowledged(String reason, boolean stalled, boolean snapshot) {
        if (reason.equals("timed_out")) {
            return stalled ? 4 : 0;
        }
        return snapshot ? 2 : 0;
    }
}
