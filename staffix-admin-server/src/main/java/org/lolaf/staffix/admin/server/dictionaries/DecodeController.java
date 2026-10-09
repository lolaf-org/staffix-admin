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

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.web.TelemetryProblems;
import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.GatewayException;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class DecodeController {

    private final EngineGateway gateway;
    private final DictionaryCache dictionaryCache;

    /**
     * A POST for the message's size, though it changes nothing: readers may decode.
     */
    @PostMapping("/api/messages/decode")
    public DecodedMessage decode(@Valid @RequestBody DecodeRequest request) {
        SessionRef config = SessionRef.of(request.getEngineId(), request.getGroup(), request.getSessionId());
        List<FixDictionary> dictionaries = gateway.getDictionaries(config).stream()
                .map(ref -> dictionaryCache.get(request.getEngineId(), ref))
                .toList();
        if (dictionaries.isEmpty()) {
            throw GatewayException.notFound("Session '" + request.getGroup() + "." + request.getSessionId()
                    + "' of engine '" + request.getEngineId() + "' serves no dictionary to decode with");
        }
        return MessageDecoder.decode(request.getMessage(), request.getSeparator(), dictionaries);
    }

    /**
     * Every message type the dictionaries of the sessions in scope define, sorted by name. Sessions may use
     * different, user-made dictionaries, so it is their union.
     */
    @GetMapping("/api/messages/types")
    public List<MessageTypeInfo> listMessageTypes(@RequestParam String engineId,
                                                  @RequestParam(required = false) @Nullable String group,
                                                  @RequestParam(required = false) @Nullable String sessionId) {
        Map<String, MessageTypeInfo> byMsgType = new TreeMap<>();
        dictionaries(engineId, group, sessionId).forEach(dictionary -> dictionary.getMessages().values()
                .forEach(m -> byMsgType.putIfAbsent(m.getMsgType(), new MessageTypeInfo(m.getMsgType(), m.getName()))));
        return byMsgType.values().stream().sorted(Comparator.comparing(MessageTypeInfo::getName)).toList();
    }

    /**
     * The fields of {@code tags} the dictionaries of the sessions in scope define, with their enum values, in the order
     * asked. Ask for one session: dictionaries of different FIX versions give the same value different meanings.
     */
    @GetMapping("/api/messages/fields")
    public List<FieldInfo> listFields(@RequestParam String engineId,
                                      @RequestParam(required = false) @Nullable String group,
                                      @RequestParam(required = false) @Nullable String sessionId,
                                      @RequestParam List<Integer> tags) {
        List<FixDictionary> dictionaries = dictionaries(engineId, group, sessionId);
        return tags.stream()
                .distinct()
                .flatMap(tag -> dictionaries.stream().map(dictionary -> dictionary.field(tag)).filter(Objects::nonNull).findFirst().stream())
                .map(field -> new FieldInfo(field.getTag(), field.getName(), field.getValues()))
                .toList();
    }

    private List<FixDictionary> dictionaries(String engineId, @Nullable String group, @Nullable String sessionId) {
        TelemetryProblems.requireGroupForSession(group, sessionId);
        return gateway.listGroups(engineId).stream()
                .filter(g -> group == null || g.getGroup().equals(group))
                .flatMap(g -> g.getSessions().stream())
                .flatMap(session -> session.getConfigIds().stream()
                        .filter(configId -> sessionId == null || configId.equals(sessionId))
                        .map(configId -> SessionRef.of(engineId, session.getGroup(), configId)))
                .flatMap(config -> gateway.getDictionaries(config).stream())
                .collect(Collectors.toMap(DictionaryRef::getHash, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .map(ref -> dictionaryCache.get(engineId, ref))
                .toList();
    }
}
