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
