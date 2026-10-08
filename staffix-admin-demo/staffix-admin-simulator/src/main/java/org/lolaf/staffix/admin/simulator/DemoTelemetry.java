package org.lolaf.staffix.admin.simulator;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.api.session.FixSession.FixSessionType;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Metrics and FIX logs for the running configs, shaped as the console's metrics and logs SPIs return them. Values
 * derive from the instant and the session only, so a chart or a log search shows the same data on each refresh.
 */
@UtilityClass
class DemoTelemetry {

    private static final String MESSAGE_TYPE = "fix.msg.type";
    private static final String DIRECTION = "fix.msg.dir";
    private static final String GROUP = "fix.sg";
    private static final String SESSION = "fix.sn";
    private static final Map<String, Double> MESSAGES_PER_SECOND = Map.of("D", 12.0, "8", 18.0, "0", 1 / 30.0);
    private static final Map<String, Double> MEDIAN_MICROS = Map.of(
            "messages.read.latency", 14.0,
            "messages.write.latency", 8.0,
            "messages.decoding.latency", 3.0,
            "messages.encoding.latency", 2.0,
            "session.rtt", 450.0);
    private static final List<String> DIRECTIONS = List.of("in", "out");
    private static final double MICROS = 1e-6;
    private static final DateTimeFormatter SENDING_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private static final List<String> SYMBOLS = List.of("AAPL", "MSFT", "AIRP.PA", "VOD.L", "EURUSD", "ESZ6");
    private static final List<String> EVENTS = List.of(
            "Logon received, session established",
            "Heartbeat timeout, test request sent",
            "Resend request received for 120 to 0",
            "Disconnected: connection reset by peer",
            "Logout received: end of session time");
    private static final long MAX_SECONDS_SCANNED = 200_000;
    private static final int MAX_NOISE_SAMPLES_PER_WINDOW = 12;

    /**
     * {@code rateWindow} in seconds, null when the kind is not a rate.
     */
    /**
     * {@code rateWindow} in seconds, null when the statistic is not a rate.
     */
    record Series(Map<String, String> labels, List<Point> points, @Nullable Long rateWindow) {
    }

    record Point(Instant timestamp, double value) {
    }

    record Entry(Instant timestamp, String group, String sessionId, String type, String line) {
    }

