package org.lolaf.staffix.admin.server.dictionaries;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.web.TelemetryProblems;
import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        TelemetryProblems.requireGroupForSession(group, sessionId);
        Map<String, MessageTypeInfo> byMsgType = new TreeMap<>();
        gateway.listGroups(engineId).stream()
                .filter(g -> group == null || g.getGroup().equals(group))
                .flatMap(g -> g.getSessions().stream())
                .flatMap(session -> session.getConfigIds().stream()
                        .filter(configId -> sessionId == null || configId.equals(sessionId))
                        .map(configId -> SessionRef.of(engineId, session.getGroup(), configId)))
                .flatMap(config -> gateway.getDictionaries(config).stream())
                .collect(Collectors.toMap(DictionaryRef::getHash, Function.identity(), (a, b) -> a))
                .values()
                .forEach(ref -> dictionaryCache.get(engineId, ref).getMessages().values()
                        .forEach(m -> byMsgType.putIfAbsent(m.getMsgType(), new MessageTypeInfo(m.getMsgType(), m.getName()))));
        return byMsgType.values().stream().sorted(Comparator.comparing(MessageTypeInfo::getName)).toList();
    }
}
