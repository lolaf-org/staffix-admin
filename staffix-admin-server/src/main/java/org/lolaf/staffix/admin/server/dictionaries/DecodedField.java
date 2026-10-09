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
package org.lolaf.staffix.admin.server.dictionaries;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

@Value
@Builder
public class DecodedField {
    int tag;
    /**
     * Null when the dictionary does not know the tag.
     */
    @Nullable
    String name;
    String value;
    /**
     * The enum description of the value, e.g. {@code BUY} for {@code 54=1}; space separated values are described
     * one by one.
     */
    @Nullable
    String meaning;
    MessageSection section;
    /**
     * 0 outside repeating groups, 1 inside one, 2 inside a nested one.
     */
    int depth;
    /**
     * True on the first field of each repetition of a group.
     */
    boolean groupStart;
}
