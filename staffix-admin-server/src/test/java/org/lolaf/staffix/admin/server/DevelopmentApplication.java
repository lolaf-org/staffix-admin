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
package org.lolaf.staffix.admin.server;

import org.springframework.boot.SpringApplication;

import java.util.Map;

/**
 * Runs the console from the IDE for UI work: the test class path holds the plugins the production jar loads from
 * {@code plugins/} (YAML store, audit log, connectors) and the development users. Start it in the repository root,
 * with the simulator for engines.
 */
public final class DevelopmentApplication {

    private DevelopmentApplication() {
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(StaffixAdminApplication.class);
        application.setDefaultProperties(Map.of("staffix.admin.security.jaas-config", "classpath:jaas/dev.conf"));
        application.run(args);
    }
}
