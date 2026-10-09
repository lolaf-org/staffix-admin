package org.lolaf.staffix.admin.server.availability;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@Value
@Builder
public class Availability {
    Duration step;
    @Singular
    List<AvailabilityStep> steps;
    /**
     * Over every step.
     */
    StatusShares total;
    /**
     * Logged in over logged in plus logged out inside session time: an operator logout and the time without data do
     * not count against it. Null when the session was never meant to be logged in.
     */
    @Nullable
    Double ratio;
}
