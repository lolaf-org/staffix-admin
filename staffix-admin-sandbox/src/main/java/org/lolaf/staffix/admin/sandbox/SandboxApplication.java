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

import lombok.extern.slf4j.Slf4j;
import org.lolaf.staffix.admin.sandbox.SandboxEngine.Session;
import org.lolaf.staffix.admin.sandbox.SandboxEngine.Target;
import org.lolaf.staffix.admin.sandbox.Traffic.DropCopy;
import org.lolaf.staffix.admin.sandbox.Traffic.Listener;
import org.lolaf.staffix.admin.sandbox.Traffic.Publisher;
import org.lolaf.staffix.admin.sandbox.Traffic.Quoter;
import org.lolaf.staffix.admin.sandbox.Traffic.Requester;
import org.lolaf.staffix.admin.sandbox.fix44.msg.MessageTypes;
import org.lolaf.staffix.alerting.alertmanager.AlertmanagerAlertsPublisherSettings;
import org.lolaf.staffix.stores.core.async.AsyncStoreSettings;
import org.lolaf.staffix.stores.messages.async.AsyncMessagesStoreSettings;
import org.lolaf.staffix.stores.messages.jdbc.JdbcMessageStoreSettings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

/**
 * Three engines trading with each other: a bank initiating to two venues, which quote it, fill the orders it places on
 * some quotes, publish news to it and copy venue-x's fills on a drop copy. venue-x stores its messages in Postgres,
 * venue-y through an async store in front of Postgres, and both post their alerts to Alertmanager. Between them every session state the console shows appears: logged in, a backup that cannot
 * connect, a session outside its window, and one whose counterparty never connects.
 */
@Slf4j
public final class SandboxApplication {

    private static final int ALPHA_BANK_PORT = 7101;
    private static final int VENUE_X_PORT = 7201;
    private static final int VENUE_X_DRP_PORT = 7299;
    private static final int VENUE_Y_PORT = 7301;

    private SandboxApplication() {
    }

    public static void main(String[] args) {
        SandboxSettings settings = SandboxSettings.fromSystemProperties();
        ScheduledExecutorService traffic = Executors.newScheduledThreadPool(2);
        Duration quoteInterval = Duration.ofMillis(200);
        Duration newsInterval = Duration.ofMillis(500);

        SandboxEngine alphaBank = new SandboxEngine("alpha-bank", settings)
                .initiator("venue-x-trading",
                        new Target(Session.of("venue-x", "trading", "ALPHA", "VENX", new Requester(traffic, quoteInterval)), VENUE_X_PORT),
                        new Target(Session.backup("venue-x", "trading-drp", "ALPHA", "VENX_DR"), VENUE_X_DRP_PORT))
                .initiator("venue-y-trading",
                        new Target(Session.of("venue-y", "trading", "ALPHA", "VENY", new Requester(traffic, quoteInterval)), VENUE_Y_PORT))
                .initiator("venue-y-fixt-trading",
                        new Target(Session.fixt("venue-y", "fixt-trading", "ALPHA", "VENY_T", new FixtTraffic.Requester(traffic, quoteInterval)), VENUE_Y_PORT))
                .initiator("venue-y-market-data",
                        new Target(Session.of("venue-y", "market-data", "ALPHA_MD", "VENY", new Listener(MessageTypes.News)), VENUE_Y_PORT))
                .acceptor("drop-copy-gateway", ALPHA_BANK_PORT,
                        Session.of("venue-x", "drop-copy", "ALPHA", "VENX_DC", new Listener(MessageTypes.News, MessageTypes.ExecutionReport)));

        DropCopy venueXDropCopy = new DropCopy(traffic, newsInterval);
        SandboxDatabase venueXDatabase = new SandboxDatabase(settings, "venue_x");
        SandboxDatabase venueYDatabase = new SandboxDatabase(settings, "venue_y");
        SandboxEngine venueX = new SandboxEngine("venue-x", settings)
                .messagesStore(jdbcStore(venueXDatabase))
                .alertsPublisher(alertmanager(settings))
                .acceptor("venue-x-gateway", VENUE_X_PORT,
                        Session.of("alpha-bank", "trading", "VENX", "ALPHA", new Quoter(venueXDropCopy)))
                .initiator("alpha-bank-drop-copy",
                        new Target(Session.of("alpha-bank", "drop-copy", "VENX_DC", "ALPHA", venueXDropCopy), ALPHA_BANK_PORT));

        SandboxEngine venueY = new SandboxEngine("venue-y", settings)
                .messagesStore(AsyncMessagesStoreSettings.builder()
                        .asyncStoreSettings(AsyncStoreSettings.builder().asyncQueueDirectory(emptyQueueDirectory("venue-y")).build())
                        .wrappedFixMessagesStoreSettings(jdbcStore(venueYDatabase))
                        .build())
                .alertsPublisher(alertmanager(settings))
                .acceptor("venue-y-gateway", VENUE_Y_PORT,
                        Session.of("alpha-bank", "trading", "VENY", "ALPHA", new Quoter()),
                        Session.fixt("alpha-bank", "fixt-trading", "VENY_T", "ALPHA", new FixtTraffic.Quoter()),
                        Session.of("alpha-bank", "market-data", "VENY", "ALPHA_MD", new Publisher(traffic, newsInterval)),
                        Session.of("alpha-bank", "end-of-day", "VENY", "ALPHA_EOD", new Quoter()).withWindowClosed(),
                        Session.of("gamma-fund", "trading", "VENY", "GAMMA", new Quoter()));

        List<SandboxEngine> engines = List.of(venueX, venueY, alphaBank);
        engines.forEach(SandboxEngine::start);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            engines.forEach(SandboxEngine::stop);
            traffic.shutdownNow();
            venueXDatabase.close();
            venueYDatabase.close();
        }, "sandbox-shutdown"));
        log.info("Sandbox running: engines announce to {}, telemetry goes to {}, venues store in {} and alert {}",
                settings.consoleUrl(), settings.otlpUrl(), settings.jdbcUrl(), settings.alertmanagerUrl());
    }

    private static JdbcMessageStoreSettings jdbcStore(SandboxDatabase database) {
        return JdbcMessageStoreSettings.builder()
                .dataSource(database.dataSource())
                .build();
    }

    private static AlertmanagerAlertsPublisherSettings alertmanager(SandboxSettings settings) {
        return AlertmanagerAlertsPublisherSettings.builder()
                .alertmanagerUrl(URI.create(settings.alertmanagerUrl()))
                .staticLabel("env", "sandbox")
                .build();
    }

    /**
     * Writes left in the queue by a previous run would replay into the tables just recreated, so it starts empty too.
     */
    private static String emptyQueueDirectory(String engineId) {
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "staffix-admin-sandbox", engineId + "-async-store");
        try {
            if (Files.exists(directory)) {
                try (Stream<Path> files = Files.walk(directory)) {
                    files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                }
            }
            return Files.createDirectories(directory).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
