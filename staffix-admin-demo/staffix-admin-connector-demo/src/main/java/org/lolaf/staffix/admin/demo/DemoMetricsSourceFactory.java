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

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class DemoMetricsSourceFactory implements MetricsSourceFactory<DemoSettings> {

    public static final String KIND = "demo-metrics";

    private final RestClient.Builder builder;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public Class<DemoSettings> getSettingsClass() {
        return DemoSettings.class;
    }

    @Override
    public MetricsSource create(DemoSettings settings) {
        if (settings.getUrl() == null) {
            throw new IllegalArgumentException("url is required");
        }
        return new DemoMetricsSource(new DemoClient(settings, builder.clone()));
    }
}
