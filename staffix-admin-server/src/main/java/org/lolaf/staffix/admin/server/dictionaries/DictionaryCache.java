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
