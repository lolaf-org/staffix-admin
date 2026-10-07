package org.lolaf.staffix.admin.store;

/**
 * Where the console keeps what admins configure: its connectors, the engines linked to them, the trace settings. The
 * deploying team picks the implementation: a single console can use a file, several consoles behind a load balancer need a shared store.
 */
public interface ConsoleConfigStore {

    /**
     * {@link ConsoleConfig#EMPTY} until the first save.
     *
     * @throws ConsoleConfigStoreException if the store cannot be read
     */
    ConsoleConfig load();

    /**
     * Stores {@code config} if its version is still the stored one, and returns it with the next version.
     *
     * @throws StaleConsoleConfigException if the store changed since {@code config} was loaded
     * @throws ConsoleConfigStoreException if the store cannot be written
     */
    ConsoleConfig save(ConsoleConfig config);
}
