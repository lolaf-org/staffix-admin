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
package org.lolaf.staffix.admin.store;

/**
 * Where the console keeps what admins configure: its connectors, the engines linked to them, the trace settings. The
 * deploying team picks the implementation: a single console can use a file, several consoles behind a load balancer need a shared store.
 */
public interface ConsoleConfigStore {

    /**
     * {@link ConsoleConfig#EMPTY} until the first save.
     *
     * @throws ConsoleConfigStoreException if the store cannot be read
     */
    ConsoleConfig load();

    /**
     * Stores {@code config} if its version is still the stored one, and returns it with the next version.
     *
     * @throws StaleConsoleConfigException if the store changed since {@code config} was loaded
     * @throws ConsoleConfigStoreException if the store cannot be written
     */
    ConsoleConfig save(ConsoleConfig config);
}
