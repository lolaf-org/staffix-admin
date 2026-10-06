package org.lolaf.staffix.admin.server.engines;

import lombok.experimental.UtilityClass;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.client.ClientHttpRequestFactory;

@UtilityClass
class EngineRequestFactory {

    ClientHttpRequestFactory create(AdminProperties properties, SslBundles sslBundles) {
        HttpClientSettings settings = properties.getEngineSslBundle() == null ? HttpClientSettings.defaults()
                : HttpClientSettings.ofSslBundle(sslBundles.getBundle(properties.getEngineSslBundle()));
        return ClientHttpRequestFactoryBuilder.jdk().build(settings
                .withTimeouts(properties.getEngineRequestTimeout(), properties.getEngineRequestTimeout())
                .withRedirects(HttpRedirects.DONT_FOLLOW));
    }
}
