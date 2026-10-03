package org.lolaf.staffix.admin.server.connectors;

import lombok.experimental.UtilityClass;
import org.lolaf.staffix.admin.store.Connector;
import org.lolaf.staffix.admin.store.ConnectorsConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a save changed, by id only: the audit never holds a setting's value.
 */
@UtilityClass
class ConnectorChanges {

    String describe(ConnectorsConfig before, ConnectorsConfig after) {
        List<String> changes = new ArrayList<>();
        changes(changes, "connector", byId(before), byId(after));
        if (!before.getDefaults().equals(after.getDefaults())) {
            changes.add("changed defaults");
        }
        changes(changes, "engine", before.getEngines(), after.getEngines());
        return "connectors: " + (changes.isEmpty() ? "no change" : String.join("; ", changes));
    }

    private static Map<String, Connector> byId(ConnectorsConfig config) {
        return config.getConnectors().stream().collect(Collectors.toMap(Connector::getId, Function.identity()));
    }

    private static void changes(List<String> changes, String what, Map<String, ?> before, Map<String, ?> after) {
        TreeSet<String> added = new TreeSet<>(after.keySet());
        added.removeAll(before.keySet());
        TreeSet<String> removed = new TreeSet<>(before.keySet());
        removed.removeAll(after.keySet());
        TreeSet<String> changed = after.keySet().stream()
                .filter(id -> before.containsKey(id) && !Objects.equals(before.get(id), after.get(id)))
                .collect(Collectors.toCollection(TreeSet::new));
        add(changes, "added " + what, added);
        add(changes, "changed " + what, changed);
        add(changes, "removed " + what, removed);
    }

    private static void add(List<String> changes, String label, TreeSet<String> ids) {
        if (!ids.isEmpty()) {
            changes.add(label + " " + String.join(", ", ids));
        }
    }
}
