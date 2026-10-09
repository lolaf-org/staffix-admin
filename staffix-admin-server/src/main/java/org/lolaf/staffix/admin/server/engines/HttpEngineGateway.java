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

import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.AdminProperties;
import org.lolaf.staffix.admin.server.dictionaries.DictionaryRef;
import org.lolaf.staffix.admin.server.sessions.CompIds;
import org.lolaf.staffix.admin.server.sessions.FixIdentity;
import org.lolaf.staffix.admin.server.sessions.ApplicationSetting;
import org.lolaf.staffix.admin.server.sessions.ResetMode;
import org.lolaf.staffix.admin.server.sessions.SessionConfig;
import org.lolaf.staffix.admin.server.sessions.SessionDesiredState;
import org.lolaf.staffix.admin.server.sessions.SessionDetail;
import org.lolaf.staffix.admin.server.sessions.SessionGroup;
import org.lolaf.staffix.admin.server.sessions.SessionStatus;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;
import org.lolaf.staffix.admin.server.metrics.SessionMeters;
import org.lolaf.staffix.admin.server.sessions.SessionComponents;
import org.lolaf.staffix.admin.server.sessions.SessionSettingsStore;
import org.lolaf.staffix.admin.server.sessions.SessionType;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Calls the engines' HTTP admin connectors, version {@value #API_VERSION} of their API, with the token each announced.
 */
@Component
class HttpEngineGateway implements EngineGateway {

    /**
     * Every open page polls its engines every 2s; within this window all of them share one status call, so the
     * engine's work does not grow with the number of viewers.
     */
    static final Duration STATUS_FRESH = Duration.ofSeconds(1);
    static final String API_VERSION = "v1";

    private final EngineRegistry registry;
    private final RestClient client;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final Map<String, CachedStatus> statuses = new ConcurrentHashMap<>();

    HttpEngineGateway(EngineRegistry registry, AdminProperties properties, SslBundles sslBundles, RestClient.Builder builder,
                      JsonMapper jsonMapper, Clock clock) {
        this.registry = registry;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.client = builder.requestFactory(EngineRequestFactory.create(properties, sslBundles)).build();
    }

    @Override
    public List<SessionGroup> listGroups(String engineId) {
        Map<String, SessionGroup.SessionGroupBuilder> groups = new TreeMap<>();
        sessions(engineId).stream()
                .sorted(Comparator.comparing((EngineSession session) -> session.status().getGroup())
                        .thenComparing(session -> session.status().getName()))
                .forEach(session -> groups.computeIfAbsent(session.status().getGroup(), group -> SessionGroup.builder().group(group))
                        .session(summary(session)));
        return groups.values().stream().map(SessionGroup.SessionGroupBuilder::build).toList();
    }

    @Override
    public SessionDetail getSession(SessionRef session) {
        EngineSession engineSession = session(session);
        SessionDetail.SessionDetailBuilder detail = SessionDetail.builder()
                .summary(summary(engineSession))
                .incomingSeqNum(engineSession.status().getIncomingSeqNum())
                .outgoingSeqNum(engineSession.status().getOutgoingSeqNum())
                .sessionSettingsStore(engineSession.description().getSessionSettingsStore());
        engineSession.description().getConfigs().forEach(config -> detail.config(SessionConfig.builder()
                .id(config.getName())
                .connectAddresses(config.getConnectAddresses())
                .build()));
        return detail.build();
    }

    @Override
    public JsonNode getSessionSettings(SessionRef session) {
        return call(session.getEngineId(), HttpMethod.GET, sessionPath(session, "settings"), null, JsonNode.class);
    }

    @Override
    public void updateSessionSettings(SessionRef session, JsonNode settings) {
        operate(session.getEngineId(), HttpMethod.PUT, sessionPath(session, "settings"), settings);
    }

    @Override
    public void removeSession(SessionRef session) {
        operate(session.getEngineId(), HttpMethod.DELETE, sessionPath(session, "settings"), null);
    }

