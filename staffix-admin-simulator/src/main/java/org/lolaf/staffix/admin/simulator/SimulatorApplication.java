package org.lolaf.staffix.admin.simulator;

import lombok.extern.slf4j.Slf4j;
import org.lolaf.ringos.Deadline;
import org.lolaf.staffix.admin.http.HttpAdminApiSettings;
import org.lolaf.staffix.api.admin.AdminApiExporter;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simulated engines announcing themselves to a console through staffix's own HTTP admin API connector, plus their
 * demo metrics and logs. System properties {@code consoleUrl}, {@code consoleUsername}, {@code consolePassword},
 * {@code adminApiPort} and {@code demoPort} override the defaults.
 */
@Slf4j
public final class SimulatorApplication {

    private SimulatorApplication() {
    }

    public static void main(String[] args) throws IOException {
        String consoleUrl = System.getProperty("consoleUrl", "http://localhost:8080");
        List<SimulatedEngine> engines = SimulationScenario.engines();
        List<AdminApiExporter> exporters = engines.stream().map(engine -> {
            AdminApiExporter exporter = HttpAdminApiSettings.builder()
                    .port(Integer.getInteger("adminApiPort", 8687))
                    .announceUrl(consoleUrl)
                    .announceUsername(System.getProperty("consoleUsername", "engine"))
                    .announcePassword(System.getProperty("consolePassword", "engine"))
                    .announceInterval(Duration.ofSeconds(10))
                    .build()
                    .instance();
            exporter.export(engine);
            return exporter;
        }).toList();
        DemoServer demo = new DemoServer(engines, Integer.getInteger("demoPort", 8688));
        demo.start();
        Random random = new SecureRandom();
        ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("simulator").factory());
        ticker.scheduleAtFixedRate(() -> engines.forEach(engine -> engine.tick(random)), 1, 1, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ticker.shutdownNow();
            exporters.forEach(exporter -> exporter.shutdown(Deadline.of(Duration.ofSeconds(2))));
            demo.stop();
        }, "simulator-shutdown"));
        log.info("Simulator running: {} engines announce to {}", engines.size(), consoleUrl);
    }
}
