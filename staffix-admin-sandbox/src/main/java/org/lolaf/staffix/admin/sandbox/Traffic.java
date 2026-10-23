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
package org.lolaf.staffix.admin.sandbox;

import lombok.Setter;
import org.lolaf.staffix.admin.sandbox.fix44.encoders.ExecutionReportEncoder;
import org.lolaf.staffix.admin.sandbox.fix44.encoders.NewOrderSingleEncoder;
import org.lolaf.staffix.admin.sandbox.fix44.encoders.NewsEncoder;
import org.lolaf.staffix.admin.sandbox.fix44.encoders.QuoteEncoder;
import org.lolaf.staffix.admin.sandbox.fix44.encoders.QuoteRequestEncoder;
import org.lolaf.staffix.admin.sandbox.fix44.fields.ClOrdID;
import org.lolaf.staffix.admin.sandbox.fix44.fields.ExecType;
import org.lolaf.staffix.admin.sandbox.fix44.fields.NoRelatedSym;
import org.lolaf.staffix.admin.sandbox.fix44.fields.OfferPx;
import org.lolaf.staffix.admin.sandbox.fix44.fields.OrdStatus;
import org.lolaf.staffix.admin.sandbox.fix44.fields.OrdType;
import org.lolaf.staffix.admin.sandbox.fix44.fields.OrderQty;
import org.lolaf.staffix.admin.sandbox.fix44.fields.Price;
import org.lolaf.staffix.admin.sandbox.fix44.fields.QuoteID;
import org.lolaf.staffix.admin.sandbox.fix44.fields.QuoteReqID;
import org.lolaf.staffix.admin.sandbox.fix44.fields.Side;
import org.lolaf.staffix.admin.sandbox.fix44.fields.Symbol;
import org.lolaf.staffix.admin.sandbox.fix44.msg.MessageTypes;
import org.lolaf.staffix.api.FixDictionaryId;
import org.lolaf.staffix.api.alerting.Alert;
import org.lolaf.staffix.api.alerting.Probe;
import org.lolaf.staffix.api.alerting.ProbeEvaluator;
import org.lolaf.staffix.api.application.FixApplication;
import org.lolaf.staffix.api.codec.FixFieldsDecoderMapper;
import org.lolaf.staffix.api.codec.FixMessageDecoder;
import org.lolaf.staffix.api.fields.FieldsRegistry;
import org.lolaf.staffix.api.monitoring.Gauge;
import org.lolaf.staffix.api.monitoring.Timer;
import org.lolaf.staffix.api.msg.DecodedFixMessage;
import org.lolaf.staffix.api.msg.MessageType;
import org.lolaf.staffix.api.msg.MessageTypeRegistry;
import org.lolaf.staffix.api.session.FixSession;
import org.lolaf.staffix.api.session.FixSessionSettings;
import org.lolaf.staffix.api.time.UTCTime;
import org.lolaf.staffix.api.version.FixApiVersion;
import org.lolaf.staffix.api.version.FixRegularVersion;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
    private static final int QUOTES_PER_ORDER = 5;

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
     * Asks for a quote on a random symbol every interval, and buys at the offer of one quote in {@value QUOTES_PER_ORDER}
     * with a NewOrderSingle naming the quote. Goes quiet for {@link #PAUSE} of every {@link #PAUSE_CYCLE}, shifted per
     * session, so the quoter's order-flow probe raises its alerts and clears them.
     */
    static final class Requester extends Periodic {

        static final Duration PAUSE_CYCLE = Duration.ofMinutes(4);
        static final Duration PAUSE = Duration.ofSeconds(70);

        private final AtomicLong requests = new AtomicLong();
        private final AtomicLong quotes = new AtomicLong();
        private final QuoteRoundTrips roundTrips = new QuoteRoundTrips();
        private QuoteRequestEncoder quoteRequest;
        private NewOrderSingleEncoder order;

        Requester(ScheduledExecutorService scheduler, Duration interval) {
            super(scheduler, interval, FixRegularVersion.VERSION_44);
        }

        @Override
        public List<FixMessageDecoder> setup(FixSessionSettings settings, FixSession session, Set<MessageType> encodedMessagesTypes) {
            encodedMessagesTypes.add(MessageTypes.QuoteRequest);
            encodedMessagesTypes.add(MessageTypes.NewOrderSingle);
            quoteRequest = session.newEncoder(QuoteRequestEncoder.class).asReusable();
            order = session.newEncoder(NewOrderSingleEncoder.class).asReusable();
            return List.of(new QuoteDecoder(this), new Ignoring(MessageTypes.ExecutionReport));
        }

        @Override
        public synchronized void onSessionCreated(FixSession session, FieldsRegistry fieldsRegistry,
                                                  MessageTypeRegistry messageTypeRegistry, List<FixMessageDecoder> decoders) {
            roundTrips.onSessionCreated(session);
            super.onSessionCreated(session, fieldsRegistry, messageTypeRegistry, decoders);
        }

        @Override
        void send(FixSession session) {
            if (isPaused(session)) {
                return;
            }
            String quoteReqId = session.getFixSessionId().getGroup() + "-" + requests.incrementAndGet();
            quoteRequest.begin()
                    .setQuoteReqID(quoteReqId)
                    .addNoRelatedSym(1)
                    .setSymbol(SYMBOLS.get(ThreadLocalRandom.current().nextInt(SYMBOLS.size())));
            roundTrips.sent(quoteReqId);
            session.send(quoteRequest, null);
        }

        private static boolean isPaused(FixSession session) {
            long cycle = PAUSE_CYCLE.toSeconds();
            long shift = Math.floorMod(session.getFixSessionId().getQualifiedName().hashCode(), cycle);
            return Math.floorMod(System.currentTimeMillis() / 1000 + shift, cycle) >= cycle - PAUSE.toSeconds();
        }

        private void quoted(FixSession session, QuoteDecoder quote) {
            roundTrips.quoted(quote.quoteReqId);
            if (quotes.incrementAndGet() % QUOTES_PER_ORDER != 0) {
                return;
            }
            session.send(order.begin()
                    .setClOrdID(quote.quoteReqId + "-o")
                    .setQuoteID(quote.quoteId)
                    .setSymbol(quote.symbol)
                    .setSide(Side.SideValues.BUY)
                    .setTransactTime(session.getClock().now(), TimeUnit.MILLISECONDS)
                    .setOrderQty(100 * ThreadLocalRandom.current().nextInt(1, 21))
                    .setOrdType(OrdType.OrdTypeValues.LIMIT)
                    .setPrice(quote.offerPx), null);
        }

        @Setter
        private static final class QuoteDecoder implements FixMessageDecoder {

            private final Requester requester;
            private String quoteReqId;
            private String quoteId;
            private String symbol;
            private double offerPx;

            QuoteDecoder(Requester requester) {
                this.requester = requester;
            }

            @Override
            public MessageType getMessageType() {
                return MessageTypes.Quote;
            }

            @Override
            public void mapFieldsForDecoding(FixFieldsDecoderMapper mapper, FieldsRegistry fieldsRegistry) {
                mapper.mapStringField(QuoteReqID.get(), this::setQuoteReqId, null)
                        .mapStringField(QuoteID.get(), this::setQuoteId, null)
                        .mapStringField(Symbol.get(), this::setSymbol, null)
                        .mapDoubleField(OfferPx.get(), this::setOfferPx, 0);
            }

            @Override
            public void onDecoded(FixSession session, boolean possDupFlag, boolean possResend) {
                requester.quoted(session, this);
            }
        }
    }

    /**
     * Publishes a headline every interval.
     */
    static class Publisher extends Periodic {

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
     * A drop copy session: publishes headlines, and copies the fills of the venue's trading sessions.
     */
    static final class DropCopy extends Publisher {

        private ExecutionReportEncoder copy;
        private volatile FixSession session;

        DropCopy(ScheduledExecutorService scheduler, Duration interval) {
            super(scheduler, interval);
        }

        @Override
        public List<FixMessageDecoder> setup(FixSessionSettings settings, FixSession session, Set<MessageType> encodedMessagesTypes) {
            encodedMessagesTypes.add(MessageTypes.ExecutionReport);
            copy = session.newEncoder(ExecutionReportEncoder.class).asReusable();
            return super.setup(settings, session, encodedMessagesTypes);
        }

        @Override
        public synchronized void onSessionCreated(FixSession session, FieldsRegistry fieldsRegistry,
                                                  MessageTypeRegistry messageTypeRegistry, List<FixMessageDecoder> decoders) {
            this.session = session;
            super.onSessionCreated(session, fieldsRegistry, messageTypeRegistry, decoders);
        }

        /**
         * Sends on the drop copy session's own thread, as its encoder is not shared; a fill made while it is logged
         * out is not copied.
         */
        void copy(Fill fill) {
            FixSession dropCopy = session;
            if (dropCopy != null && dropCopy.isLoggedIn()) {
                dropCopy.processTask(() -> dropCopy.send(fill.encode(copy.begin(), dropCopy.getClock().now()), null));
            }
        }
    }

    record Fill(String clOrdId, String orderId, String symbol, Side.SideValues side, double quantity, double price) {

        ExecutionReportEncoder encode(ExecutionReportEncoder report, UTCTime now) {
            return report
                    .setOrderID(orderId)
                    .setClOrdID(clOrdId)
                    .setExecID(orderId + "-f1")
                    .setExecType(ExecType.ExecTypeValues.TRADE)
                    .setOrdStatus(OrdStatus.OrdStatusValues.FILLED)
                    .setSymbol(symbol)
                    .setSide(side)
                    .setOrderQty(quantity)
                    .setPrice(price)
                    .setLastQty(quantity)
                    .setLastPx(price)
                    .setLeavesQty(0)
                    .setCumQty(quantity)
                    .setAvgPx(price)
                    .setTransactTime(now, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Answers every QuoteRequest with a Quote around a random mid, and fills every NewOrderSingle at its price, copying
     * the fill to the drop copy when there is one.
     */
    static final class Quoter implements FixApplication {

        static final String ORDER_FLOW_PROBE = "order-flow";

        private final DropCopy dropCopy;
        private final OrderFlow orderFlow = new OrderFlow(Duration.ofSeconds(20), Duration.ofSeconds(45));

        Quoter() {
            this(null);
        }

        Quoter(DropCopy dropCopy) {
            this.dropCopy = dropCopy;
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
            encodedMessagesTypes.add(MessageTypes.Quote);
            encodedMessagesTypes.add(MessageTypes.ExecutionReport);
            return List.of(new QuoteRequestDecoder(session), new OrderDecoder(session, dropCopy, orderFlow));
        }

        /**
         * Added once: staffix suspends it while the session is not logged in, since a session nobody can send on is not
         * short of orders, and resets it at logon, so the silence restarts from there.
         */
        @Override
        public void onSessionCreated(FixSession session, FieldsRegistry fieldsRegistry, MessageTypeRegistry messageTypeRegistry,
                                     List<FixMessageDecoder> decoders) {
            session.getProbes().add(Probe.builder()
                    .name(ORDER_FLOW_PROBE)
                    .description("Time since the last NewOrderSingle")
                    .evaluator(orderFlow)
                    .reminderInterval(Duration.ofMinutes(1))
                    .build());
        }

        /**
         * Evaluated on the engine's probes thread, hence the volatile; the alerts are built once, so an evaluation
         * allocates nothing.
         */
        private static final class OrderFlow implements ProbeEvaluator {

            private final long warningAfterNanos;
            private final long majorAfterNanos;
            private final Optional<Alert> warning;
            private final Optional<Alert> major;
            private volatile long lastOrderNanos = System.nanoTime();

            OrderFlow(Duration warningAfter, Duration majorAfter) {
                warningAfterNanos = warningAfter.toNanos();
                majorAfterNanos = majorAfter.toNanos();
                warning = Optional.of(Alert.warning("no order for more than " + warningAfter.toSeconds() + "s"));
                major = Optional.of(Alert.major("no order for more than " + majorAfter.toSeconds() + "s"));
            }

            void onOrder() {
                lastOrderNanos = System.nanoTime();
            }

            @Override
            public void reset() {
                lastOrderNanos = System.nanoTime();
            }

            @Override
            public Optional<Alert> evaluate() {
                long silence = System.nanoTime() - lastOrderNanos;
                return silence >= majorAfterNanos ? major : silence >= warningAfterNanos ? warning : Optional.empty();
            }
        }

        @Setter
        private static final class OrderDecoder implements FixMessageDecoder {

            private final ExecutionReportEncoder report;
            private final DropCopy dropCopy;
            private final OrderFlow orderFlow;
            private final AtomicLong orders = new AtomicLong();
            private String clOrdId;
            private String symbol;
            private Side.SideValues side;
            private double orderQty;
            private double price;

            OrderDecoder(FixSession session, DropCopy dropCopy, OrderFlow orderFlow) {
                report = session.newEncoder(ExecutionReportEncoder.class).asReusable();
                this.dropCopy = dropCopy;
                this.orderFlow = orderFlow;
            }

            @Override
            public MessageType getMessageType() {
                return MessageTypes.NewOrderSingle;
            }

            @Override
            public void mapFieldsForDecoding(FixFieldsDecoderMapper mapper, FieldsRegistry fieldsRegistry) {
                mapper.mapStringField(ClOrdID.get(), this::setClOrdId, null)
                        .mapStringField(Symbol.get(), this::setSymbol, null)
                        .mapCharValuesEnumField(Side.get(), this::setSide, null)
                        .mapDoubleField(OrderQty.get(), this::setOrderQty, 0)
                        .mapDoubleField(Price.get(), this::setPrice, 0);
            }

            @Override
            public void onDecoded(FixSession session, boolean possDupFlag, boolean possResend) {
                orderFlow.onOrder();
                String orderId = session.getFixSessionId().getSenderCompID().getValue() + "-" + orders.incrementAndGet();
                Fill fill = new Fill(clOrdId, orderId, symbol, side, orderQty, price);
                session.send(fill.encode(report.begin(), session.getClock().now()), null);
                if (dropCopy != null) {
                    dropCopy.copy(fill);
                }
            }
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
                double mid = 100 + ThreadLocalRandom.current().nextDouble(10);
                session.send(quote.begin()
                        .setQuoteReqID(quoteReqId)
                        .setQuoteID(quoteReqId + "-q")
                        .setSymbol(symbol)
                        .setBidPx(Math.round((mid - 0.25) * 100) / 100d)
                        .setOfferPx(Math.round((mid + 0.25) * 100) / 100d), null);
                pricing.stop();
                lastMid.set(mid);
            }
        }
    }

    /**
     * Takes in what its counterparty publishes and does nothing with it.
     */
    static final class Listener implements FixApplication {

        private final List<MessageType> messageTypes;

        Listener(MessageType... messageTypes) {
            this.messageTypes = List.of(messageTypes);
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
            return messageTypes.stream().<FixMessageDecoder>map(Ignoring::new).toList();
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
