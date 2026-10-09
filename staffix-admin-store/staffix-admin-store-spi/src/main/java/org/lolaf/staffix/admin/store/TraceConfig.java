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
package org.lolaf.staffix.admin.store;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * How the order trace follows an id and what it shows of each message. A null list means the console's defaults, an
 * empty one none: no linking tags, the trace stops at the messages carrying the id asked.
 */
@Value
@Builder(toBuilder = true)
public class TraceConfig {
    /**
     * The tags whose values tie the messages of one order or quote together, e.g. ClOrdID(11), OrderID(37).
     */
    @Nullable
    List<Integer> linkingTags;
    /**
     * The fields shown for each message, in this order.
     */
    @Nullable
    List<TraceField> keyFields;
}
