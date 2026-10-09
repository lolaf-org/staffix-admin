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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.lolaf.staffix.admin.server.SteppingClock;
import org.lolaf.staffix.admin.server.dictionaries.DictionaryRef;
import org.lolaf.staffix.admin.server.onboarding.EngineAnnouncement;
import org.lolaf.staffix.admin.server.sessions.ApplicationSetting;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionDesiredState;
import org.lolaf.staffix.admin.server.sessions.SessionDetail;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.lolaf.staffix.admin.server.sessions.SessionStatus;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;
import org.lolaf.staffix.admin.server.sessions.SessionComponents;
import org.lolaf.staffix.admin.server.sessions.SessionSettingsStore;
import org.lolaf.staffix.admin.server.TestTls;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpEngineGatewayTest {

    private static final String SESSION_COMPONENTS = """
            {"applicationFactories":[{"instanceId":"default","applicationIds":["orders"]}],"messagesStores":["default"],
             "messagesLoggers":["otlp"],
             "sessionsPlugins":[{"instanceId":"metrics","pluginTypes":["org.lolaf.staffix.api.monitoring.FixSessionsMonitoringManager"]}]}
            """;

    private static final String SESSIONS = """
            {"instanceId":"alpha-engine","version":"v1","sessions":[
             {"group":"beta","name":"drop-copy","type":"ACCEPTOR","instanceId":"main-acceptor",
              "configs":[{"name":"drop-copy","connectAddresses":[],
                "identity":{"fixVersion":"FIXT.1.1","defaultApplVerId":"9","sender":{"compId":"US"},"target":{"compId":"BETA","locationId":"LDN"}},
                "dictionaries":[{"id":"FIXT.1.1","hash":"aa"},{"id":"default-FIX.5.0-SP2","hash":"bb"}]}],
              "messagesLoggerInstanceId":"default","monitoringInstanceId":null},
             {"group":"alpha","name":"trading","type":"INITIATOR","instanceId":"alpha-initiator",
              "configs":[{"name":"trading","connectAddresses":["alpha.example.com:9001"],
                "identity":{"fixVersion":"FIX.4.4","sender":{"compId":"US"},"target":{"compId":"ALPHA"}},
                "dictionaries":[{"id":"default-FIX.4.4","hash":"cc"}]},
               {"name":"trading-drp","connectAddresses":["drp.example.com:9001"],
                "identity":{"fixVersion":"FIX.4.4","sender":{"compId":"US","subId":"DR"},"target":{"compId":"ALPHA"}},
                "dictionaries":[{"id":"default-FIX.4.4","hash":"cc"}]}],
              "messagesLoggerInstanceId":"otlp","monitoringInstanceId":"otlp-metrics","addedLater":true}]}
            """;
    private static final String STATUS = """
            {"instanceId":"alpha-engine","sessionsVersion":"%s","sessions":[
             {"group":"beta","name":"drop-copy","selectedConfig":"drop-copy",
              "desiredState":"LOGGED_OUT","status":"LOGGED_OUT_BY_OPERATOR","incomingSeqNum":1,"outgoingSeqNum":1},
             {"group":"alpha","name":"trading","selectedConfig":"trading-drp",
              "desiredState":"LOGGED_IN","status":"LOGGED_IN","incomingSeqNum":12,"outgoingSeqNum":34},
             {"group":"alpha","name":"added-since","selectedConfig":"added-since",
              "desiredState":"LOGGED_IN","status":"LOGGED_IN","incomingSeqNum":1,"outgoingSeqNum":1}]}
            """;

    private volatile String sessionsVersion = "v1";
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final SteppingClock clock = new SteppingClock();
    private HttpServer connector;
    /**
     * Only the engine being announced answers its probe, so a test may move it.
     */
    private volatile URI announcing;
    private EngineRegistry registry;
    private HttpEngineGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        connector = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        connector.createContext("/engines/alpha-engine/", this::serve);
        connector.start();
        registry = new EngineRegistry(properties(null), (baseUrl, token, engineId) -> baseUrl.equals(announcing), Clock.systemUTC());
        announce("alpha-token", connector.getAddress().getPort());
        gateway = gateway(null);
    }

    private void serve(HttpExchange exchange) throws IOException {
        String versionedPath = exchange.getRequestURI().getRawPath().substring("/engines/alpha-engine/".length());
        if (!"Bearer alpha-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                || !versionedPath.startsWith("v1/")) {
            exchange.sendResponseHeaders(versionedPath.startsWith("v1/") ? 401 : 404, -1);
            exchange.close();
            return;
        }
        String path = versionedPath.substring("v1/".length());
        String request = exchange.getRequestMethod() + " " + path;
        requests.add(request);
        bodies.put(request, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = switch (path) {
            case "status" -> STATUS.formatted(sessionsVersion).getBytes(StandardCharsets.UTF_8);
            case "sessions" -> SESSIONS.getBytes(StandardCharsets.UTF_8);
            case "dictionaries/default-FIX.4.4" -> "<fix/>".getBytes(StandardCharsets.UTF_8);
            case "session-settings-stores" -> "[{\"instanceId\":\"main\",\"persistent\":true}]".getBytes(StandardCharsets.UTF_8);
            case "session-components" -> SESSION_COMPONENTS.getBytes(StandardCharsets.UTF_8);
            case "schemas/session-settings" -> "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8);
            case "sessions/alpha/trading/application-settings" ->
                    "[{\"id\":\"logon.password\",\"secret\":true}]".getBytes(StandardCharsets.UTF_8);
            default -> new byte[0];
        };
        int status = statuses.getOrDefault(request, body.length > 0 ? 200 : 204);
        if (status >= 400) {
            body = ("{\"status\":" + status + ",\"detail\":\"trading is not logged in\"}").getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set("Content-Type", status >= 400 ? "application/problem+json"
                : path.startsWith("dictionaries/") ? "application/xml" : "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
        if (status != 204) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private static AdminProperties properties(String engineSslBundle) {
        return new AdminProperties(Duration.ofSeconds(90), Duration.ofSeconds(2), Path.of("data"), engineSslBundle, false);
    }

    private HttpEngineGateway gateway(String engineSslBundle) {
        DefaultSslBundleRegistry sslBundles = new DefaultSslBundleRegistry();
        sslBundles.registerBundle("engines", SslBundle.of(SslStoreBundle.of(null, null, TestTls.trustStore())));
        return new HttpEngineGateway(registry, properties(engineSslBundle), sslBundles, RestClient.builder(),
                JsonMapper.builder().build(), clock);
    }

    @AfterEach
    void tearDown() {
        connector.stop(0);
    }

    private void announce(String token, int port) {
        announce(token, URI.create("http://127.0.0.1:" + port + "/engines/alpha-engine"));
    }

    private void announce(String token, URI baseUrl) {
        announcing = baseUrl;
        registry.announce(EngineAnnouncement.builder()
                .engineId("alpha-engine")
                .baseUrl(baseUrl)
                .token(token)
                .build());
    }

    private static SessionRef trading() {
        return SessionRef.of("alpha-engine", "alpha", "trading");
    }

    @Test
    void groupsAreSortedAndEachSessionIsNamedAfterItsMainConfig() {
        List<SessionGroup> groups = gateway.listGroups("alpha-engine");

        assertThat(groups).extracting(SessionGroup::getGroup).containsExactly("alpha", "beta");
        SessionSummary trading = groups.get(0).getSessions().get(0);
        assertThat(trading.getId()).isEqualTo("trading");
        assertThat(trading.getConfigIds()).containsExactly("trading", "trading-drp");
        assertThat(trading.getRunningConfigId()).isEqualTo("trading-drp");
        assertThat(trading.getStatus()).isEqualTo(SessionStatus.LOGGED_IN);
        assertThat(trading.getIdentity().getSender().getSubId()).isEqualTo("DR");
        assertThat(trading.getIdentity().getDefaultApplVerId()).isNull();
        SessionSummary dropCopy = groups.get(1).getSessions().get(0);
        assertThat(dropCopy.getStatus()).isEqualTo(SessionStatus.LOGGED_OUT_BY_OPERATOR);
        assertThat(dropCopy.getDesiredState()).isEqualTo(SessionDesiredState.LOGGED_OUT);
        assertThat(dropCopy.getIdentity().getTarget().getLocationId()).isEqualTo("LDN");
    }

    @Test
    void eachSessionCarriesItsEmitters() {
        List<SessionGroup> groups = gateway.listGroups("alpha-engine");

        SessionSummary trading = groups.get(0).getSessions().get(0);
        assertThat(trading.getMessagesLoggerInstanceId()).isEqualTo("otlp");
        assertThat(trading.getMonitoringInstanceId()).isEqualTo("otlp-metrics");
        SessionSummary dropCopy = groups.get(1).getSessions().get(0);
        assertThat(dropCopy.getMessagesLoggerInstanceId()).isEqualTo("default");
        assertThat(dropCopy.getMonitoringInstanceId()).isNull();
    }

    @Test
    void aSessionDetailListsItsConfigsWithTheirAddresses() {
        SessionDetail detail = gateway.getSession(trading());

        assertThat(detail.getIncomingSeqNum()).isEqualTo(12);
        assertThat(detail.getConfigs()).extracting(config -> config.getId() + " " + config.getConnectAddresses())
                .containsExactly("trading [alpha.example.com:9001]", "trading-drp [drp.example.com:9001]");
    }

    @Test
    void operationsAddressTheSessionByItsName() {
        gateway.logon(trading());
        gateway.reset(trading(), ResetMode.RESET_SEQUENCE);
        gateway.setSeqNums(trading(), null, 42L);
        gateway.sendMessages(trading(), List.of("35=B|", "35=B|148=again|"), '|', true);
        gateway.activate(trading(), "trading");

        assertThat(requests).filteredOn(request -> !request.startsWith("GET ")).containsExactly(
                "POST sessions/alpha/trading/logon",
                "POST sessions/alpha/trading/reset",
                "PUT sessions/alpha/trading/seqnums",
                "POST sessions/alpha/trading/messages",
                "POST sessions/alpha/trading/activate");
        assertThat(bodies.get("POST sessions/alpha/trading/activate")).isEqualTo("{\"config\":\"trading\"}");
        assertThat(bodies.get("POST sessions/alpha/trading/reset")).isEqualTo("{\"mode\":\"RESET_SEQUENCE\"}");
        assertThat(bodies.get("PUT sessions/alpha/trading/seqnums")).isEqualTo("{\"outgoing\":42}");
        assertThat(bodies.get("POST sessions/alpha/trading/messages"))
                .contains("\"messages\":[\"35=B|\",\"35=B|148=again|\"]", "\"separator\":\"|\"", "\"possDup\":true");
    }

    @Test
    void readsWithinTheFreshnessWindowShareOneStatusCall() {
        gateway.listGroups("alpha-engine");
        gateway.getSession(trading());
        gateway.getDictionaries(trading());
        assertThat(requests).containsExactly("GET status", "GET sessions");

        clock.step(HttpEngineGateway.STATUS_FRESH);
        gateway.listGroups("alpha-engine");
        assertThat(requests).as("same sessions version").containsExactly("GET status", "GET sessions", "GET status");
    }

    @Test
    void anotherSessionsVersionReadsTheSessionsAgain() {
        gateway.listGroups("alpha-engine");

        sessionsVersion = "v2";
        clock.step(HttpEngineGateway.STATUS_FRESH);
        gateway.listGroups("alpha-engine");

        assertThat(requests).containsExactly("GET status", "GET sessions", "GET status", "GET sessions");
    }

    @Test
    void aSessionNotYetDescribedIsLeftOutUntilItIs() {
        assertThat(gateway.listGroups("alpha-engine").get(0).getSessions()).extracting(SessionSummary::getId)
                .containsExactly("trading");
    }

    @Test
    void anOperationReadsAFreshStatusAndTheNextPollSeesItsEffect() {
        gateway.listGroups("alpha-engine");

        gateway.logon(trading());
        gateway.listGroups("alpha-engine");

        assertThat(requests).containsExactly("GET status", "GET sessions", "POST sessions/alpha/trading/logon", "GET status");
    }

    @Test
    void aReannouncedEngineIsReadAgainWithinTheWindow() {
        gateway.listGroups("alpha-engine");

        announce("restarted-token", connector.getAddress().getPort());

        assertThatThrownBy(() -> gateway.listGroups("alpha-engine"))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getBody().getDetail()).contains("refused its token"));
    }

    @Test
    void anEngineRefusalKeepsItsStatusAndReason() {
        statuses.put("POST sessions/alpha/trading/messages", 409);

        assertThatThrownBy(() -> gateway.sendMessages(trading(), List.of("35=B|"), '|', false))
                .isInstanceOfSatisfying(GatewayException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getBody().getDetail()).isEqualTo("trading is not logged in");
                });
    }

    @Test
    void unknownSessionsConfigsAndTokensAreReported() throws Exception {
        statuses.put("POST sessions/gamma/trading/logon", 404);
        statuses.put("POST sessions/alpha/trading/activate", 404);

        assertThatThrownBy(() -> gateway.logon(SessionRef.of("alpha-engine", "gamma", "trading")))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> gateway.activate(trading(), "trading-uat"))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        announce("stale-token", connector.getAddress().getPort());
        assertThatThrownBy(() -> gateway.listGroups("alpha-engine"))
                .isInstanceOfSatisfying(GatewayException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getBody().getDetail()).contains("refused its token");
                });
    }

    @Test
    void anEngineThatDoesNotAnswerIsUnavailable() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }
        announce("alpha-token", closedPort);

        assertThatThrownBy(() -> gateway.listGroups("alpha-engine"))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void anHttpsEngineIsCalledWithTheConfiguredBundlesTrust() throws Exception {
        URI httpsBaseUrl = startHttpsConnector();
        announce("alpha-token", httpsBaseUrl);

        assertThat(gateway("engines").listGroups("alpha-engine")).extracting(SessionGroup::getGroup).contains("alpha");
    }

    @Test
    void anHttpsEngineNotTrustedIsUnavailable() throws Exception {
        URI httpsBaseUrl = startHttpsConnector();
        announce("alpha-token", httpsBaseUrl);

        assertThatThrownBy(() -> gateway.listGroups("alpha-engine"))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    private URI startHttpsConnector() throws IOException {
        connector.stop(0);
        HttpsServer httpsConnector = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpsConnector.setHttpsConfigurator(new HttpsConfigurator(TestTls.serverContext()));
        httpsConnector.createContext("/engines/alpha-engine/", this::serve);
        httpsConnector.start();
        connector = httpsConnector;
        return URI.create("https://127.0.0.1:" + connector.getAddress().getPort() + "/engines/alpha-engine");
    }

    @Test
    void eachConfigDecodesWithItsOwnDictionaries() {
        assertThat(gateway.getDictionaries(SessionRef.of("alpha-engine", "alpha", "trading")))
                .containsExactly(DictionaryRef.of("default-FIX.4.4", "cc"));
        assertThat(gateway.getDictionaries(SessionRef.of("alpha-engine", "beta", "drop-copy")))
                .extracting(DictionaryRef::getId).containsExactly("FIXT.1.1", "default-FIX.5.0-SP2");
        assertThat(gateway.getDictionaryXml("alpha-engine", "default-FIX.4.4")).isEqualTo("<fix/>".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void sessionSettingsAreReplacedRemovedAndAddedThroughTheirRoutes() {
        JsonMapper json = JsonMapper.builder().build();
        JsonNode settings = json.readTree("{\"fixSessionType\":\"ACCEPTOR\"}");

        gateway.updateSessionSettings(trading(), settings);
        gateway.removeSession(trading());
        gateway.addSession("alpha-engine", "main", settings);

        assertThat(requests).contains("PUT sessions/alpha/trading/settings", "DELETE sessions/alpha/trading/settings",
                "POST session-settings-stores/main/sessions");
        assertThat(bodies.get("POST session-settings-stores/main/sessions")).isEqualTo("{\"fixSessionType\":\"ACCEPTOR\"}");
    }

    @Test
    void storesSchemaAndApplicationSettingsAreRead() {
        assertThat(gateway.listSessionSettingsStores("alpha-engine"))
                .containsExactly(SessionSettingsStore.builder().instanceId("main").persistent(true).build());
        assertThat(gateway.getSessionSettingsSchema("alpha-engine").path("type").asString()).isEqualTo("object");
        assertThat(gateway.getSessionComponents("alpha-engine")).isEqualTo(SessionComponents.builder()
                .applicationFactory(SessionComponents.ApplicationFactory.builder().instanceId("default").applicationId("orders").build())
                .messagesStore("default")
                .messagesLogger("otlp")
                .sessionsPlugin(SessionComponents.SessionsPlugin.builder()
                        .instanceId("metrics").pluginType("org.lolaf.staffix.api.monitoring.FixSessionsMonitoringManager").build())
                .build());
        assertThat(gateway.getApplicationSettings(trading()))
                .containsExactly(ApplicationSetting.builder().id("logon.password").secret(true).build());
    }
}
