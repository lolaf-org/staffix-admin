package org.lolaf.staffix.admin.store;

/**
 * Where the console keeps its connectors and the engines linked to them. The deploying team picks the
 * implementation: a single console can use a file, several consoles behind a load balancer need a shared store.
 */
public interface ConnectorStore {

    /**
     * {@link ConnectorsConfig#EMPTY} until the first save.
     *
     * @throws ConnectorStoreException if the store cannot be read
     */
    ConnectorsConfig load();

    /**
     * Stores {@code config} if its version is still the stored one, and returns it with the next version.
     *
     * @throws StaleConnectorsException if the store changed since {@code config} was loaded
     * @throws ConnectorStoreException  if the store cannot be written
     */
    ConnectorsConfig save(ConnectorsConfig config);
}
