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
