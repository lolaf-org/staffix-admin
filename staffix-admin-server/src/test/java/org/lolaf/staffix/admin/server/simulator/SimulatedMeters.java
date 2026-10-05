package org.lolaf.staffix.admin.server.simulator;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.metrics.Meter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The meters a monitoring plugin of {@link SimulatedEmitters} publishes: a client one measures less, and the others
 * carry the application's custom pricing timer.
 */
@UtilityClass
class SimulatedMeters {

    static final String CUSTOM_TIMER = "orders.pricing";
    static final String CUSTOM_TAG = "venue";

    List<Meter> of(@Nullable String monitoring) {
        if (monitoring == null) {
            return List.of();
        }
        boolean clients = monitoring.startsWith("clients");
        List<Meter> meters = new ArrayList<>();
        meters.add(messages("messages.read.latency", "Time to fully process a received message."));
        meters.add(messages("messages.write.latency", "Time to send an encoded message."));
        if (!clients) {
            meters.add(messages("messages.decoding.latency", "Time to decode a received message."));
            meters.add(messages("messages.encoding.latency", "Time to encode a sent message."));
        }
        meters.add(Meter.builder().name("session.rtt").type(Meter.Type.TIMER).description("Round-trip time to the counterparty.").build());
        if (!clients) {
            meters.add(Meter.builder().name("session.clock.offset").type(Meter.Type.GAUGE).unit(TimeUnit.NANOSECONDS)
                    .description("The counterparty's clock minus this engine's.").build());
        }
        meters.add(Meter.builder().name("session.logon.state").type(Meter.Type.GAUGE).description("1 while logged on.").build());
        if (!clients) {
            meters.add(Meter.builder().name(CUSTOM_TIMER).type(Meter.Type.TIMER).description("Pricing an order.")
                    .tagKey(CUSTOM_TAG).percentiles(true).custom(true).build());
        }
        return meters;
    }

    private static Meter messages(String name, String description) {
        return Meter.builder().name(name).type(Meter.Type.TIMER).description(description)
                .tagKey("fix.msg.type").tagKey("fix.msg.dir").percentiles(true).build();
    }
}
