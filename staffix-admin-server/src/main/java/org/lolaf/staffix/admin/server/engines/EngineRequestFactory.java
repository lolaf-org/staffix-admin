package org.lolaf.staffix.admin.server.engines;

import lombok.experimental.UtilityClass;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;

@UtilityClass
class EngineRequestFactory {

    JdkClientHttpRequestFactory create(AdminProperties properties, SslBundles sslBundles) {
        HttpClient.Builder httpClient = HttpClient.newBuilder().connectTimeout(properties.getEngineRequestTimeout());
        if (properties.getEngineSslBundle() != null) {
            httpClient.sslContext(sslBundles.getBundle(properties.getEngineSslBundle()).createSslContext());
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient.build());
        requestFactory.setReadTimeout(properties.getEngineRequestTimeout());
        return requestFactory;
    }
}
