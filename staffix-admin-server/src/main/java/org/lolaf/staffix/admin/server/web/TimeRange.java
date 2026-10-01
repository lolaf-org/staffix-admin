package org.lolaf.staffix.admin.server.web;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.time.Duration;
import java.time.Instant;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TimeRange {

    private static final Duration MAX = Duration.ofDays(31);
    private static final int POINTS_PER_CHART = 240;
    private static final Duration MIN_STEP = Duration.ofSeconds(5);

    Instant from;
    Instant to;

    public static TimeRange of(Instant from, Instant to) {
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX) > 0) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "'from' must be before 'to', at most " + MAX.toDays() + " days apart"), null);
        }
        return new TimeRange(from, to);
    }

    /**
     * Enough points for a chart's width, never finer than metrics are usually scraped.
     */
    public Duration chartStep() {
        Duration step = Duration.between(from, to).dividedBy(POINTS_PER_CHART);
        return step.compareTo(MIN_STEP) < 0 ? MIN_STEP : step;
    }
}