    @Override
    public void addSession(String engineId, String sessionSettingsStore, JsonNode settings) {
        operate(engineId, HttpMethod.POST, List.of("session-settings-stores", sessionSettingsStore, "sessions"), settings);
    }

    @Override
    public JsonNode getSessionSettingsSchema(String engineId) {
        return call(engineId, HttpMethod.GET, List.of("schemas", "session-settings"), null, JsonNode.class);
    }

    @Override
    public List<SessionSettingsStore> listSessionSettingsStores(String engineId) {
        return List.of(call(engineId, HttpMethod.GET, List.of("session-settings-stores"), null, SessionSettingsStore[].class));
    }

    @Override
    public List<SessionMeters> getMeters(String engineId) {
        return List.of(call(engineId, HttpMethod.GET, List.of("meters"), null, SessionMeters[].class));
    }

    @Override
    public SessionComponents getSessionComponents(String engineId) {
        return call(engineId, HttpMethod.GET, List.of("session-components"), null, SessionComponents.class);
    }

    @Override
    public List<ApplicationSetting> getApplicationSettings(SessionRef session) {
        return List.of(call(session.getEngineId(), HttpMethod.GET, sessionPath(session, "application-settings"), null,
                ApplicationSetting[].class));
    }

    @Override
    public JsonNode getEmitters(String engineId) {
        return call(engineId, HttpMethod.GET, List.of("components"), null, JsonNode.class);
    }

    @Override
    public void logon(SessionRef session) {
        operate(session.getEngineId(), HttpMethod.POST, sessionPath(session, "logon"), null);
    }

    @Override
    public void logout(SessionRef session) {
        operate(session.getEngineId(), HttpMethod.POST, sessionPath(session, "logout"), null);
    }

    @Override
    public void reset(SessionRef session, ResetMode mode) {
        operate(session.getEngineId(), HttpMethod.POST, sessionPath(session, "reset"), Map.of("mode", mode.name()));
    }

    @Override
    public void activate(SessionRef session, String configId) {
        operate(session.getEngineId(), HttpMethod.POST, sessionPath(session, "activate"), Map.of("config", configId));
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
        operate(session.getEngineId(), HttpMethod.PUT, sessionPath(session, "seqnums"), seqNums);
    }

    @Override
    public void sendMessages(SessionRef session, List<String> messages, char separator, boolean possDup) {
        operate(session.getEngineId(), HttpMethod.POST, sessionPath(session, "messages"),
                Map.of("messages", messages, "separator", String.valueOf(separator), "possDup", possDup));
    }

