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
package org.lolaf.staffix.admin.server.secrets;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.connector.SecretResolver;

import java.util.function.UnaryOperator;

@RequiredArgsConstructor
public class EnvSecretResolver implements SecretResolver {

    private final UnaryOperator<String> environment;

    @Override
    public String getScheme() {
        return "env";
    }

    @Override
    public String resolve(String reference) {
        String value = environment.apply(reference);
        if (value == null) {
            throw new IllegalArgumentException("environment variable " + reference + " is not set");
        }
        return value;
    }
}
