package org.lolaf.staffix.admin.server;

import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;

@Value
@ConfigurationProperties("staffix.admin")
public class AdminProperties {
    /**
     * An engine silent for longer is shown unreachable. Several times the engines' announce interval, so one lost
     * announcement does not flag it.
     */
    Duration engineHeartbeatTimeout;
    /**
     * How long a call to an engine may take, connecting included, before the operator is told it did not answer.
     */
    Duration engineRequestTimeout;
    /**
     * Where the console keeps what admins configure, such as its connectors; relative to the working directory.
     */
    Path dataDir;

    public AdminProperties(@DefaultValue("90s") Duration engineHeartbeatTimeout,
                           @DefaultValue("5s") Duration engineRequestTimeout,
                           @DefaultValue("data") Path dataDir) {
        this.engineHeartbeatTimeout = engineHeartbeatTimeout;
        this.engineRequestTimeout = engineRequestTimeout;
        this.dataDir = dataDir;
    }
}
