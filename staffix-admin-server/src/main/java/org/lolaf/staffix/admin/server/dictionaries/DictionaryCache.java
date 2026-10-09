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
package org.lolaf.staffix.admin.server.dictionaries;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parsed dictionaries by content hash. A dictionary is fetched from an engine once; engines sharing it share the
 * entry. Unbounded: an installation runs a handful of dictionaries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictionaryCache {

    private final Map<String, FixDictionary> dictionaries = new ConcurrentHashMap<>();
    private final EngineGateway gateway;

    public FixDictionary get(String engineId, DictionaryRef ref) {
        return dictionaries.computeIfAbsent(ref.getHash(), hash -> {
            log.info("Loading dictionary {} ({}) from engine {}", ref.getId(), hash, engineId);
            return FixDictionaryParser.parse(ref.getId(), gateway.getDictionaryXml(engineId, ref.getId()));
        });
    }
}
