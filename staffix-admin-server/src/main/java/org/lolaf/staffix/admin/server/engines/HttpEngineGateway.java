package org.lolaf.staffix.admin.server.engines;

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.lolaf.staffix.admin.server.dictionaries.DictionaryRef;
import org.lolaf.staffix.admin.server.sessions.CompIds;
import org.lolaf.staffix.admin.server.sessions.FixIdentity;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionConfig;
import org.lolaf.staffix.admin.server.sessions.SessionDetail;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.lolaf.staffix.admin.server.sessions.SessionHealth;
import org.lolaf.staffix.admin.server.sessions.SessionState;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;
import org.lolaf.staffix.admin.server.sessions.SessionType;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Calls the engines' HTTP admin connectors with the token each announced. A session box is named after its main
 * config, while the connector addresses the config that runs, so each operation first reads the engine's status.
 */
@Component
@Profile("!mock")
class HttpEngineGateway implements EngineGateway {

    private final EngineRegistry registry;
    private final RestClient client;
    private final JsonMapper jsonMapper;

    HttpEngineGateway(EngineRegistry registry, AdminProperties properties, RestClient.Builder builder, JsonMapper jsonMapper) {
        this.registry = registry;
        this.jsonMapper = jsonMapper;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.getEngineRequestTimeout())
                .build());
        requestFactory.setReadTimeout(properties.getEngineRequestTimeout());
        this.client = builder.requestFactory(requestFactory).build();
    }

    @Override
    public List<SessionGroup> listGroups(String engineId) {
        Map<String, SessionGroup.SessionGroupBuilder> groups = new TreeMap<>();
        status(engineId).getSessions().stream()
                .sorted(Comparator.comparing(ConnectorStatus.Session::getGroup).thenComparing(ConnectorStatus.Session::getName))
                .forEach(session -> groups.computeIfAbsent(session.getGroup(), group -> SessionGroup.builder().group(group))
                        .session(summary(session)));
        return groups.values().stream().map(SessionGroup.SessionGroupBuilder::build).toList();
    }

    @Override
    public SessionDetail getSession(SessionRef session) {
        ConnectorStatus.Session status = session(session);
        SessionDetail.SessionDetailBuilder detail = SessionDetail.builder()
                .summary(summary(status))
                .incomingSeqNum(status.getIncomingSeqNum())
                .outgoingSeqNum(status.getOutgoingSeqNum());
        status.getConfigs().forEach(config -> detail.config(SessionConfig.builder()
                .id(config.getName())
                .connectAddresses(config.getConnectAddresses())
                .build()));
        return detail.build();
    }

    @Override
    public JsonNode getSessionSettings(SessionRef session) {
        return call(session.getEngineId(), HttpMethod.GET, runningConfig(session, "settings"), null, JsonNode.class);
    }

    @Override
    public void logon(SessionRef session) {
        call(session.getEngineId(), HttpMethod.POST, runningConfig(session, "logon"), null, Void.class);
    }

    @Override
    public void logout(SessionRef session) {
        call(session.getEngineId(), HttpMethod.POST, runningConfig(session, "logout"), null, Void.class);
    }

    @Override
    public void reset(SessionRef session, ResetMode mode) {
        call(session.getEngineId(), HttpMethod.POST, runningConfig(session, "reset"), Map.of("mode", mode.name()), Void.class);
    }

    @Override
    public void activate(SessionRef session, String configId) {
        ConnectorStatus.Session status = session(session);
        if (status.getConfigs().stream().noneMatch(config -> config.getName().equals(configId))) {
            throw GatewayException.invalid("Session '" + session.getGroup() + "." + session.getSessionId()
                    + "' has no config '" + configId + "'");
        }
        call(session.getEngineId(), HttpMethod.POST, List.of("sessions", session.getGroup(), configId, "activate"), null, Void.class);
    }

    @Override
    public void setSeqNums(SessionRef session, @Nullable Long incoming, @Nullable Long outgoing) {
        Map<String, Long> seqNums = new HashMap<>();
        if (incoming != null) {
            seqNums.put("incoming", incoming);
        }
        if (outgoing != null) {
            seqNums.put("outgoing", outgoing);
        }
        call(session.getEngineId(), HttpMethod.PUT, runningConfig(session, "seqnums"), seqNums, Void.class);
    }

    @Override
    public void sendMessage(SessionRef session, String message, char separator, boolean possDup) {
        call(session.getEngineId(), HttpMethod.POST, runningConfig(session, "messages"),
                Map.of("message", message, "separator", String.valueOf(separator), "possDup", possDup), Void.class);
    }

    /**
     * An engine knows the dictionaries of the configs that run only, so a backup gets those of the config its
     * session runs, which is nearly always the same dictionary.
     */
    @Override
    public List<DictionaryRef> getDictionaries(SessionRef config) {
        return status(config.getEngineId()).getSessions().stream()
                .filter(session -> session.getGroup().equals(config.getGroup())
                        && session.getConfigs().stream().anyMatch(c -> c.getName().equals(config.getSessionId())))
                .findFirst()
                .orElseThrow(() -> GatewayException.unknownSession(config))
                .getDictionaries().stream()
                .map(dictionary -> DictionaryRef.of(dictionary.getId(), dictionary.getHash()))
                .toList();
    }

    @Override
    public byte[] getDictionaryXml(String engineId, String dictionaryId) {
        return call(engineId, HttpMethod.GET, List.of("dictionaries", dictionaryId), null, byte[].class);
    }

    private ConnectorStatus status(String engineId) {
        return call(engineId, HttpMethod.GET, List.of("status"), null, ConnectorStatus.class);
    }

    private ConnectorStatus.Session session(SessionRef session) {
        return status(session.getEngineId()).getSessions().stream()
                .filter(s -> s.getGroup().equals(session.getGroup()) && s.getName().equals(session.getSessionId()))
                .findFirst()
                .orElseThrow(() -> GatewayException.unknownSession(session));
    }

    private List<String> runningConfig(SessionRef session, String operation) {
        return List.of("sessions", session.getGroup(), session(session).getSelectedConfig(), operation);
    }

    private <T> T call(String engineId, HttpMethod method, List<String> path, @Nullable Object body, Class<T> responseType) {
        RegisteredEngine engine = registry.get(engineId);
        RestClient.RequestBodySpec request = client.method(method)
                .uri(uri(engine.getBaseUrl(), path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + engine.getToken());
        if (body != null) {
            request.body(body);
        }
        try {
            return request.retrieve()
                    .onStatus(HttpStatusCode::isError, (req, response) -> {
                        throw problem(engineId, response.getStatusCode(), readProblem(response.getBody().readAllBytes()));
                    })
                    .body(responseType);
        } catch (ResourceAccessException e) {
            throw GatewayException.unavailable(engineId, e);
        }
    }

    private static URI uri(URI baseUrl, List<String> path) {
        StringBuilder uri = new StringBuilder(baseUrl.toString().replaceAll("/+$", ""));
        path.forEach(segment -> uri.append('/').append(UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8)));
        return URI.create(uri.toString());
    }

    private String readProblem(byte[] body) {
        try {
            ProblemDetail problem = jsonMapper.readValue(body, ProblemDetail.class);
            return problem.getDetail() != null ? problem.getDetail() : new String(body, StandardCharsets.UTF_8);
        } catch (RuntimeException notAProblem) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static GatewayException problem(String engineId, HttpStatusCode status, String detail) {
        return switch (status.value()) {
            case 400 -> GatewayException.invalid(detail);
            case 401 -> GatewayException.tokenRefused(engineId);
            case 404 -> GatewayException.notFound(detail);
            case 409 -> GatewayException.rejected(detail);
            default -> GatewayException.failed(engineId, status.value() + " " + detail);
        };
    }

    private static SessionSummary summary(ConnectorStatus.Session session) {
        return SessionSummary.builder()
                .group(session.getGroup())
                .id(session.getName())
                .type(SessionType.valueOf(session.getType()))
                .instanceId(session.getInstanceId())
                .configIds(session.getConfigs().stream().map(ConnectorStatus.Config::getName).toList())
                .runningConfigId(session.getSelectedConfig())
                .identity(identity(session.getIdentity()))
                .health(SessionHealth.of(session.isLoggedIn(), session.isWithinSessionTime()))
                .loggedIn(session.isLoggedIn())
                .connected(session.isConnected())
                .withinSessionTime(session.isWithinSessionTime())
                .desiredState(SessionState.valueOf(session.getDesiredState()))
                .build();
    }

    private static FixIdentity identity(ConnectorStatus.Identity identity) {
        Function<ConnectorStatus.CompIds, CompIds> compIds = ids -> CompIds.builder()
                .compId(ids.getCompId())
                .subId(ids.getSubId())
                .locationId(ids.getLocationId())
                .build();
        return FixIdentity.builder()
                .fixVersion(identity.getFixVersion())
                .defaultApplVerId(identity.getDefaultApplVerId())
                .sender(compIds.apply(identity.getSender()))
                .target(compIds.apply(identity.getTarget()))
                .build();
    }
}
