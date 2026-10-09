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
package org.lolaf.staffix.admin.store;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Map;

/**
 * A backend the console reads telemetry from. The kind (e.g. {@code prometheus}, {@code loki}) says whether it serves
 * metrics or logs and how its settings read.
 */
@Value
@Builder(toBuilder = true)
public class Connector {
    String id;
    String name;
    String kind;
    /**
     * As the kind defines them. A string value may be a reference such as {@code ${env:NAME}}, and a secret one is
     * stored encrypted ({@code enc:v1:...}) unless it is a reference; both are resolved when the connector is built,
     * not here.
     */
    @Singular
    Map<String, Object> settings;
}
