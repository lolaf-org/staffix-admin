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
