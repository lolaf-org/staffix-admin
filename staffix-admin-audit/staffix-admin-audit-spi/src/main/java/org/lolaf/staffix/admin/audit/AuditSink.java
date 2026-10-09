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
package org.lolaf.staffix.admin.audit;

/**
 * Where the console records audit entries; every sink bean receives each entry, and the console refuses to start
 * without one. {@code staffix-admin-audit-log} logs them on {@code staffix.admin.audit}.
 */
public interface AuditSink {

    /**
     * Called after the action, on the request's thread. A failure is logged and does not undo the action, which
     * already happened; a sink that must not lose entries buffers or retries itself.
     */
    void record(AuditEntry entry);
}
