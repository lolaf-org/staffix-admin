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
package org.lolaf.staffix.admin.connector;

/**
 * Resolves the {@code ${scheme:reference}} values of connector settings, e.g. {@code ${env:LOKI_TENANT}}.
 */
public interface SecretResolver {

    /**
     * Lower case, e.g. {@code env}; unique among the console's resolvers.
     */
    String getScheme();

    /**
     * @throws IllegalArgumentException if the reference resolves to nothing; its message must not hold a secret
     */
    String resolve(String reference);
}
