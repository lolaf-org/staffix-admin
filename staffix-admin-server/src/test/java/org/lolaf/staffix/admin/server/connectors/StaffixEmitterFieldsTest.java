package org.lolaf.staffix.admin.server.connectors;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.api.monitoring.FixSessionsMonitoringManager;
import org.lolaf.staffix.monitoring.micrometer.otlp.OtlpMicrometerMonitoringManagerSettings;
import org.lolaf.staffix.stores.loggers.async.AsyncMessagesLoggerSettings;
import org.lolaf.staffix.stores.loggers.file.FileMessagesLoggerSettings;
import org.lolaf.staffix.stores.loggers.otlp.OtlpMessagesLoggerSettings;
import org.lolaf.staffix.stores.loggers.slf4j.Slf4jMessagesLoggerSettings;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the staffix names {@link EngineEmitterSettings} reads: a rename in staffix fails here, not silently in the
 * console.
 */
class StaffixEmitterFieldsTest {

    private static List<String> properties(Class<?> settingsClass) throws Exception {
        return Arrays.stream(Introspector.getBeanInfo(settingsClass).getPropertyDescriptors()).map(PropertyDescriptor::getName).toList();
    }

    @Test
    void namesTheTypesItRecognisesAsStaffixDoes() {
        assertThat(EngineEmitterSettings.OTLP_LOGGER).isEqualTo(OtlpMessagesLoggerSettings.class.getSimpleName());
        assertThat(EngineEmitterSettings.MONITORING).isEqualTo(FixSessionsMonitoringManager.class.getSimpleName());
        assertThat(EngineEmitterSettings.NOT_EXPORTING).containsExactlyInAnyOrder(Slf4jMessagesLoggerSettings.class.getSimpleName(),
                FileMessagesLoggerSettings.class.getSimpleName(), AsyncMessagesLoggerSettings.class.getSimpleName());
    }

    @Test
    void readsPropertiesStaffixSettingsHave() throws Exception {
        assertThat(properties(OtlpMessagesLoggerSettings.class)).contains("otlpEndpointUrl", "instanceId");
        assertThat(properties(OtlpMicrometerMonitoringManagerSettings.class)).contains("otlpEndpointUrl", "instanceId", "baseTimeUnit", "step");
    }
}
