package org.lolaf.staffix.admin.server.trace;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.store.TraceConfig;
import org.lolaf.staffix.admin.store.TraceField;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The trace settings in force: the configured ones, the defaults for a list left out. The defaults are applied here
 * rather than written to the stored configuration, so a later release can improve them where nobody changed them.
 */
@Value
public class TraceSettings {
    static final List<Integer> DEFAULT_LINKING_TAGS = List.of(11, 41, 37, 131, 117);
    static final List<TraceField> DEFAULT_KEY_FIELDS = List.of(
            field(11, "ClOrdID"),
            field(41, "OrigClOrdID"),
            field(37, "OrderID"),
            field(131, "QuoteReqID"),
            field(117, "QuoteID"),
            field(150, "ExecType"),
            field(39, "OrdStatus"),
            field(54, "Side"),
            field(55, "Symbol"),
            field(38, "OrderQty"),
            field(44, "Price"),
            field(32, "LastQty"),
            field(31, "LastPx"));

    List<Integer> linkingTags;
    List<TraceField> keyFields;

    /**
     * @throws InvalidTraceSettingsException listing every problem of the configured settings
     */
    static TraceSettings of(@Nullable TraceConfig config) {
        List<Integer> linkingTags = config == null ? null : config.getLinkingTags();
        List<TraceField> keyFields = config == null ? null : config.getKeyFields();
        TraceSettings settings = new TraceSettings(Objects.requireNonNullElse(linkingTags, DEFAULT_LINKING_TAGS),
                Objects.requireNonNullElse(keyFields, DEFAULT_KEY_FIELDS));
        List<String> problems = settings.problems();
        if (!problems.isEmpty()) {
            throw new InvalidTraceSettingsException(problems);
        }
        return settings;
    }

    private List<String> problems() {
        List<String> problems = new ArrayList<>();
        Set<Integer> linking = new HashSet<>();
        for (Integer tag : linkingTags) {
            if (tag == null || tag <= 0) {
                problems.add("linkingTags: " + tag + " is not a tag");
            } else if (!linking.add(tag)) {
                problems.add("linkingTags: " + tag + " is listed twice");
            }
        }
        Set<Integer> shown = new HashSet<>();
        for (TraceField field : keyFields) {
            if (field.getTag() <= 0) {
                problems.add("keyFields: " + field.getTag() + " is not a tag");
            } else if (!shown.add(field.getTag())) {
                problems.add("keyFields: " + field.getTag() + " is listed twice");
            }
            if (field.getName() == null || field.getName().isBlank()) {
                problems.add("keyFields: tag " + field.getTag() + " has no name");
            }
        }
        return problems;
    }

    private static TraceField field(int tag, String name) {
        return TraceField.builder().tag(tag).name(name).build();
    }
}
