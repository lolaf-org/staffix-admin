package org.lolaf.staffix.admin.store;

import lombok.Getter;

@Getter
public class StaleConsoleConfigException extends RuntimeException {

    private final long expectedVersion;
    private final long storedVersion;

    public StaleConsoleConfigException(long expectedVersion, long storedVersion) {
        super("The console configuration changed since it was loaded: version " + storedVersion + " is stored, the change was based on "
                + expectedVersion);
        this.expectedVersion = expectedVersion;
        this.storedVersion = storedVersion;
    }
}
