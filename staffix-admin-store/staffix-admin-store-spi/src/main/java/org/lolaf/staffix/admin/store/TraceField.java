package org.lolaf.staffix.admin.store;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class TraceField {
    int tag;
    String name;
}
