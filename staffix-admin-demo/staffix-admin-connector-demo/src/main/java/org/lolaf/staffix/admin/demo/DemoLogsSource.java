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
package org.lolaf.staffix.admin.demo;

import org.lolaf.staffix.admin.logs.LogEntry;
import org.lolaf.staffix.admin.logs.LogQuery;
import org.lolaf.staffix.admin.logs.LogType;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceException;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

final class DemoLogsSource implements LogsSource {

    private final DemoClient client;

    DemoLogsSource(DemoClient client) {
        this.client = client;
    }

    @Override
    public List<LogEntry> query(LogQuery query) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("engineId", query.getEngineId());
        parameters.put("group", query.getGroup());
        parameters.put("sessionId", query.getSessionId());
        parameters.put("from", query.getFrom());
        parameters.put("to", query.getTo());
        parameters.put("text", query.getText());
        parameters.put("types", String.join(",", query.getTypes().stream().map(LogType::name).toList()));
        parameters.put("msgTypes", String.join(",", query.getMsgTypes()));
        parameters.put("fieldValues", String.join(",", query.getFieldValues()));
        parameters.put("limit", query.getLimit());
        JsonNode response = client.get("/demo/logs", parameters, LogsSourceException::new);
        return StreamSupport.stream(response.spliterator(), false)
                .map(entry -> LogEntry.builder()
                        .timestamp(Instant.parse(entry.path("timestamp").asString()))
                        .group(entry.path("group").asString(null))
                        .sessionId(entry.path("sessionId").asString(null))
                        .type(LogType.valueOf(entry.path("type").asString()))
                        .line(entry.path("line").asString())
                        .build())
                .toList();
    }

    @Override
    public void testConnection() {
        client.ping(LogsSourceException::new);
    }
}
