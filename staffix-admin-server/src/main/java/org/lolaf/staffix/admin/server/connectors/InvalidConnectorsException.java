package org.lolaf.staffix.admin.server.connectors;

import lombok.Getter;

import java.util.List;

@Getter
public class InvalidConnectorsException extends RuntimeException {

    private final List<String> problems;

    InvalidConnectorsException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }
}
