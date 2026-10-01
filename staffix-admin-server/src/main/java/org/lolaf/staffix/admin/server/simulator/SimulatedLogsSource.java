package org.lolaf.staffix.admin.server.simulator;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.sessions.SessionSummary;
import org.lolaf.staffix.admin.server.sessions.SessionType;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.lolaf.staffix.admin.server.simulator.SimulatedTelemetry.noise;

/**
 * Generates each second's traffic on the fly, newest first: heartbeats, order flow, now and then a session event.
 */
@RequiredArgsConstructor
class SimulatedLogsSource implements LogsSource {

    private static final DateTimeFormatter SENDING_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private static final List<String> SYMBOLS = List.of("AAPL", "MSFT", "AIRP.PA", "VOD.L", "EURUSD", "ESZ6");
    private static final List<String> EVENTS = List.of(
            "Logon received, session established",
            "Heartbeat timeout, test request sent",
            "Resend request received for 120 to 0",
            "Disconnected: connection reset by peer",
            "Logout received: end of session time");
    private static final long MAX_SECONDS_SCANNED = 200_000;

    private final SimulatedEngineGateway gateway;

    @Override
    public List<LogEntry> query(LogQuery query) {
        List<SessionSummary> sessions = SimulatedTelemetry.sessions(gateway, query.getInstanceId(), query.getSessionId());
        if (query.getSessionId() != null && sessions.isEmpty()) {
            throw GatewayException.unknownSession(query.getInstanceId(), query.getSessionId());
        }
        List<LogEntry> entries = new ArrayList<>();
        long earliest = Math.max(query.getFrom().getEpochSecond(), query.getTo().getEpochSecond() - MAX_SECONDS_SCANNED / Math.max(sessions.size(), 1));
        for (long second = query.getTo().getEpochSecond(); second >= earliest && entries.size() < query.getLimit(); second--) {
            long current = second;
            sessions.stream()
                    .flatMap(session -> secondOfTraffic(session, current).stream())
                    .filter(e -> query.getTypes().isEmpty() || query.getTypes().contains(e.getType()))
                    .filter(e -> query.getText() == null || e.getLine().contains(query.getText()))
                    .filter(e -> !e.getTimestamp().isAfter(query.getTo()))
                    .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                    .limit(query.getLimit() - entries.size())
                    .forEach(entries::add);
        }
        return entries;
    }

    private static List<LogEntry> secondOfTraffic(SessionSummary session, long second) {
        double random = noise(session.getId(), second);
        List<LogEntry> entries = new ArrayList<>();
        Instant t = Instant.ofEpochSecond(second, (long) (random * 999_000_000));
        String[] parties = session.getFixSessionId().substring(session.getFixSessionId().lastIndexOf(':') + 1).split("->");
        boolean weSendOrders = session.getType() == SessionType.INITIATOR;
        if (random < 0.01) {
            entries.add(entry(session, t, LogType.EVENT, EVENTS.get((int) (random * 100 * EVENTS.size()) % EVENTS.size())));
        }
        if (random > 0.6) {
            String symbol = SYMBOLS.get((int) (random * 1000) % SYMBOLS.size());
            String order = "11=ORD-" + (second % 1_000_000) + "|55=" + symbol + "|54=" + (random > 0.8 ? 2 : 1)
                    + "|38=" + (100 * (1 + (int) (random * 20))) + "|40=2|44=" + String.format("%.2f", 50 + random * 100);
            LogType orderSide = weSendOrders ? LogType.OUT : LogType.IN;
            LogType reportSide = weSendOrders ? LogType.IN : LogType.OUT;
            entries.add(entry(session, t.plusMillis(2), reportSide, fix(parties, reportSide, second, t.plusMillis(2),
                    "35=8|37=EX-" + (second % 1_000_000) + "|17=FILL-" + second + "|150=2|39=2|" + order + "|14=100|151=0")));
            entries.add(entry(session, t, orderSide, fix(parties, orderSide, second, t, "35=D|" + order)));
        }
        if (second % 30 == 0) {
            entries.add(entry(session, t, LogType.IN, fix(parties, LogType.IN, second, t, "35=0")));
            entries.add(entry(session, t, LogType.OUT, fix(parties, LogType.OUT, second, t, "35=0")));
        }
        return entries;
    }

    private static String fix(String[] parties, LogType direction, long second, Instant sendingTime, String body) {
        String sender = direction == LogType.OUT ? parties[0] : parties[1];
        String target = direction == LogType.OUT ? parties[1] : parties[0];
        String fields = body.substring(0, body.indexOf('|') < 0 ? body.length() : body.indexOf('|'))
                + "|34=" + (second % 100_000) + "|49=" + sender + "|56=" + target + "|52=" + SENDING_TIME.format(sendingTime)
                + (body.indexOf('|') < 0 ? "" : body.substring(body.indexOf('|')));
        return "8=FIX.4.4|9=" + fields.length() + "|" + fields + "|10=" + String.format("%03d", fields.length() % 256) + "|";
    }

    private static LogEntry entry(SessionSummary session, Instant t, LogType type, String line) {
        return LogEntry.builder().timestamp(t).sessionId(session.getId()).type(type).line(line).build();
    }
}
