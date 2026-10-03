package org.lolaf.staffix.admin.store;

import lombok.Getter;

@Getter
public class StaleConnectorsException extends RuntimeException {

    private final long expectedVersion;
    private final long storedVersion;

    public StaleConnectorsException(long expectedVersion, long storedVersion) {
        super("Connectors changed since they were loaded: version " + storedVersion + " is stored, the change was based on "
                + expectedVersion);
        this.expectedVersion = expectedVersion;
        this.storedVersion = storedVersion;
    }
}
