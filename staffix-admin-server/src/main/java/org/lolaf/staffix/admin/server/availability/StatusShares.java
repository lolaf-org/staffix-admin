package org.lolaf.staffix.admin.server.availability;

import lombok.Builder;
import lombok.Value;

/**
 * Shares of a time span, 0 to 1, adding up to 1.
 */
@Value
@Builder
public class StatusShares {
    double loggedIn;
    double loggedOutInsideSessionTime;
    double loggedOutOutsideSessionTime;
    double loggedOutByOperator;
    /**
     * No sample: the engine was down, did not export, or the backend lost them.
     */
    double noData;
}
