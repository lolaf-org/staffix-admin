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
package org.lolaf.staffix.admin.server.ha;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.engines.Engine;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.EngineRegistry;
import org.lolaf.staffix.admin.server.engines.GatewayException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HaPairsTest {

    private final EngineRegistry registry = mock(EngineRegistry.class);
    private final EngineGateway gateway = mock(EngineGateway.class);
    private final HaPairs pairs = new HaPairs(registry, gateway);
    private final List<Engine> engines = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(registry.list()).thenReturn(engines);
        when(gateway.getHa(anyString())).thenReturn(Optional.empty());
    }

    private void engine(String engineId, String pairName) {
        engines.add(Engine.builder().engineId(engineId).reachable(true).lastAnnouncedAt(Instant.EPOCH).build());
        when(gateway.getHa(engineId)).thenReturn(Optional.of(status(pairName)));
    }

    private static HaStatus status(String pairName) {
        return HaStatus.builder().nodeId("1").pairName(pairName).role(HaRole.LEADER).followerConnections(List.of()).droppedFollowers(List.of()).build();
    }

    private static List<String> engineIds(HaPair pair) {
        return pair.getNodes().stream().map(HaPair.Node::getEngineId).toList();
    }

    @Test
    void enginesSharingAPairNameFormOnePairAndNodeIdsDoNotMatter() {
        engine("engine-1", "market-data");
        engine("engine-2", "gateway");
        engine("engine-3", "market-data");
        engine("engine-4", "gateway");

        List<HaPair> listed = pairs.list();

        assertThat(listed).extracting(HaPair::getName).containsExactly("gateway", "market-data");
        assertThat(engineIds(listed.get(0))).containsExactly("engine-2", "engine-4");
        assertThat(engineIds(listed.get(1))).containsExactly("engine-1", "engine-3");
    }

    @Test
    void aNodeCarriesTheSessionsItReplicates() {
        engine("engine-1", "gateway");
        HaSession session = HaSession.builder().group("alpha").name("trading").build();
        when(gateway.getHaSessions("engine-1")).thenReturn(List.of(session));

        assertThat(pairs.list().get(0).getNodes().get(0).getSessions()).containsExactly(session);
    }

    @Test
    void anEngineWithoutHighAvailabilityIsLeftOut() {
        engines.add(Engine.builder().engineId("plain").reachable(true).lastAnnouncedAt(Instant.EPOCH).build());

        assertThat(pairs.list()).isEmpty();
    }

    @Test
    void anUnnamedEngineIsListedAloneAfterTheNamedPairs() {
        engine("unnamed-1", null);
        engine("unnamed-2", null);
        engine("engine-1", "gateway");

        List<HaPair> listed = pairs.list();

        assertThat(listed).extracting(HaPair::getName).containsExactly("gateway", null, null);
        assertThat(engineIds(listed.get(1))).containsExactly("unnamed-1");
    }

    @Test
    void aNodeThatStoppedAnsweringStaysInItsPairWithoutAStatus() {
        engine("engine-1", "gateway");
        engine("engine-2", "gateway");
        pairs.list();
        when(gateway.getHa("engine-2")).thenThrow(GatewayException.unavailable("engine-2", new RuntimeException("down")));

        HaPair pair = pairs.list().get(0);

        assertThat(engineIds(pair)).containsExactly("engine-1", "engine-2");
        assertThat(pair.getNodes().get(1).getStatus()).isNull();
    }

    @Test
    void anEngineKnownUnreachableIsNotCalled() {
        engine("engine-1", "gateway");
        pairs.list();
        engines.set(0, Engine.builder().engineId("engine-1").reachable(false).lastAnnouncedAt(Instant.EPOCH).build());
        clearInvocations(gateway);

        assertThat(pairs.list()).singleElement().satisfies(pair -> assertThat(pair.getNodes().get(0).getStatus()).isNull());
        verify(gateway, never()).getHa("engine-1");
    }

    @Test
    void anEngineNeverSeenAnsweringIsLeftOut() {
        engines.add(Engine.builder().engineId("down").reachable(false).lastAnnouncedAt(Instant.EPOCH).build());

        assertThat(pairs.list()).isEmpty();
    }
}
