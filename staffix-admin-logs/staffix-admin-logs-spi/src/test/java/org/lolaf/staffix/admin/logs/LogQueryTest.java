package org.lolaf.staffix.admin.logs;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LogQueryTest {

    private static final String ORDER = "8=FIX.4.4|35=D|11=ALPHA-1|55=EUR/USD|10=123|";

    @Test
    void matchesAnyLineWithoutFieldValues() {
        assertThat(query().build().hasFieldValue("Logon received")).isTrue();
    }

    @Test
    void matchesAFieldOfAnyTagEqualToOneOfTheValues() {
        assertThat(query().fieldValue("BETA-9").fieldValue("ALPHA-1").build().hasFieldValue(ORDER)).isTrue();
        assertThat(query().fieldValue("EUR/USD").build().hasFieldValue(ORDER)).isTrue();
    }

    @Test
    void matchesWholeValuesOnly() {
        assertThat(query().fieldValue("ALPHA").build().hasFieldValue(ORDER)).isFalse();
        assertThat(query().fieldValue("ALPHA-12").build().hasFieldValue(ORDER)).isFalse();
        assertThat(query().fieldValue("11=ALPHA-1").build().hasFieldValue(ORDER)).isFalse();
    }

    @Test
    void matchesTheLastFieldWithoutATrailingSeparator() {
        assertThat(query().fieldValue("ALPHA-1").build().hasFieldValue("35=D|11=ALPHA-1")).isTrue();
    }

    @Test
    void readsSohSeparatedMessages() {
        assertThat(query().fieldValue("ALPHA-1").build().hasFieldValue(ORDER.replace('|', '\u0001'))).isTrue();
    }

    @Test
    void ignoresTextThatIsNotAField() {
        assertThat(query().fieldValue("ALPHA-1").build().hasFieldValue("Rejected order=ALPHA-1")).isFalse();
    }

    private static LogQuery.LogQueryBuilder query() {
        return LogQuery.builder().engineId("alpha").from(Instant.EPOCH).to(Instant.EPOCH.plusSeconds(60)).limit(10);
    }
}
