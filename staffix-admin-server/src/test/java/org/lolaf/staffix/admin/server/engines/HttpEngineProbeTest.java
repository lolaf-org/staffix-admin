package org.lolaf.staffix.admin.server.engines;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HttpEngineProbeTest {

    private final HttpEngineProbe probe = new HttpEngineProbe(
            new AdminProperties(Duration.ofSeconds(90), Duration.ofSeconds(2), Path.of("data"), null, false),
            new DefaultSslBundleRegistry(), RestClient.builder());
    private HttpServer connector;
    private URI baseUrl;

    @BeforeEach
    void startConnector() throws Exception {
        connector = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        connector.createContext("/engines/alpha-engine/", exchange -> {
            boolean authorized = "Bearer alpha-token".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = """
                    {"engineId":"alpha-engine","staffixVersion":"0.9.1","apiVersions":["v1"]}""".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(authorized ? 200 : 401, authorized ? body.length : -1);
            if (authorized) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        connector.start();
        baseUrl = URI.create("http://127.0.0.1:" + connector.getAddress().getPort() + "/engines/alpha-engine");
    }

    @AfterEach
    void stopConnector() {
        connector.stop(0);
    }

    @Test
    void anEngineAnswersAsItselfWithItsToken() {
        assertThat(probe.answersAs(baseUrl, "alpha-token", "alpha-engine")).isTrue();
    }

    @Test
    void anotherTokenOrEngineIdDoesNotAnswer() {
        assertThat(probe.answersAs(baseUrl, "guessed-token", "alpha-engine")).isFalse();
        assertThat(probe.answersAs(baseUrl, "alpha-token", "beta-engine")).isFalse();
    }

    @Test
    void aClosedPortDoesNotAnswer() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThat(probe.answersAs(URI.create("http://127.0.0.1:" + closedPort + "/engines/alpha-engine"), "alpha-token",
                "alpha-engine")).isFalse();
    }
}
