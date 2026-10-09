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
package org.lolaf.staffix.admin.server.engines;

import lombok.extern.slf4j.Slf4j;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.net.URI;

@Slf4j
@Component
class HttpEngineProbe implements EngineProbe {

    private final RestClient client;

    HttpEngineProbe(AdminProperties properties, SslBundles sslBundles, RestClient.Builder builder) {
        this.client = builder.requestFactory(EngineRequestFactory.create(properties, sslBundles)).build();
    }

    @Override
    public boolean answersAs(URI baseUrl, String token, String engineId) {
        try {
            JsonNode info = client.get()
                    .uri(URI.create(baseUrl.toString().replaceAll("/+$", "") + "/"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
            return info != null && engineId.equals(info.path("engineId").asString(null));
        } catch (RestClientException | IllegalArgumentException e) {
            log.debug("Engine {} does not answer at {}: {}", engineId, baseUrl, e.toString());
            return false;
        }
    }
}
