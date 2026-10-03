package org.lolaf.staffix.admin.demo;

import lombok.Value;
import org.lolaf.staffix.admin.connector.Description;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@Value
public class DemoSettings {
    @Description("The simulator's demo endpoint, e.g. http://localhost:8688.")
    URI url;
    @Description("How long a query may take, connecting included, before the console reports the simulator did not answer.")
    Duration timeout;

    public DemoSettings(URI url, @DefaultValue("5s") Duration timeout) {
        this.url = url;
        this.timeout = timeout;
    }
}
