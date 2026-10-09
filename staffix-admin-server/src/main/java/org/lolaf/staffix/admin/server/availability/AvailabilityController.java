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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.metrics.MetricQuery;
import org.lolaf.staffix.admin.metrics.MetricStatistic;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceException;
import org.lolaf.staffix.admin.server.connectors.TelemetrySources;
import org.lolaf.staffix.admin.server.web.TelemetryProblems;
import org.lolaf.staffix.admin.server.web.TimeRange;
import org.lolaf.staffix.api.session.FixSessionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

@RestController
@RequiredArgsConstructor
public class AvailabilityController {

    private static final String LOGON_STATUS = "session.logon.status";
    /**
     * Below rounding errors of shares adding up to 1.
     */
    private static final double NO_DATA_MIN = 1e-9;

    private final TelemetrySources telemetrySources;

    /**
     * A session's statuses over the period, from the logon status gauge its engine exports. Steps are whole exports
     * long, so each holds the same number of samples.
     */
    @GetMapping("/api/availability")
    public Availability getAvailability(@RequestParam String engineId,
                                        @RequestParam String group,
                                        @RequestParam String sessionId,
                                        @RequestParam Instant from,
                                        @RequestParam Instant to) {
        TelemetrySources.Resolved<MetricsSource> resolved = TelemetryProblems.requireSources("metrics", engineId, group, sessionId,
                () -> telemetrySources.metrics(engineId, group, sessionId));
        TimeRange range = TimeRange.of(from, to);
        Duration step = wholeExports(range.chartStep(), longestExportStep(resolved));
        Instant start = Instant.ofEpochSecond(Math.floorDiv(from.getEpochSecond(), step.toSeconds()) * step.toSeconds());
        Map<FixSessionStatus, Map<Instant, Double>> sharesByStatus = new EnumMap<>(FixSessionStatus.class);
        try {
            for (FixSessionStatus status : FixSessionStatus.values()) {
                MetricQuery query = MetricQuery.builder()
                        .engineId(engineId)
                        .group(group)
                        .sessionId(sessionId)
                        .meter(LOGON_STATUS)
                        .statistic(MetricStatistic.SHARE)
                        .value((double) status.getCode())
                        .from(start)
                        .to(range.getTo())
                        .step(step)
                        .build();
                Map<Instant, Double> shares = new HashMap<>();
                resolved.sources().forEach(source -> source.query(query.toBuilder().sampleInterval(resolved.exportSteps().get(source)).build())
                        .forEach(series -> series.getPoints().forEach(point -> shares.merge(point.getTimestamp(), point.getValue(), Math::max))));
                sharesByStatus.put(status, shares);
            }
        } catch (MetricsSourceException e) {
            throw TelemetryProblems.backendFailed("metrics", e);
        }
        List<AvailabilityStep> steps = new ArrayList<>();
        for (Instant end = start.plus(step); !end.isAfter(range.getTo()); end = end.plus(step)) {
            Instant at = end;
            steps.add(new AvailabilityStep(end, shares(status -> sharesByStatus.get(status).getOrDefault(at, 0.0))));
        }
        StatusShares total = average(steps);
        double meantToBeLoggedIn = total.getLoggedIn() + total.getLoggedOutInsideSessionTime();
        return Availability.builder()
                .step(step)
                .steps(steps)
                .total(total)
                .ratio(meantToBeLoggedIn == 0 ? null : total.getLoggedIn() / meantToBeLoggedIn)
                .build();
    }

    private static StatusShares shares(ToDoubleFunction<FixSessionStatus> shareOf) {
        double loggedIn = Math.min(1, shareOf.applyAsDouble(FixSessionStatus.LOGGED_IN));
        double inside = Math.min(1, shareOf.applyAsDouble(FixSessionStatus.LOGGED_OUT_INSIDE_SESSION_TIME));
        double outside = Math.min(1, shareOf.applyAsDouble(FixSessionStatus.LOGGED_OUT_OUTSIDE_SESSION_TIME));
        double operator = Math.min(1, shareOf.applyAsDouble(FixSessionStatus.LOGGED_OUT_BY_OPERATOR));
        return StatusShares.builder()
                .loggedIn(loggedIn)
                .loggedOutInsideSessionTime(inside)
                .loggedOutOutsideSessionTime(outside)
                .loggedOutByOperator(operator)
                .noData(noData(1 - loggedIn - inside - outside - operator))
                .build();
    }

    private static double noData(double rest) {
        return rest < NO_DATA_MIN ? 0 : rest;
    }

    private static StatusShares average(List<AvailabilityStep> steps) {
        return StatusShares.builder()
                .loggedIn(average(steps, StatusShares::getLoggedIn))
                .loggedOutInsideSessionTime(average(steps, StatusShares::getLoggedOutInsideSessionTime))
                .loggedOutOutsideSessionTime(average(steps, StatusShares::getLoggedOutOutsideSessionTime))
                .loggedOutByOperator(average(steps, StatusShares::getLoggedOutByOperator))
                .noData(steps.isEmpty() ? 1 : noData(average(steps, StatusShares::getNoData)))
                .build();
    }

    private static double average(List<AvailabilityStep> steps, ToDoubleFunction<StatusShares> share) {
        return steps.stream().mapToDouble(step -> share.applyAsDouble(step.getShares())).average().orElse(0);
    }

    private static @Nullable Duration longestExportStep(TelemetrySources.Resolved<MetricsSource> resolved) {
        return resolved.exportSteps().values().stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private static Duration wholeExports(Duration step, @Nullable Duration exportStep) {
        if (exportStep == null || exportStep.toSeconds() == 0) {
            return step;
        }
        long exports = Math.ceilDiv(step.toSeconds(), exportStep.toSeconds());
        return Duration.ofSeconds(Math.max(1, exports) * exportStep.toSeconds());
    }
}
