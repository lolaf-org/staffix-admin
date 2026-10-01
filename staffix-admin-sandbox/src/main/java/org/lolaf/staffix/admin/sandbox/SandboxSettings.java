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
