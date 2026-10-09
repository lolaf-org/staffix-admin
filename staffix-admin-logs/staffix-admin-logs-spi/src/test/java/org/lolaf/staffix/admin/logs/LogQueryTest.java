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
