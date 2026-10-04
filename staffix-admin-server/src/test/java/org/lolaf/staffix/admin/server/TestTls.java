package org.lolaf.staffix.admin.server;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * A self-signed certificate for localhost and 127.0.0.1, and a truststore holding it.
 */
@UtilityClass
public class TestTls {

    private static final char[] PASSWORD = "changeit".toCharArray();

    @SneakyThrows
    public SSLContext serverContext() {
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore("tls/server.p12"), PASSWORD);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        return context;
    }

    public KeyStore trustStore() {
        return keyStore("tls/trust.p12");
    }

    @SneakyThrows
    private KeyStore keyStore(String resource) {
        try (InputStream in = TestTls.class.getClassLoader().getResourceAsStream(resource)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(in, PASSWORD);
            return keyStore;
        }
    }
}
