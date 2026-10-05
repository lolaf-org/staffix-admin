package org.lolaf.staffix.admin.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serves the simulated engines' metrics and logs to the console's demo connectors: {@code GET /demo/metrics},
 * {@code GET /demo/logs} and {@code GET /demo/health}. {@code sessionId} is a config id, as in the console's queries;
 * a config that does not run yields nothing, as a real backend holds nothing for it.
 */
@Slf4j
final class DemoServer {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Map<String, SimulatedEngine> engines;
    private final HttpServer server;

    DemoServer(List<SimulatedEngine> engines, int port) throws IOException {
        this.engines = engines.stream().collect(Collectors.toMap(SimulatedEngine::getInstanceId, engine -> engine));
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/demo/", this::handle);
    }

    void start() {
        server.start();
        log.info("Demo metrics and logs served on port {}", server.getAddress().getPort());
    }

    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            Object body = switch (exchange.getRequestURI().getPath()) {
                case "/demo/health" -> null;
                case "/demo/metrics" -> DemoTelemetry.metrics(sessions(query), query.get("meter"), query.get("statistic"),
                        query.containsKey("quantile") ? Double.valueOf(query.get("quantile")) : null,
                        List.copyOf(values(query.get("splitBy"))), filters(query.get("tagFilters")),
                        Instant.parse(query.get("from")), Instant.parse(query.get("to")), Duration.ofSeconds(Long.parseLong(query.get("step"))),
                        query.containsKey("rateWindow") ? Duration.ofSeconds(Long.parseLong(query.get("rateWindow"))) : null);
                case "/demo/logs" -> DemoTelemetry.logs(sessions(query), Instant.parse(query.get("from")), Instant.parse(query.get("to")),
                        query.get("text"), values(query.get("types")), values(query.get("msgTypes")), Integer.parseInt(query.get("limit")));
                default -> throw new NotFound("No resource " + exchange.getRequestURI().getPath());
            };
            if (body == null) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                send(exchange, 200, JSON.writeValueAsBytes(body));
            }
        } catch (NotFound e) {
            send(exchange, 404, problem(404, e.getMessage()));
        } catch (RuntimeException e) {
            send(exchange, 400, problem(400, e.toString()));
        }
    }

    private List<SimulatedSession> sessions(Map<String, String> query) {
        SimulatedEngine engine = engines.get(query.get("engineId"));
        if (engine == null) {
            throw new NotFound("Unknown engine '" + query.get("engineId") + "'");
        }
        String group = query.get("group");
        String configId = query.get("sessionId");
        if (configId != null && (group == null || !engine.hasConfig(group, configId))) {
            throw new NotFound("Engine '" + engine.getInstanceId() + "' has no session '" + group + "." + configId + "'");
        }
        return engine.runningConfigs().stream()
                .filter(session -> group == null || session.group().equals(group))
                .filter(session -> configId == null || session.name().equals(configId))
                .toList();
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                int equals = pair.indexOf('=');
                if (equals > 0) {
                    query.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return query;
    }

    private static Map<String, String> filters(String commaSeparated) {
        Map<String, String> filters = new HashMap<>();
        values(commaSeparated).forEach(filter -> filters.put(filter.substring(0, filter.indexOf('=')), filter.substring(filter.indexOf('=') + 1)));
        return filters;
    }

    private static Set<String> values(String commaSeparated) {
        return commaSeparated == null || commaSeparated.isBlank() ? Set.of() : Set.copyOf(Arrays.asList(commaSeparated.split(",")));
    }

    private static byte[] problem(int status, String detail) throws IOException {
        return JSON.writeValueAsBytes(Map.of("status", status, "detail", detail));
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", status < 300 ? "application/json" : "application/problem+json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static final class NotFound extends RuntimeException {
        NotFound(String message) {
            super(message);
        }
    }
}
