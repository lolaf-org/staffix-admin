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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.engines.Engine;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.EngineRegistry;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Groups the registered engines into pairs. Engines without high availability are left out.
 */
@Component
@RequiredArgsConstructor
public class HaPairs {

    private final EngineRegistry registry;
    private final EngineGateway gateway;
    /**
     * A node that stopped answering names no pair, yet it is the one the operator looks for after a failover. Lost when
     * the console restarts: such a node shows again once it answers.
     */
    private final Map<String, String> lastPairNames = new ConcurrentHashMap<>();

    public List<HaPair> list() {
        Map<String, HaPair.HaPairBuilder> named = new TreeMap<>();
        List<HaPair> unnamed = new ArrayList<>();
        for (Engine engine : registry.list()) {
            Optional<HaPair.Node> node = node(engine);
            if (node.isEmpty()) {
                continue;
            }
            String pairName = lastPairNames.get(engine.getEngineId());
            if (pairName == null) {
                unnamed.add(HaPair.builder().node(node.get()).build());
            } else {
                named.computeIfAbsent(pairName, name -> HaPair.builder().name(name)).node(node.get());
            }
        }
        return Stream.concat(named.values().stream().map(HaPair.HaPairBuilder::build), unnamed.stream()).toList();
    }

    private Optional<HaPair.Node> node(Engine engine) {
        String engineId = engine.getEngineId();
        if (!engine.isReachable()) {
            return silent(engineId);
        }
        try {
            Optional<HaStatus> status = gateway.getHa(engineId);
            remember(engineId, status.map(HaStatus::getPairName).orElse(null));
            return status.map(ha -> HaPair.Node.builder().engineId(engineId).status(ha).sessions(gateway.getHaSessions(engineId)).build());
        } catch (GatewayException e) {
            return silent(engineId);
        }
    }

    private Optional<HaPair.Node> silent(String engineId) {
        return lastPairNames.containsKey(engineId)
                ? Optional.of(HaPair.Node.builder().engineId(engineId).build())
                : Optional.empty();
    }

    private void remember(String engineId, @Nullable String pairName) {
        if (pairName == null) {
            lastPairNames.remove(engineId);
        } else {
            lastPairNames.put(engineId, pairName);
        }
    }
}
