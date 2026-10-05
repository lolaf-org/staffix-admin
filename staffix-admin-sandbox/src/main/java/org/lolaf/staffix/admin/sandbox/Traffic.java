package org.lolaf.staffix.admin.sandbox;

import lombok.Setter;
import org.lolaf.staffix.admin.sandbox.fix44.encoders.NewsEncoder;
import org.lolaf.staffix.admin.sandbox.fix44.encoders.QuoteEncoder;
import org.lolaf.staffix.admin.sandbox.fix44.encoders.QuoteRequestEncoder;
import org.lolaf.staffix.admin.sandbox.fix44.fields.NoRelatedSym;
import org.lolaf.staffix.admin.sandbox.fix44.fields.QuoteReqID;
import org.lolaf.staffix.admin.sandbox.fix44.fields.Symbol;
import org.lolaf.staffix.admin.sandbox.fix44.msg.MessageTypes;
import org.lolaf.staffix.api.FixDictionaryId;
import org.lolaf.staffix.api.application.FixApplication;
import org.lolaf.staffix.api.codec.FixFieldsDecoderMapper;
import org.lolaf.staffix.api.codec.FixMessageDecoder;
import org.lolaf.staffix.api.fields.FieldsRegistry;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The applications the sandbox sessions run, so that metrics and logs have something to show.
 */
final class Traffic {

    private static final List<String> SYMBOLS = List.of("EUR/USD", "GBP/USD", "USD/JPY", "USD/CHF", "AUD/USD");

    private Traffic() {
    }

    /**
     * Base for an application that sends on its own every interval while logged in, on the session's thread.
     */
    abstract static class Periodic implements FixApplication {

        private final ScheduledExecutorService scheduler;
        private final Duration interval;
        private final FixRegularVersion version;
        private ScheduledFuture<?> sending;

        Periodic(ScheduledExecutorService scheduler, Duration interval, FixRegularVersion version) {
            this.scheduler = scheduler;
            this.interval = interval;
            this.version = version;
        }

        @Override
        public FixApiVersion getFixApiVersion() {
            return FixApiVersion.of(version);
        }

        @Override
        public FixDictionaryId getDictionaryId() {
            return FixDictionaryId.of(version);
        }

        @Override
        public synchronized void onSessionCreated(FixSession session, FieldsRegistry fieldsRegistry,
                                                  MessageTypeRegistry messageTypeRegistry, List<FixMessageDecoder> decoders) {
            sending = scheduler.scheduleAtFixedRate(() -> {
                if (session.isLoggedIn()) {
                    session.processTask(() -> send(session));
                }
            }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public synchronized void onSessionDestroyed(FixSession session) {
            if (sending != null) {
                sending.cancel(false);
            }
        }

        abstract void send(FixSession session);
    }

    /**
     * Asks for a quote on a random symbol every interval.
     */
    static final class Requester extends Periodic {

        private final AtomicLong requests = new AtomicLong();
        private final QuoteRoundTrips roundTrips = new QuoteRoundTrips();
        private QuoteRequestEncoder quoteRequest;

        Requester(ScheduledExecutorService scheduler, Duration interval) {
            super(scheduler, interval, FixRegularVersion.VERSION_44);
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

    /**
     * Publishes a headline every interval.
     */
    static final class Publisher extends Periodic {

        private NewsEncoder news;

        Publisher(ScheduledExecutorService scheduler, Duration interval) {
            super(scheduler, interval, FixRegularVersion.VERSION_44);
        }

        @Override
        public List<FixMessageDecoder> setup(FixSessionSettings settings, FixSession session, Set<MessageType> encodedMessagesTypes) {
            encodedMessagesTypes.add(MessageTypes.News);
            news = session.newEncoder(NewsEncoder.class).asReusable();
            return List.of();
        }

        @Override
        void send(FixSession session) {
            news.begin()
                    .setHeadline(SYMBOLS.get(ThreadLocalRandom.current().nextInt(SYMBOLS.size())) + " moves")
                    .addNoLinesOfText(1)
                    .setText("Published by " + session.getFixSessionId().getQualifiedName());
            session.send(news, null);
        }
    }

    /**
     * Answers every QuoteRequest with a Quote around a random mid.
     */
    static final class Quoter implements FixApplication {

        @Override
        public FixApiVersion getFixApiVersion() {
            return FixApiVersion.of(FixRegularVersion.VERSION_44);
        }

        @Override
        public FixDictionaryId getDictionaryId() {
            return FixDictionaryId.of(FixRegularVersion.VERSION_44);
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
                }
                pricing.start();
                double mid = 100 + ThreadLocalRandom.current().nextDouble(10);
                session.send(quote.begin()
                        .setQuoteReqID(quoteReqId)
                        .setQuoteID(quoteReqId + "-q")
                        .setSymbol(symbol)
                        .setBidPx(Math.round((mid - 0.25) * 100) / 100d)
                        .setOfferPx(Math.round((mid + 0.25) * 100) / 100d), null);
                pricing.stop();
            }
        }
    }

    /**
     * Takes in what its counterparty publishes and does nothing with it.
     */
    static final class Listener implements FixApplication {

        private final MessageType messageType;

        Listener(MessageType messageType) {
            this.messageType = messageType;
        }

        @Override
        public FixApiVersion getFixApiVersion() {
            return FixApiVersion.of(FixRegularVersion.VERSION_44);
        }

        @Override
        public FixDictionaryId getDictionaryId() {
            return FixDictionaryId.of(FixRegularVersion.VERSION_44);
        }

        @Override
        public List<FixMessageDecoder> setup(FixSessionSettings settings, FixSession session, Set<MessageType> encodedMessagesTypes) {
            return List.of(new Ignoring(messageType));
        }
    }

    private static final class Ignoring implements FixMessageDecoder {

        private final MessageType messageType;

        Ignoring(MessageType messageType) {
            this.messageType = messageType;
        }

        @Override
        public MessageType getMessageType() {
            return messageType;
        }

        @Override
        public void mapFieldsForDecoding(FixFieldsDecoderMapper mapper, FieldsRegistry fieldsRegistry) {
        }

        @Override
        public void onDecoded(FixSession session, boolean possDupFlag, boolean possResend) {
        }
    }
}
