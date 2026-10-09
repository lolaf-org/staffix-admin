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
package org.lolaf.staffix.admin.metrics;

/**
 * Builds the metrics source of every connector of one kind, each from its own settings.
 *
 * @param <S> the settings, bound by the console from what the admin entered
 */
public interface MetricsSourceFactory<S> {

    /**
     * The kind connectors name, e.g. {@code prometheus}; unique among metrics and logs factories.
     */
    String getKind();

    Class<S> getSettingsClass();

    /**
     * @throws IllegalArgumentException if the settings cannot make a source, e.g. a required one is missing
     */
    MetricsSource create(S settings);
}
