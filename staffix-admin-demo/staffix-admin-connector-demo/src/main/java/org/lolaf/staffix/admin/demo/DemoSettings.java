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
package org.lolaf.staffix.admin.demo;

import lombok.Value;
import org.lolaf.staffix.admin.connector.Description;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@Value
public class DemoSettings {
    @Description("The simulator's demo endpoint, e.g. http://localhost:8688.")
    URI url;
    @Description("How long a query may take, connecting included, before the console reports the simulator did not answer.")
    Duration timeout;

    public DemoSettings(URI url, @DefaultValue("5s") Duration timeout) {
        this.url = url;
        this.timeout = timeout;
    }
}
