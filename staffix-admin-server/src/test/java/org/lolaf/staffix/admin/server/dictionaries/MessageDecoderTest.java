package org.lolaf.staffix.admin.server.dictionaries;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.server.engines.GatewayException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class MessageDecoderTest {

    private static final String ORDER_WITH_PARTIES = "8=FIX.4.4|9=120|35=D|34=12|49=US|56=THEM|52=20261001-09:30:00.000"
            + "|11=ORD-1|453=2|448=ALICE|447=D|452=1|448=BOB|447=D|452=3|55=AAPL|54=1|38=100|40=2|44=101.5|9999=x|10=123|";

    private static FixDictionary fix44;

    @BeforeAll
    static void loadFix44() throws IOException {
        try (InputStream in = MessageDecoderTest.class.getResourceAsStream("/FIX44.xml")) {
            fix44 = FixDictionaryParser.parse("default-FIX.4.4", in.readAllBytes());
        }
    }

    @Test
    void namesTheMessageAndItsFields() {
        DecodedMessage decoded = decode(ORDER_WITH_PARTIES);

        assertThat(decoded.getMsgType()).isEqualTo("D");
        assertThat(decoded.getMsgTypeName()).isEqualTo("NewOrderSingle");
        assertThat(field(decoded, 54)).extracting(DecodedField::getName, DecodedField::getMeaning).containsExactly("Side", "BUY");
        assertThat(field(decoded, 40).getMeaning()).isEqualTo("LIMIT");
        assertThat(field(decoded, 44).getMeaning()).isNull();
    }

    @Test
    void splitsHeaderBodyAndTrailer() {
        DecodedMessage decoded = decode(ORDER_WITH_PARTIES);

        assertThat(field(decoded, 49).getSection()).isEqualTo(MessageSection.HEADER);
        assertThat(field(decoded, 11).getSection()).isEqualTo(MessageSection.BODY);
        assertThat(field(decoded, 10).getSection()).isEqualTo(MessageSection.TRAILER);
    }

    @Test
    void nestsRepeatingGroups() {
        List<DecodedField> fields = decode(ORDER_WITH_PARTIES).getFields();

        assertThat(fields.subList(fields.indexOf(field(fields, 453)), fields.indexOf(field(fields, 55)) + 1))
                .extracting(DecodedField::getTag, DecodedField::getDepth, DecodedField::isGroupStart)
                .containsExactly(
                        tuple(453, 0, false),
                        tuple(448, 1, true), tuple(447, 1, false), tuple(452, 1, false),
                        tuple(448, 1, true), tuple(447, 1, false), tuple(452, 1, false),
                        tuple(55, 0, false));
    }

    @Test
    void keepsAnUnknownTagUnnamed() {
        DecodedField unknown = field(decode(ORDER_WITH_PARTIES), 9999);

        assertThat(unknown.getName()).isNull();
        assertThat(unknown.getValue()).isEqualTo("x");
    }

    @Test
    void readsASohSeparatedMessage() {
        DecodedMessage decoded = MessageDecoder.decode("8=FIX.4.4\u000135=0\u000110=000\u0001", '\u0001', List.of(fix44));

        assertThat(decoded.getMsgTypeName()).isEqualTo("Heartbeat");
    }

    @Test
    void refusesAFieldWithoutTag() {
        assertThatThrownBy(() -> decode("8=FIX.4.4|oops|10=000|")).isInstanceOf(GatewayException.class);
    }

    private static DecodedMessage decode(String message) {
        return MessageDecoder.decode(message, '|', List.of(fix44));
    }

    private static DecodedField field(DecodedMessage decoded, int tag) {
        return field(decoded.getFields(), tag);
    }

    private static DecodedField field(List<DecodedField> fields, int tag) {
        return fields.stream().filter(f -> f.getTag() == tag).findFirst().orElseThrow();
    }
}
