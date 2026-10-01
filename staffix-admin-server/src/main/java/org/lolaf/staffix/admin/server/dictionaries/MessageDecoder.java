package org.lolaf.staffix.admin.server.dictionaries;

import lombok.experimental.UtilityClass;
import org.lolaf.staffix.admin.server.engines.GatewayException;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Names each {@code tag=value} of a raw FIX message. Dictionaries come transport first: a FIXT session decodes
 * session messages and the header with FIXT11 and the rest with its application dictionary.
 */
@UtilityClass
public class MessageDecoder {

    private static final int MSG_TYPE = 35;

    public static DecodedMessage decode(String raw, char separator, List<FixDictionary> dictionaries) {
        List<String[]> pairs = Arrays.stream(raw.strip().split(Pattern.quote(String.valueOf(separator))))
                .filter(field -> !field.isBlank())
                .map(MessageDecoder::tagAndValue)
                .toList();
        String msgType = pairs.stream().filter(p -> p[0].equals(String.valueOf(MSG_TYPE))).map(p -> p[1]).findFirst().orElse(null);
        FixDictionary.Message message = msgType == null ? null : dictionaries.stream()
                .map(d -> d.getMessages().get(msgType))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        FixDictionary transport = dictionaries.getFirst();

        DecodedMessage.DecodedMessageBuilder decoded = DecodedMessage.builder()
                .msgType(msgType)
                .msgTypeName(message == null ? null : message.getName())
                .dictionaryIds(dictionaries.stream().map(FixDictionary::getId).toList());
        Deque<FixDictionary.Group> openGroups = new ArrayDeque<>();
        boolean bodyStarted = false;
        for (String[] pair : pairs) {
            int tag = Integer.parseInt(pair[0]);
            MessageSection section = section(tag, transport, bodyStarted);
            bodyStarted |= section != MessageSection.HEADER;
            while (!openGroups.isEmpty() && !openGroups.peek().getMembers().getTags().contains(tag)) {
                openGroups.pop();
            }
            boolean groupStart = !openGroups.isEmpty() && openGroups.peek().getDelimiterTag() == tag;
            FixDictionary.Field field = field(tag, dictionaries);
            decoded.field(DecodedField.builder()
                    .tag(tag)
                    .name(field == null ? null : field.getName())
                    .value(pair[1])
                    .meaning(field == null ? null : meaning(field, pair[1]))
                    .section(section)
                    .depth(openGroups.size())
                    .groupStart(groupStart)
                    .build());
            FixDictionary.Structure scope = !openGroups.isEmpty() ? openGroups.peek().getMembers()
                    : section == MessageSection.HEADER ? transport.getHeader()
                    : message != null ? message.getBody() : null;
            FixDictionary.Group opened = scope == null ? null : scope.getGroups().get(tag);
            if (opened != null && !pair[1].equals("0")) {
                openGroups.push(opened);
            }
        }
        return decoded.build();
    }

    private static String[] tagAndValue(String field) {
        int equals = field.indexOf('=');
        if (equals <= 0 || equals > 9 || !field.substring(0, equals).chars().allMatch(Character::isDigit)) {
            throw GatewayException.invalid("'" + field + "' is not a tag=value field");
        }
        return new String[]{field.substring(0, equals), field.substring(equals + 1)};
    }

    private static MessageSection section(int tag, FixDictionary transport, boolean bodyStarted) {
        if (transport.getTrailer().getTags().contains(tag)) {
            return MessageSection.TRAILER;
        }
        return !bodyStarted && isHeaderTag(tag, transport.getHeader()) ? MessageSection.HEADER : MessageSection.BODY;
    }

    private static boolean isHeaderTag(int tag, FixDictionary.Structure header) {
        return header.getTags().contains(tag) || header.getGroups().values().stream().anyMatch(g -> isHeaderTag(tag, g.getMembers()));
    }

    /**
     * The application dictionary wins over the transport one for a tag both define.
     */
    private static FixDictionary.Field field(int tag, List<FixDictionary> dictionaries) {
        for (int i = dictionaries.size() - 1; i >= 0; i--) {
            FixDictionary.Field field = dictionaries.get(i).field(tag);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    private static String meaning(FixDictionary.Field field, String value) {
        if (field.getValues().isEmpty()) {
            return null;
        }
        String meaning = Arrays.stream(value.split(" "))
                .map(v -> field.getValues().getOrDefault(v, v))
                .collect(Collectors.joining(" "));
        return meaning.equals(value) ? null : meaning;
    }
}
