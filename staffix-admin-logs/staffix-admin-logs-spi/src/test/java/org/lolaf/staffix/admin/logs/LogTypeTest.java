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
import org.lolaf.staffix.api.monitoring.FixMessageDirection;

import static org.assertj.core.api.Assertions.assertThat;

class LogTypeTest {

    @Test
    void readsADirectionAsAMessageAndItsAbsenceAsAnEvent() {
        assertThat(LogType.ofDirectionValue(FixMessageDirection.IN.getValue())).isEqualTo(LogType.IN);
        assertThat(LogType.ofDirectionValue(FixMessageDirection.OUT.getValue())).isEqualTo(LogType.OUT);
        assertThat(LogType.ofDirectionValue(null)).isEqualTo(LogType.EVENT);
        assertThat(LogType.ofDirectionValue("")).isEqualTo(LogType.EVENT);
    }

    @Test
    void readsNothingFromAValueStaffixDoesNotWrite() {
        assertThat(LogType.ofDirectionValue("sideways")).isNull();
    }

    @Test
    void givesEachMessageTypeItsDirection() {
        assertThat(LogType.IN.getDirection()).isEqualTo(FixMessageDirection.IN);
        assertThat(LogType.OUT.getDirection()).isEqualTo(FixMessageDirection.OUT);
        assertThat(LogType.EVENT.getDirection()).isNull();
    }
}
