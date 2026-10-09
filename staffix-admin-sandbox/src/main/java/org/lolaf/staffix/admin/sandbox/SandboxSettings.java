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
package org.lolaf.staffix.admin.sandbox;

/**
 * Where the sandbox engines send their telemetry and announce themselves; each is a system property of the same
 * name, with the defaults matching the docker stack and a console started locally with the development users.
 */
record SandboxSettings(String consoleUrl, String consoleUsername, String consolePassword, int adminApiPort, String otlpUrl) {

    static SandboxSettings fromSystemProperties() {
        return new SandboxSettings(
                System.getProperty("consoleUrl", "http://localhost:8080"),
                System.getProperty("consoleUsername", "engine"),
                System.getProperty("consolePassword", "engine"),
                Integer.getInteger("adminApiPort", 8686),
                System.getProperty("otlpUrl", "http://localhost:4318"));
    }
}
