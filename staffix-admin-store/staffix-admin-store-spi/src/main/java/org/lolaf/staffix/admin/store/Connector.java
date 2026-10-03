package org.lolaf.staffix.admin.store;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Map;

/**
 * A backend the console reads telemetry from. The kind (e.g. {@code prometheus}, {@code loki}) says whether it serves
 * metrics or logs and how its settings read.
 */
@Value
@Builder(toBuilder = true)
public class Connector {
    String id;
    String name;
    String kind;
    /**
     * As the kind defines them. A string value may be a reference such as {@code ${env:NAME}}, and a secret one is
     * stored encrypted ({@code enc:v1:...}) unless it is a reference; both are resolved when the connector is built,
     * not here.
     */
    @Singular
    Map<String, Object> settings;
}