    /**
     * One series per combination of the split tags' values the filters keep; sessions merged into one series add up
     * their rates and keep the highest of anything else. A rate's noise is averaged over {@code rateWindow} (the step
     * when null), so a narrow window shows a noisier chart as a real backend would.
     */
    List<Series> metrics(List<SimulatedSession> sessions, String meter, String statistic, @Nullable Double quantile,
                         List<String> splitBy, Map<String, String> filters, Instant from, Instant to, Duration step,
                         @Nullable Duration rateWindow) {
        Duration window = statistic.equals("VALUE") ? null : rateWindow == null ? step : rateWindow;
        boolean perSession = splitBy.contains(GROUP) || splitBy.contains(SESSION);
        List<Map<String, String>> combinations = new ArrayList<>(List.of(Map.of()));
        for (String tag : splitBy) {
            if (tag.equals(GROUP) || tag.equals(SESSION)) {
                continue;
            }
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> combination : combinations) {
                for (String value : valuesOf(tag, filters)) {
                    Map<String, String> extended = new LinkedHashMap<>(combination);
                    extended.put(tag, value);
                    next.add(extended);
                }
            }
            combinations = next;
        }
        List<Series> series = new ArrayList<>();
        for (Map<String, String> combination : combinations) {
            String messageType = combination.get(MESSAGE_TYPE);
            String suffix = meter + combination + filters;
            if (perSession) {
                sessions.forEach(session -> {
                    Map<String, String> labels = new LinkedHashMap<>(combination);
                    if (splitBy.contains(GROUP)) {
                        labels.put(GROUP, session.group());
                    }
                    if (splitBy.contains(SESSION)) {
                        labels.put(SESSION, session.name());
                    }
                    String key = key(session) + suffix;
                    series.add(series(labels, from, to, step, window, t -> value(meter, statistic, quantile, window, key, messageType, t)));
                });
            } else {
                List<String> keys = sessions.stream().map(session -> key(session) + suffix).toList();
                series.add(series(combination, from, to, step, window, t -> keys.stream()
                        .mapToDouble(key -> value(meter, statistic, quantile, window, key, messageType, t))
                        .reduce(0, statistic.equals("RATE") ? Double::sum : Math::max)));
            }
        }
        return sessions.isEmpty() ? List.of() : series;
    }

    private static List<String> valuesOf(String tag, Map<String, String> filters) {
        if (filters.containsKey(tag)) {
            return List.of(filters.get(tag));
        }
        return switch (tag) {
            case MESSAGE_TYPE -> MESSAGES_PER_SECOND.keySet().stream().sorted().toList();
            case DIRECTION -> DIRECTIONS;
            case SimulatedMonitoring.VENUE -> SimulatedMonitoring.VENUES;
            default -> List.of("default");
        };
    }

    /**
     * Newest first, at most {@code limit}.
     */
    List<Entry> logs(List<SimulatedSession> sessions, Instant from, Instant to, @Nullable String text, Set<String> types,
                     Set<String> msgTypes, Set<String> fieldValues, int limit) {
        Pattern fieldValue = Pattern.compile("(^|\\|)[0-9]+=(" + fieldValues.stream().map(Pattern::quote).collect(Collectors.joining("|")) + ")(\\||$)");
        List<Entry> entries = new ArrayList<>();
        long earliest = Math.max(from.getEpochSecond(), to.getEpochSecond() - MAX_SECONDS_SCANNED / Math.max(sessions.size(), 1));
        for (long second = to.getEpochSecond(); second >= earliest && entries.size() < limit; second--) {
            long current = second;
            sessions.stream()
                    .flatMap(session -> secondOfTraffic(session, current).stream())
                    .filter(e -> types.isEmpty() || types.contains(e.type()))
                    .filter(e -> text == null || e.line().contains(text))
                    .filter(e -> msgTypes.isEmpty() || msgTypes.stream().anyMatch(t -> e.line().contains("|35=" + t + "|")))
                    .filter(e -> fieldValues.isEmpty() || fieldValue.matcher(e.line()).find())
                    .filter(e -> !e.timestamp().isAfter(to))
                    .sorted(Comparator.comparing(Entry::timestamp).reversed())
                    .limit(limit - entries.size())
                    .forEach(entries::add);
        }
        return entries;
    }

    private static Series series(Map<String, String> labels, Instant from, Instant to, Duration step, @Nullable Duration rateWindow,
                                 ToDoubleFunction<Instant> value) {
        List<Point> points = new ArrayList<>();
        long seconds = step.toSeconds();
        for (long second = from.getEpochSecond() / seconds * seconds; second <= to.getEpochSecond(); second += seconds) {
            Instant t = Instant.ofEpochSecond(second);
            points.add(new Point(t, value.applyAsDouble(t)));
        }
        return new Series(new LinkedHashMap<>(labels), points, rateWindow == null ? null : rateWindow.toSeconds());
    }

    private static double value(String meter, String statistic, @Nullable Double quantile, @Nullable Duration rateWindow, String key,
                                @Nullable String messageType, Instant t) {
        long second = t.getEpochSecond();
        double random = rateWindow == null ? noise(key, second) : averageNoise(key, second, rateWindow.toSeconds());
        double wave = 1 + 0.5 * Math.sin(2 * Math.PI * second / 7200.0 + key.hashCode());
        double median = MEDIAN_MICROS.getOrDefault(meter, 50.0 + Math.floorMod(meter.hashCode(), 450));
        return switch (statistic) {
            case "RATE" -> messageType != null ? MESSAGES_PER_SECOND.get(messageType) * wave * (0.8 + 0.4 * random)
                    : meter.equals("session.rtt") ? 1 / 30.0 : 5 * wave * (0.8 + 0.4 * random);
            case "QUANTILE" -> latency(median, quantile, random);
            case "MEAN" -> meter.equals("session.rtt") ? (350 + 150 * wave + 60 * random) * MICROS : 1.2 * latency(median, 0.5, random);
            case "VALUE" -> switch (meter) {
                case "session.clock.offset" -> (40 * Math.sin(2 * Math.PI * second / 3600.0 + key.hashCode()) + 8 * (random - 0.5)) * MICROS;
                case "session.logon.status" -> 1;
                default -> 0;
            };
            default -> throw new IllegalArgumentException("Unknown statistic " + statistic);
        };
    }

    private static double latency(double medianMicros, @Nullable Double quantile, double random) {
        double q = quantile == null ? 0.5 : quantile;
        double tail = 1 + 12 * Math.pow(Math.max(q - 0.5, 0) * 2, 3);
        double spike = random > 0.99 ? 3 : 1;
        return medianMicros * tail * spike * (0.85 + 0.3 * random) * MICROS;
    }

    private static List<Entry> secondOfTraffic(SimulatedSession session, long second) {
        double random = noise(key(session), second);
        List<Entry> entries = new ArrayList<>();
        Instant t = Instant.ofEpochSecond(second, (long) (random * 999_000_000));
        String[] parties = {session.fixSessionId().getSenderCompID().getValue(), session.fixSessionId().getTargetCompID().getValue()};
        boolean weSendOrders = session.settings.getFixSessionType() == FixSessionType.INITIATOR;
        if (random < 0.01) {
            entries.add(entry(session, t, "EVENT", EVENTS.get((int) (random * 100 * EVENTS.size()) % EVENTS.size())));
        }
        if (random > 0.6) {
            String symbol = SYMBOLS.get((int) (random * 1000) % SYMBOLS.size());
            int quantity = 100 * (1 + (int) (random * 20));
            String price = String.format(Locale.ROOT, "%.2f", 50 + random * 100);
            String order = "11=ORD-" + (second % 1_000_000) + "|55=" + symbol + "|54=" + (random > 0.8 ? 2 : 1)
                    + "|38=" + quantity + "|40=2|44=" + price;
            String orderSide = weSendOrders ? "OUT" : "IN";
            String reportSide = weSendOrders ? "IN" : "OUT";
            entries.add(entry(session, t.plusMillis(2), reportSide, fix(parties, reportSide, second, t.plusMillis(2),
                    "35=8|37=EX-" + (second % 1_000_000) + "|17=FILL-" + second + "|150=F|39=2|" + order
                            + "|32=" + quantity + "|31=" + price + "|151=0|14=" + quantity + "|6=" + price)));
            entries.add(entry(session, t, orderSide, fix(parties, orderSide, second, t, "35=D|" + order)));
        }
        if (second % 30 == 0) {
            entries.add(entry(session, t, "IN", fix(parties, "IN", second, t, "35=0")));
            entries.add(entry(session, t, "OUT", fix(parties, "OUT", second, t, "35=0")));
        }
        return entries;
    }

    private static String fix(String[] parties, String direction, long second, Instant sendingTime, String body) {
        String sender = direction.equals("OUT") ? parties[0] : parties[1];
        String target = direction.equals("OUT") ? parties[1] : parties[0];
        int firstField = body.indexOf('|');
        String fields = body.substring(0, firstField < 0 ? body.length() : firstField)
                + "|34=" + (second % 100_000) + "|49=" + sender + "|56=" + target + "|52=" + SENDING_TIME.format(sendingTime)
                + (firstField < 0 ? "" : body.substring(firstField));
        return "8=FIX.4.4|9=" + fields.length() + "|" + fields + "|10=" + String.format("%03d", fields.length() % 256) + "|";
    }

    private static Entry entry(SimulatedSession session, Instant t, String type, String line) {
        return new Entry(t, session.group(), session.name(), type, line);
    }

    private static double averageNoise(String key, long lastSecond, long windowSeconds) {
        int samples = (int) Math.max(1, Math.min(windowSeconds, MAX_NOISE_SAMPLES_PER_WINDOW));
        double sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += noise(key, lastSecond - i * windowSeconds / samples);
        }
        return sum / samples;
    }

    private static double noise(String key, long epochSecond) {
        return new SplittableRandom(key.hashCode() * 1_000_003L ^ epochSecond).nextDouble();
    }

    private static String key(SimulatedSession session) {
        return session.group() + "." + session.name();
    }
}
