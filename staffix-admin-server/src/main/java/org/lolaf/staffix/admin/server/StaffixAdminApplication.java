package org.lolaf.staffix.admin.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StaffixAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(StaffixAdminApplication.class, args);
    }
}
