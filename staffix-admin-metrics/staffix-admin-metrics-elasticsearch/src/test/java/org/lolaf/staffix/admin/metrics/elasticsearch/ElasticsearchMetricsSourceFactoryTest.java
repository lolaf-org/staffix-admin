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

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchMetricsSourceFactoryTest {

    private final ElasticsearchMetricsSourceFactory factory = new ElasticsearchMetricsSourceFactory(RestClient.builder(), JsonMapper.builder().build());

    @Test
    void buildsASourcePerConnector() {
        assertThat(factory.create(settings(URI.create("http://alpha:9200"), null, null, null)))
                .isNotSameAs(factory.create(settings(URI.create("http://beta:9200"), null, null, null)));
    }

    @Test
    void checksTheConnection() {
        assertThatThrownBy(() -> factory.create(settings(null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("url is required");
    }

    private static ElasticsearchMetricsSettings settings(URI url, String apiKey, String username, String password) {
        return new ElasticsearchMetricsSettings(url, Duration.ofSeconds(2), "metrics-*.otel-*", apiKey, username, password, TimeUnit.MILLISECONDS, Duration.ofSeconds(10));
    }
}
