package org.lolaf.staffix.admin.server.availability;

import lombok.Value;

import java.time.Instant;

@Value
public class AvailabilityStep {
    /**
     * The step covers the step's length up to here.
     */
    Instant end;
    StatusShares shares;
}
