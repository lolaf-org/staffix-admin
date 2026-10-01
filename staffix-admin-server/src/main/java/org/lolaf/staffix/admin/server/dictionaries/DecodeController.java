package org.lolaf.staffix.admin.server.dictionaries;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.engines.EngineGateway;
import org.lolaf.staffix.admin.server.engines.SessionRef;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
