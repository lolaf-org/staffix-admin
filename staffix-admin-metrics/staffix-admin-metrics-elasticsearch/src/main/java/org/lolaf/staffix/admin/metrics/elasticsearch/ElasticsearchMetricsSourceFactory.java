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
package org.lolaf.staffix.admin.metrics.elasticsearch;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.elasticsearch.ElasticsearchClient;
import org.lolaf.staffix.admin.metrics.MetricsSource;
import org.lolaf.staffix.admin.metrics.MetricsSourceFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public class ElasticsearchMetricsSourceFactory implements MetricsSourceFactory<ElasticsearchMetricsSettings> {

    public static final String KIND = "elasticsearch-metrics";

    private final RestClient.Builder builder;
    private final JsonMapper jsonMapper;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public Class<ElasticsearchMetricsSettings> getSettingsClass() {
        return ElasticsearchMetricsSettings.class;
    }

    @Override
    public MetricsSource create(ElasticsearchMetricsSettings settings) {
        ElasticsearchClient.check(settings);
        return new ElasticsearchMetricsSource(settings, builder.clone(), jsonMapper);
    }
}
