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
package org.lolaf.staffix.admin.server.sessions;

import lombok.experimental.UtilityClass;
import tools.jackson.databind.JsonNode;

import java.util.Set;
import java.util.TreeSet;

/**
 * The fields two settings documents differ in, by name only: a value can be a credential, and the audit trail must
 * never hold one.
 */
@UtilityClass
class SettingsChanges {

    static String describe(JsonNode before, JsonNode after) {
        Set<String> changed = new TreeSet<>();
        collect(before, after, "", changed);
        return changed.isEmpty() ? "no change" : "changed " + String.join(", ", changed);
    }

    private static void collect(JsonNode before, JsonNode after, String path, Set<String> changed) {
        if (before.isObject() && after.isObject()) {
            Set<String> names = new TreeSet<>(before.propertyNames());
            names.addAll(after.propertyNames());
            names.forEach(name -> collect(before.path(name), after.path(name), path.isEmpty() ? name : path + "." + name, changed));
        } else if (!before.equals(after)) {
            changed.add(path);
        }
    }
}
