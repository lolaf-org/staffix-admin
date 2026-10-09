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
