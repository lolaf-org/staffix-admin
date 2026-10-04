package org.lolaf.staffix.admin.server.engines;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.lolaf.staffix.admin.server.SteppingClock;
import org.lolaf.staffix.admin.server.dictionaries.DictionaryRef;
import org.lolaf.staffix.admin.server.onboarding.EngineAnnouncement;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionDetail;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.lolaf.staffix.admin.server.sessions.SessionHealth;
import org.lolaf.staffix.admin.server.sessions.SessionState;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

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

    private static final String STATUS = """
            {"instanceId":"alpha-engine","sessions":[
             {"group":"beta","name":"drop-copy","type":"ACCEPTOR","instanceId":"main-acceptor",
              "configs":[{"name":"drop-copy","connectAddresses":[]}],"selectedConfig":"drop-copy",
              "identity":{"fixVersion":"FIXT.1.1","defaultApplVerId":"9","sender":{"compId":"US"},"target":{"compId":"BETA","locationId":"LDN"}},
              "loggedIn":false,"connected":false,"withinSessionTime":true,"desiredState":"LOGGED_OUT",
              "incomingSeqNum":1,"outgoingSeqNum":1,"dictionaries":[{"id":"FIXT.1.1","hash":"aa"},{"id":"default-FIX.5.0-SP2","hash":"bb"}],
              "messagesLoggerInstanceId":"default","monitoringInstanceId":null},
             {"group":"alpha","name":"trading","type":"INITIATOR","instanceId":"alpha-initiator",
              "configs":[{"name":"trading","connectAddresses":["alpha.example.com:9001"]},{"name":"trading-drp","connectAddresses":["drp.example.com:9001"]}],
              "selectedConfig":"trading-drp",
              "identity":{"fixVersion":"FIX.4.4","sender":{"compId":"US","subId":"DR"},"target":{"compId":"ALPHA"}},
              "loggedIn":true,"connected":true,"withinSessionTime":true,"desiredState":"LOGGED_IN",
              "incomingSeqNum":12,"outgoingSeqNum":34,"dictionaries":[{"id":"default-FIX.4.4","hash":"cc"}],
              "messagesLoggerInstanceId":"otlp","monitoringInstanceId":"otlp-metrics","addedLater":true}]}
            """;

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final SteppingClock clock = new SteppingClock();
    private HttpServer connector;
    private EngineRegistry registry;
    private HttpEngineGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        connector = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        connector.createContext("/engines/alpha-engine/", exchange -> {
            String path = exchange.getRequestURI().getRawPath().substring("/engines/alpha-engine/".length());
            String request = exchange.getRequestMethod() + " " + path;
            if (!"Bearer alpha-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            requests.add(request);
            bodies.put(request, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = switch (path) {
                case "status" -> STATUS.getBytes(StandardCharsets.UTF_8);
                case "dictionaries/default-FIX.4.4" -> "<fix/>".getBytes(StandardCharsets.UTF_8);
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
        });
        connector.start();
        AdminProperties properties = new AdminProperties(Duration.ofSeconds(90), Duration.ofSeconds(2), Path.of("data"));
        registry = new EngineRegistry(properties, Clock.systemUTC());
        announce("alpha-token", connector.getAddress().getPort());
        gateway = new HttpEngineGateway(registry, properties, RestClient.builder(), JsonMapper.builder().build(), clock);
    }

    @AfterEach
    void tearDown() {
        connector.stop(0);
    }

    private void announce(String token, int port) {
        registry.announce(EngineAnnouncement.builder()
                .engineId("alpha-engine")
                .baseUrl(URI.create("http://127.0.0.1:" + port + "/engines/alpha-engine"))
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
        assertThat(trading.getHealth()).isEqualTo(SessionHealth.LOGGED_IN);
        assertThat(trading.getIdentity().getSender().getSubId()).isEqualTo("DR");
        assertThat(trading.getIdentity().getDefaultApplVerId()).isNull();
        SessionSummary dropCopy = groups.get(1).getSessions().get(0);
        assertThat(dropCopy.getHealth()).isEqualTo(SessionHealth.LOGGED_OUT_WITHIN_SESSION_TIME);
        assertThat(dropCopy.getDesiredState()).isEqualTo(SessionState.LOGGED_OUT);
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
    void operationsAddressTheRunningConfig() {
        gateway.logon(trading());
        gateway.reset(trading(), ResetMode.RESET_SEQUENCE);
        gateway.setSeqNums(trading(), null, 42L);
        gateway.sendMessage(trading(), "35=B|", '|', true);
        gateway.activate(trading(), "trading");

        assertThat(requests).filteredOn(request -> !request.equals("GET status")).containsExactly(
                "POST sessions/alpha/trading-drp/logon",
                "POST sessions/alpha/trading-drp/reset",
                "PUT sessions/alpha/trading-drp/seqnums",
                "POST sessions/alpha/trading-drp/messages",
                "POST sessions/alpha/trading/activate");
        assertThat(bodies.get("POST sessions/alpha/trading-drp/reset")).isEqualTo("{\"mode\":\"RESET_SEQUENCE\"}");
        assertThat(bodies.get("PUT sessions/alpha/trading-drp/seqnums")).isEqualTo("{\"outgoing\":42}");
        assertThat(bodies.get("POST sessions/alpha/trading-drp/messages"))
                .contains("\"message\":\"35=B|\"", "\"separator\":\"|\"", "\"possDup\":true");
    }

    @Test
    void readsWithinTheFreshnessWindowShareOneStatusCall() {
        gateway.listGroups("alpha-engine");
        gateway.getSession(trading());
        gateway.getDictionaries(trading());
        assertThat(requests).containsExactly("GET status");

        clock.step(HttpEngineGateway.STATUS_FRESH);
        gateway.listGroups("alpha-engine");
        assertThat(requests).containsExactly("GET status", "GET status");
    }

    @Test
    void anOperationReadsAFreshStatusAndTheNextPollSeesItsEffect() {
        gateway.listGroups("alpha-engine");

        gateway.logon(trading());
        gateway.listGroups("alpha-engine");

        assertThat(requests).containsExactly("GET status", "GET status", "POST sessions/alpha/trading-drp/logon", "GET status");
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
        statuses.put("POST sessions/alpha/trading-drp/messages", 409);

        assertThatThrownBy(() -> gateway.sendMessage(trading(), "35=B|", '|', false))
                .isInstanceOfSatisfying(GatewayException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getBody().getDetail()).isEqualTo("trading is not logged in");
                });
    }

    @Test
    void unknownSessionsConfigsAndTokensAreReported() throws Exception {
        assertThatThrownBy(() -> gateway.logon(SessionRef.of("alpha-engine", "gamma", "trading")))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> gateway.activate(trading(), "trading-uat"))
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

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
    void aBackupDecodesWithTheDictionariesOfTheConfigItsSessionRuns() {
        assertThat(gateway.getDictionaries(SessionRef.of("alpha-engine", "alpha", "trading")))
                .containsExactly(DictionaryRef.of("default-FIX.4.4", "cc"));
        assertThat(gateway.getDictionaries(SessionRef.of("alpha-engine", "beta", "drop-copy")))
                .extracting(DictionaryRef::getId).containsExactly("FIXT.1.1", "default-FIX.5.0-SP2");
        assertThat(gateway.getDictionaryXml("alpha-engine", "default-FIX.4.4")).isEqualTo("<fix/>".getBytes(StandardCharsets.UTF_8));
    }
}
