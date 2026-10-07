package org.lolaf.staffix.admin.server.trace;

import lombok.Getter;

import java.util.List;

@Getter
public class InvalidTraceSettingsException extends RuntimeException {

    private final List<String> problems;

    InvalidTraceSettingsException(List<String> problems) {
        super("The trace settings of the console configuration are invalid: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }
}
