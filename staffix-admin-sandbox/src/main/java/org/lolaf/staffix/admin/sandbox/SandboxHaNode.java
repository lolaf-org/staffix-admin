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

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.lolaf.primos.ClusterConfig;
import org.lolaf.primos.Peer;
import org.lolaf.staffix.admin.sandbox.SandboxEngine.Session;
import org.lolaf.staffix.admin.sandbox.Traffic.Publisher;
import org.lolaf.staffix.admin.sandbox.Traffic.Quoter;
import org.lolaf.staffix.api.InstanceProvider;
import org.lolaf.staffix.api.session.FixSessionSettings.HaSessionSettings.ReplicationMode;
import org.lolaf.replicos.EncryptionMode;
import org.lolaf.replicos.ReplicationEncryptionSettings;
import org.lolaf.staffix.ha.ReplicationHaSettings;
import org.lolaf.staffix.ha.primos.PrimosLeaderElectionSettings;
import org.lolaf.staffix.stores.core.async.AsyncStoreSettings;
import org.lolaf.staffix.stores.messages.async.AsyncMessagesStoreSettings;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * One node of the venue-y group, {@code 1}, {@code 2} or {@code 3}, in a process of its own so that {@code ha.sh} can
 * kill it. Any node may lead, the others following it; {@code 3} is a disaster-recovery copy, which {@code SYNC} sends
 * never wait for and a switchover never goes to. Each
 * node has its own schema, as replication refuses a store shared with the leader. Its state lives under
 * {@code target/ha}: a node finding none starts afresh, one finding it carries on, as after a crash.
 */
@Slf4j
@UtilityClass
public class SandboxHaNode {

    private static final String HOST = "127.0.0.1";
    private static final String PAIR = "venue-y";
    // the same in witness/witness.properties
    private static final String SECRET = "sandbox-secret-change-it";
    private static final String REPLICATION_SECRET = "sandbox-replication-secret-change-it";
    private static final int WITNESS_PORT = 7340;
    private static final Duration NEWS_INTERVAL = Duration.ofMillis(500);
    private static final int EVENTS_BATCHING = 1000;
    private static final Set<String> SESSIONS_IN_DATABASE = Set.of("fixt-trading", "trading-2");
    static final int NODES = 3;
    private static final Set<String> SYNC_FOLLOWERS = Set.of("1", "2");

    static int fixPort(int nodeId) {
        return 7300 + nodeId;
    }

    public static void main(String[] args) throws IOException {
        int nodeId = Integer.parseInt(args[0]);
        String engineId = PAIR + "-" + nodeId;
        Path directory = Path.of("target", "ha", engineId);
        boolean firstStart = !Files.exists(directory);
        Path queueDirectory = Files.createDirectories(directory.resolve("async-store"));

        SandboxSettings settings = SandboxSettings.fromSystemProperties();
        ScheduledExecutorService traffic = Executors.newScheduledThreadPool(1);
        SandboxDatabase database = new SandboxDatabase(settings, "venue_y_" + nodeId, firstStart);
        SandboxEngine engine = new SandboxEngine(engineId, settings)
                .messagesStore(AsyncMessagesStoreSettings.builder()
                        .asyncStoreSettings(AsyncStoreSettings.builder()
                                .asyncQueueDirectory(queueDirectory.toString())
                                .eventsBatching(EVENTS_BATCHING)
                                .build())
                        .wrappedFixMessagesStoreSettings(SandboxApplication.jdbcStore(database))
                        .build())
                .alertsPublisher(SandboxApplication.alertmanager(settings))
                .highAvailability(highAvailability(nodeId, directory))
                .acceptorsSessionsIn(database.dataSource(), session -> SESSIONS_IN_DATABASE.contains(session.fixSessionId().getName()))
                .acceptor("venue-y-gateway", fixPort(nodeId),
                        Session.of("alpha-bank", "trading", "VENY", "ALPHA", new Quoter()).replicated(ReplicationMode.SYNC_APPLIED),
                        Session.of("alpha-bank", "trading-2", "VENY_2", "ALPHA", new Quoter()).replicated(ReplicationMode.SYNC_RECEIVED),
                        Session.fixt("alpha-bank", "fixt-trading", "VENY_T", "ALPHA", new FixtTraffic.Quoter()).replicated(ReplicationMode.ASYNC),
                        Session.of("alpha-bank", "market-data", "VENY", "ALPHA_MD", new Publisher(traffic, NEWS_INTERVAL)),
                        Session.of("alpha-bank", "end-of-day", "VENY", "ALPHA_EOD", new Quoter()).withWindowClosed(),
                        Session.of("gamma-fund", "trading", "VENY", "GAMMA", new Quoter()));

        engine.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            engine.stop();
            traffic.shutdownNow();
            database.close();
        }, "sandbox-ha-node-shutdown"));
        log.info("Node {} of group {} running, FIX on {}", nodeId, PAIR, fixPort(nodeId));
    }

    private static ReplicationHaSettings highAvailability(int nodeId, Path directory) {
        ReplicationHaSettings.ReplicationHaSettingsBuilder<?, ?> settings = ReplicationHaSettings.builder();
        ClusterConfig.ClusterConfigBuilder cluster = ClusterConfig.builder().applicationName(PAIR).nodeId(nodeId);
        for (int peer = 1; peer <= NODES; peer++) {
            settings.peer(String.valueOf(peer), replicationAddress(peer));
            if (peer != nodeId) {
                cluster.peer(Peer.application(peer, HOST, electionPort(peer)));
            }
        }
        return settings
                .nodeId(String.valueOf(nodeId))
                .syncFollowers(SYNC_FOLLOWERS)
                .monitoringInstanceId(SandboxEngine.METRICS)
                .alertsPublisherInstanceId(InstanceProvider.DEFAULT_INSTANCE_ID)
                .leaderElection(PrimosLeaderElectionSettings.builder()
                        .cluster(cluster.witness(Peer.witness(Peer.MAX_ID, HOST, WITNESS_PORT)).build())
                        .stateDirectory(directory.resolve("primos"))
                        .bindAddress(new InetSocketAddress(HOST, electionPort(nodeId)))
                        .clusterSecret(SECRET.getBytes(StandardCharsets.UTF_8))
                        .build())
                .encryption(ReplicationEncryptionSettings.builder()
                        .mode(encryptionMode())
                        .secret(REPLICATION_SECRET.getBytes(StandardCharsets.UTF_8))
                        .build())
                .build();
    }

    /**
     * {@code SANDBOX_HA_ENCRYPTION}, {@code required} by default: restarting one node {@code preferred} and the other
     * {@code disabled} replicates in clear and raises {@code ha-encryption}.
     */
    private static EncryptionMode encryptionMode() {
        return EncryptionMode.valueOf(System.getenv().getOrDefault("SANDBOX_HA_ENCRYPTION", "required").toUpperCase(Locale.ROOT));
    }

    private static int electionPort(int nodeId) {
        return WITNESS_PORT + nodeId;
    }

    private static InetSocketAddress replicationAddress(int nodeId) {
        return new InetSocketAddress(HOST, 17300 + nodeId);
    }
}
