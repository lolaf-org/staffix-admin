package org.lolaf.staffix.admin.server.simulator;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.server.sessions.SessionType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Each second of each running session, newest first: an order and its execution report, the initiator sending the
 * order, and every 30 seconds a heartbeat each way.
 */
@RequiredArgsConstructor
class SimulatedLogsSource implements LogsSource {

    private final SimulatedEngineGateway gateway;

    @Override
    public List<LogEntry> query(LogQuery query) {
        List<SimulatedSession> sessions = gateway.runningConfigs(query.getEngineId(), query.getGroup(), query.getSessionId());
        List<LogEntry> entries = new ArrayList<>();
        for (long second = query.getTo().getEpochSecond() - 1; second >= query.getFrom().getEpochSecond() && entries.size() < query.getLimit(); second--) {
            for (SimulatedSession session : sessions) {
                secondOfTraffic(session, second).stream()
                        .filter(e -> query.getTypes().isEmpty() || query.getTypes().contains(e.getType()))
                        .filter(e -> query.getText() == null || e.getLine().contains(query.getText()))
                        .filter(e -> query.getMsgTypes().isEmpty() || query.getMsgTypes().stream().anyMatch(t -> e.getLine().contains("|35=" + t + "|")))
                        .limit(query.getLimit() - entries.size())
                        .forEach(entries::add);
            }
        }
        return entries;
    }

    private static List<LogEntry> secondOfTraffic(SimulatedSession session, long second) {
        Instant t = Instant.ofEpochSecond(second);
        boolean weSendOrders = session.type == SessionType.INITIATOR;
        List<LogEntry> entries = new ArrayList<>();
        if (second % 30 == 0) {
            entries.add(entry(session, t, LogType.IN, second, "35=0"));
            entries.add(entry(session, t, LogType.OUT, second, "35=0"));
        }
        entries.add(entry(session, t, weSendOrders ? LogType.IN : LogType.OUT, second, "35=8|11=ORD-" + second + "|39=2"));
        entries.add(entry(session, t, weSendOrders ? LogType.OUT : LogType.IN, second, "35=D|11=ORD-" + second + "|55=AAPL"));
        return entries;
    }

    private static LogEntry entry(SimulatedSession session, Instant t, LogType type, long second, String body) {
        String sender = session.identity.getSender().getCompId();
        String target = session.identity.getTarget().getCompId();
        String line = "8=FIX.4.4|" + body + "|34=" + second % 100_000 + "|49=" + (type == LogType.OUT ? sender : target)
                + "|56=" + (type == LogType.OUT ? target : sender) + "|10=000|";
        return LogEntry.builder().timestamp(t).group(session.group).sessionId(session.id).type(type).line(line).build();
    }
}
