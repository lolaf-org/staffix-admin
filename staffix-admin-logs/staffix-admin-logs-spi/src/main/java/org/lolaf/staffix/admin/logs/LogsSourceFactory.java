package org.lolaf.staffix.admin.logs;

/**
 * Builds the logs source of every connector of one kind, each from its own settings.
 *
 * @param <S> the settings, bound by the console from what the admin entered
 */
public interface LogsSourceFactory<S> {

    /**
     * The kind connectors name, e.g. {@code loki}; unique among metrics and logs factories.
     */
    String getKind();

    Class<S> getSettingsClass();

    /**
     * @throws IllegalArgumentException if the settings cannot make a source, e.g. a required one is missing
     */
    LogsSource create(S settings);
}
