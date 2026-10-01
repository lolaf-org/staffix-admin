package org.lolaf.staffix.admin.server.simulator;

import org.junit.jupiter.api.Test;
import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedLogsSourceTest {

    private final SimulatedLogsSource logs = new SimulatedLogsSource(
            new SimulatedEngineGateway(new SimulationScenario(JsonMapper.builder().build()).engines()));

    @Test
    void listsNewestFirstWithinTheRange() {
        Instant to = Instant.parse("2026-10-01T10:00:00Z");
        List<LogEntry> entries = logs.query(LogQuery.builder()
                .instanceId("paris-prod")
                .from(to.minusSeconds(600))
                .to(to)
                .limit(300)
                .build());

        assertThat(entries).hasSize(300)
                .isSortedAccordingTo(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .allSatisfy(e -> assertThat(e.getTimestamp()).isBetween(to.minusSeconds(600), to));
    }

    @Test
    void givesTheSameEntriesOnEveryQuery() {
        LogQuery query = LogQuery.builder().instanceId("nyc-uat").from(Instant.EPOCH).to(Instant.ofEpochSecond(3600)).limit(50).build();

        assertThat(logs.query(query)).isEqualTo(logs.query(query));
    }
}
