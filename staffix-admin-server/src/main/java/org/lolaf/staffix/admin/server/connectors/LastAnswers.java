package org.lolaf.staffix.admin.server.connectors;

import org.lolaf.staffix.admin.server.engines.GatewayException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Each engine's last answer to one call: reused while fresh, and kept when the engine stops answering, so telemetry
 * still resolves through an engine that is restarting.
 */
final class LastAnswers<T> {

    private final Duration fresh;
    private final Clock clock;
    private final Function<String, T> call;
    private final Map<String, Answer<T>> answers = new ConcurrentHashMap<>();

    LastAnswers(Duration fresh, Clock clock, Function<String, T> call) {
        this.fresh = fresh;
        this.clock = clock;
        this.call = call;
    }

    /**
     * Empty when the engine never answered.
     */
    Optional<T> of(String engineId) {
        Instant now = clock.instant();
        Answer<T> last = answers.get(engineId);
        if (last != null && now.isBefore(last.at().plus(fresh))) {
            return Optional.of(last.value());
        }
        try {
            T value = call.apply(engineId);
            answers.put(engineId, new Answer<>(now, value));
            return Optional.of(value);
        } catch (GatewayException unreachable) {
            return last == null ? Optional.empty() : Optional.of(last.value());
        }
    }

    private record Answer<T>(Instant at, T value) {
    }
}
