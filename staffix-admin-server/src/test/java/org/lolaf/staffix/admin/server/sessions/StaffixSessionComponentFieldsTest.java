/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
