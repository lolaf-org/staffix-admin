package org.lolaf.staffix.admin.server.sessions;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.api.InstanceProvider;
import org.lolaf.staffix.api.session.FixSessionSettings;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the staffix names the UI's {@code sessionComponentFields.tsx} maps to the engine's session components: a rename
 * in staffix fails here, not silently as a free text field in the console.
 */
class StaffixSessionComponentFieldsTest {

    @Test
    void namesTheFieldsAsStaffixDoes() throws Exception {
        assertThat(Arrays.stream(Introspector.getBeanInfo(FixSessionSettings.class).getPropertyDescriptors()).map(PropertyDescriptor::getName))
                .contains("fixApplicationFactoryInstanceId", "fixApplicationInstanceId", "fixMessageStoreInstanceId",
                        "fixMessageLoggerInstanceId", "fixSessionPluginsInstanceIds");
    }

    @Test
    void takesAnUnsetIdAsStaffixDoes() {
        assertThat(InstanceProvider.DEFAULT_INSTANCE_ID).isEqualTo("default");
    }
}
