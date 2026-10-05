package org.lolaf.staffix.admin.sandbox;

import lombok.Setter;
import org.lolaf.staffix.admin.sandbox.fix50sp2.encoders.QuoteEncoder;
import org.lolaf.staffix.admin.sandbox.fix50sp2.encoders.QuoteRequestEncoder;
import org.lolaf.staffix.admin.sandbox.fix50sp2.fields.NoRelatedSym;
import org.lolaf.staffix.admin.sandbox.fix50sp2.fields.QuoteReqID;
import org.lolaf.staffix.admin.sandbox.fix50sp2.fields.Symbol;
import org.lolaf.staffix.admin.sandbox.fix50sp2.msg.MessageTypes;
import org.lolaf.staffix.api.FixDictionaryId;
import org.lolaf.staffix.api.application.FixApplication;
import org.lolaf.staffix.api.codec.FixFieldsDecoderMapper;
import org.lolaf.staffix.api.codec.FixMessageDecoder;
import org.lolaf.staffix.api.fields.FieldsRegistry;
import org.lolaf.staffix.api.monitoring.Gauge;
import org.lolaf.staffix.api.monitoring.Timer;
import org.lolaf.staffix.api.msg.MessageType;
import org.lolaf.staffix.api.msg.MessageTypeRegistry;
import org.lolaf.staffix.api.session.FixSession;
import org.lolaf.staffix.api.session.FixSessionSettings;
import org.lolaf.staffix.api.version.FixApiVersion;
import org.lolaf.staffix.api.version.FixRegularVersion;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The quote traffic of {@link Traffic} over FIXT.1.1, its application messages in FIX 5.0 SP2.
 */
final class FixtTraffic {

    private static final List<String> SYMBOLS = List.of("EUR/USD", "GBP/USD", "USD/JPY");

    private FixtTraffic() {
    }

    static final class Requester extends Traffic.Periodic {

        private final AtomicLong requests = new AtomicLong();
        private final QuoteRoundTrips roundTrips = new QuoteRoundTrips();
        private QuoteRequestEncoder quoteRequest;

        Requester(ScheduledExecutorService scheduler, Duration interval) {
            super(scheduler, interval, FixRegularVersion.VERSION_50_SP2);
        }

        @Override
        public List<FixMessageDecoder> setup(FixSessionSettings settings, FixSession session, Set<MessageType> encodedMessagesTypes) {
            encodedMessagesTypes.add(MessageTypes.QuoteRequest);
            quoteRequest = session.newEncoder(QuoteRequestEncoder.class).asReusable();
            return List.of(new QuoteDecoder(roundTrips));
        }

        @Override
        public synchronized void onSessionCreated(FixSession session, FieldsRegistry fieldsRegistry,
                                                  MessageTypeRegistry messageTypeRegistry, List<FixMessageDecoder> decoders) {
            roundTrips.onSessionCreated(session);
            super.onSessionCreated(session, fieldsRegistry, messageTypeRegistry, decoders);
        }

        @Override
        void send(FixSession session) {
            String quoteReqId = session.getFixSessionId().getName() + "-" + requests.incrementAndGet();
            quoteRequest.begin()
                    .setQuoteReqID(quoteReqId)
                    .addNoRelatedSym(1)
                    .setSymbol(SYMBOLS.get(ThreadLocalRandom.current().nextInt(SYMBOLS.size())));
            roundTrips.sent(quoteReqId);
            session.send(quoteRequest, null);
        }

        @Setter
        private static final class QuoteDecoder implements FixMessageDecoder {

            private final QuoteRoundTrips roundTrips;
            private String quoteReqId;

            QuoteDecoder(QuoteRoundTrips roundTrips) {
                this.roundTrips = roundTrips;
            }

            @Override
            public MessageType getMessageType() {
                return MessageTypes.Quote;
            }

            @Override
            public void mapFieldsForDecoding(FixFieldsDecoderMapper mapper, FieldsRegistry fieldsRegistry) {
                mapper.mapStringField(QuoteReqID.get(), this::setQuoteReqId, null);
            }

            @Override
            public void onDecoded(FixSession session, boolean possDupFlag, boolean possResend) {
                roundTrips.quoted(quoteReqId);
            }
        }
    }

    static final class Quoter implements FixApplication {

        @Override
        public FixApiVersion getFixApiVersion() {
            return FixApiVersion.of(FixRegularVersion.VERSION_50_SP2);
        }

        @Override
        public FixDictionaryId getDictionaryId() {
            return FixDictionaryId.of(FixRegularVersion.VERSION_50_SP2);
        }

        @Override
        public List<FixMessageDecoder> setup(FixSessionSettings settings, FixSession session, Set<MessageType> encodedMessagesTypes) {
            encodedMessagesTypes.add(MessageTypes.Quote);
            return List.of(new QuoteRequestDecoder(session));
        }

        @Setter
        private static final class QuoteRequestDecoder implements FixMessageDecoder {

            private final QuoteEncoder quote;
            private Timer pricing;
            private Gauge lastMid;
            private String quoteReqId;
            private String symbol;

            QuoteRequestDecoder(FixSession session) {
                quote = session.newEncoder(QuoteEncoder.class).asReusable();
            }

            @Override
            public MessageType getMessageType() {
                return MessageTypes.QuoteRequest;
            }

            @Override
            public void mapFieldsForDecoding(FixFieldsDecoderMapper mapper, FieldsRegistry fieldsRegistry) {
                mapper.mapStringField(QuoteReqID.get(), this::setQuoteReqId, null)
                        .forGroup(NoRelatedSym.get())
                        .withFieldsAutoResetDisabled()
                        .mapStringField(Symbol.get(), this::setSymbol, null);
            }

            @Override
            public void onDecoded(FixSession session, boolean possDupFlag, boolean possResend) {
                if (pricing == null) {
                    pricing = QuoteRoundTrips.pricing(session);
                    lastMid = QuoteRoundTrips.lastMid(session);
                }
                pricing.start();
                double mid = 1 + ThreadLocalRandom.current().nextDouble();
                session.send(quote.begin()
                        .setQuoteReqID(quoteReqId)
                        .setQuoteID(quoteReqId + "-q")
                        .setSymbol(symbol)
                        .setBidPx(Math.round((mid - 0.0005) * 10_000) / 10_000d)
                        .setOfferPx(Math.round((mid + 0.0005) * 10_000) / 10_000d), null);
                pricing.stop();
                lastMid.set(mid);
            }
        }
    }
}
