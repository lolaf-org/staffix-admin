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
package org.lolaf.staffix.admin.audit.log;

import lombok.extern.slf4j.Slf4j;
import org.lolaf.staffix.admin.audit.AuditEntry;
import org.lolaf.staffix.admin.audit.AuditOutcome;
import org.lolaf.staffix.admin.audit.AuditSink;

/**
 * One line per entry on the {@code staffix.admin.audit} logger, INFO when it succeeded and WARN when
 * it failed, so the trail is as durable as the deployment's logging.
 */
@Slf4j(topic = "staffix.admin.audit")
public class LoggerAuditSink implements AuditSink {

    @Override
    public void record(AuditEntry entry) {
        String session = entry.getEngineId() == null ? ""
                : " engine=" + entry.getEngineId() + " group=" + entry.getGroup() + " session=" + entry.getSessionId();
        if (entry.getOutcome() == AuditOutcome.OK) {
            log.info("user={}{} operation=[{}] outcome=OK", entry.getUser(), session, entry.getOperation());
        } else {
            log.warn("user={}{} operation=[{}] outcome=FAILED reason=[{}]", entry.getUser(), session, entry.getOperation(), entry.getReason());
        }
    }
}