    @Override
    public List<DictionaryRef> getDictionaries(SessionRef config) {
        return sessions(config.getEngineId()).stream()
                .filter(session -> session.status().getGroup().equals(config.getGroup()))
                .flatMap(session -> session.description().getConfigs().stream())
                .filter(c -> c.getName().equals(config.getSessionId()))
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

    private List<EngineSession> sessions(String engineId) {
        RegisteredEngine engine = registry.get(engineId);
        CachedStatus cached = statuses.computeIfAbsent(engineId, id -> new CachedStatus());
        synchronized (cached) {
            Instant now = clock.instant();
            if (!cached.isFresh(engine, now)) {
                cached.engine = engine;
                cached.at = now;
                try {
                    ConnectorStatus status = call(engineId, HttpMethod.GET, List.of("status"), null, ConnectorStatus.class);
                    if (cached.described == null || !cached.described.getVersion().equals(status.getSessionsVersion())) {
                        cached.described = call(engineId, HttpMethod.GET, List.of("sessions"), null, ConnectorSessions.class);
                    }
                    cached.sessions = join(cached.described, status);
                    cached.failure = null;
                } catch (GatewayException e) {
                    cached.failure = e;
                }
            }
            if (cached.failure != null) {
                throw cached.failure;
            }
            return cached.sessions;
        }
    }

    /**
     * A session that came after the description was read is left out until the next poll reads the new one.
     */
    private static List<EngineSession> join(ConnectorSessions described, ConnectorStatus status) {
        Map<List<String>, ConnectorSessions.Session> descriptions = new HashMap<>();
        described.getSessions().forEach(session -> descriptions.put(List.of(session.getGroup(), session.getName()), session));
        List<EngineSession> joined = new ArrayList<>();
        for (ConnectorStatus.Session session : status.getSessions()) {
            ConnectorSessions.Session description = descriptions.get(List.of(session.getGroup(), session.getName()));
            if (description != null) {
                joined.add(new EngineSession(description, session));
            }
        }
        return joined;
    }

    /**
     * Marks the status stale after, so the next poll shows the operation's effect.
     */
    private void operate(String engineId, HttpMethod method, List<String> path, @Nullable Object body) {
        try {
            call(engineId, method, path, body, Void.class);
        } finally {
            markStale(engineId);
        }
    }

    private void markStale(String engineId) {
        CachedStatus cached = statuses.get(engineId);
        if (cached != null) {
            synchronized (cached) {
                cached.at = null;
            }
        }
    }

    private EngineSession session(SessionRef session) {
        return sessions(session.getEngineId()).stream()
                .filter(s -> s.status().getGroup().equals(session.getGroup()) && s.status().getName().equals(session.getSessionId()))
                .findFirst()
                .orElseThrow(() -> GatewayException.unknownSession(session));
    }

    private static List<String> sessionPath(SessionRef session, String operation) {
        return List.of("sessions", session.getGroup(), session.getSessionId(), operation);
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
        StringBuilder uri = new StringBuilder(baseUrl.toString().replaceAll("/+$", "")).append('/').append(API_VERSION);
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

    private static SessionSummary summary(EngineSession session) {
        ConnectorSessions.Session description = session.description();
        ConnectorStatus.Session status = session.status();
        return SessionSummary.builder()
                .group(status.getGroup())
                .id(status.getName())
                .type(SessionType.valueOf(description.getType()))
                .instanceId(description.getInstanceId())
                .configIds(description.getConfigs().stream().map(ConnectorSessions.Config::getName).toList())
                .runningConfigId(status.getSelectedConfig())
                .identity(identity(session.selectedConfig().getIdentity()))
                .status(SessionStatus.valueOf(status.getStatus()))
                .desiredState(SessionDesiredState.valueOf(status.getDesiredState()))
                .messagesLoggerInstanceId(description.getMessagesLoggerInstanceId())
                .monitoringInstanceId(description.getMonitoringInstanceId())
                .build();
    }

    private static FixIdentity identity(ConnectorSessions.Identity identity) {
        Function<ConnectorSessions.CompIds, CompIds> compIds = ids -> CompIds.builder()
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

    private record EngineSession(ConnectorSessions.Session description, ConnectorStatus.Session status) {

        ConnectorSessions.Config selectedConfig() {
            return description.getConfigs().stream()
                    .filter(config -> config.getName().equals(status.getSelectedConfig()))
                    .findFirst()
                    .orElse(description.getConfigs().get(0));
        }
    }

    /**
     * Also holds a failed call, so the viewers waiting on an engine that does not answer do not each wait out the
     * request timeout in turn. The description is kept until the status reports another version.
     */
    private static final class CachedStatus {
        @Nullable RegisteredEngine engine;
        @Nullable Instant at;
        @Nullable ConnectorSessions described;
        @Nullable List<EngineSession> sessions;
        @Nullable GatewayException failure;

        boolean isFresh(RegisteredEngine current, Instant now) {
            return engine != null && at != null && now.isBefore(at.plus(STATUS_FRESH))
                    && engine.getBaseUrl().equals(current.getBaseUrl()) && engine.getToken().equals(current.getToken());
        }
    }
}
