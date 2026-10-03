package org.lolaf.staffix.admin.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.lolaf.staffix.admin.sandbox.SandboxEngine.Session;
import org.lolaf.staffix.admin.sandbox.SandboxEngine.Target;
import org.lolaf.staffix.admin.sandbox.Traffic.Listener;
import org.lolaf.staffix.admin.sandbox.Traffic.Publisher;
import org.lolaf.staffix.admin.sandbox.Traffic.Quoter;
import org.lolaf.staffix.admin.sandbox.Traffic.Requester;
import org.lolaf.staffix.admin.sandbox.fix44.msg.MessageTypes;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Three engines trading with each other: a bank initiating to two venues, which quote it, publish news to it and
 * send it a drop copy. Between them every session state the console shows appears: logged in, a backup that cannot
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
                .initiator("venue-y-market-data",
                        new Target(Session.of("venue-y", "market-data", "ALPHA_MD", "VENY", new Listener(MessageTypes.News)), VENUE_Y_PORT))
                .acceptor("drop-copy-gateway", ALPHA_BANK_PORT,
                        Session.of("venue-x", "drop-copy", "ALPHA", "VENX_DC", new Listener(MessageTypes.News)));

        SandboxEngine venueX = new SandboxEngine("venue-x", settings)
                .acceptor("venue-x-gateway", VENUE_X_PORT,
                        Session.of("alpha-bank", "trading", "VENX", "ALPHA", new Quoter()))
                .initiator("alpha-bank-drop-copy",
                        new Target(Session.of("alpha-bank", "drop-copy", "VENX_DC", "ALPHA", new Publisher(traffic, newsInterval)), ALPHA_BANK_PORT));

        SandboxEngine venueY = new SandboxEngine("venue-y", settings)
                .acceptor("venue-y-gateway", VENUE_Y_PORT,
                        Session.of("alpha-bank", "trading", "VENY", "ALPHA", new Quoter()),
                        Session.of("alpha-bank", "market-data", "VENY", "ALPHA_MD", new Publisher(traffic, newsInterval)),
                        Session.of("alpha-bank", "end-of-day", "VENY", "ALPHA_EOD", new Quoter()).withWindowClosed(),
                        Session.of("gamma-fund", "trading", "VENY", "GAMMA", new Quoter()));

        List<SandboxEngine> engines = List.of(venueX, venueY, alphaBank);
        engines.forEach(SandboxEngine::start);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            engines.forEach(SandboxEngine::stop);
            traffic.shutdownNow();
        }, "sandbox-shutdown"));
        log.info("Sandbox running: engines announce to {}, telemetry goes to {}", settings.consoleUrl(), settings.otlpUrl());
    }
}
