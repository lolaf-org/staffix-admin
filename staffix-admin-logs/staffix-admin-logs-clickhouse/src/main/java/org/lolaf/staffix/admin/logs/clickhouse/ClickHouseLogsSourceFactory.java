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
package org.lolaf.staffix.admin.logs.clickhouse;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.clickhouse.ClickHouseClient;
import org.lolaf.staffix.admin.logs.LogsSource;
import org.lolaf.staffix.admin.logs.LogsSourceFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public class ClickHouseLogsSourceFactory implements LogsSourceFactory<ClickHouseLogsSettings> {

    public static final String KIND = "clickhouse-logs";

    private final RestClient.Builder builder;
    private final JsonMapper jsonMapper;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public Class<ClickHouseLogsSettings> getSettingsClass() {
        return ClickHouseLogsSettings.class;
    }

    @Override
    public LogsSource create(ClickHouseLogsSettings settings) {
        ClickHouseClient.check(settings);
        return new ClickHouseLogsSource(settings, builder.clone(), jsonMapper);
    }
}
