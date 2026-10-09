# Plugins

Every jar in `plugins/` (next to `staffix-admin.jar`; `-Dloader.path=...` names another directory) joins the
console's class path at startup. A plugin is a jar whose
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` names its auto-configuration, which
registers beans of the console's SPIs:

| SPI (module) | What a plugin's bean does |
|---|---|
| `MetricsSourceFactory` (`staffix-admin-metrics-spi`), `LogsSourceFactory` (`staffix-admin-logs-spi`) | Adds a connector kind, with its settings class (`@Description`, `@Secret` from `staffix-admin-connector-spi`) |
| `SecretResolver` (`staffix-admin-connector-spi`) | Adds a reference scheme, e.g. `${vault:...}`; a scheme already taken fails at startup |
| `AuditSink` (`staffix-admin-audit-spi`) | Receives every audit entry, as `staffix-admin-audit-log` does; at least one is required |
| `ConsoleConfigStore` (`staffix-admin-store-spi`) | Keeps the console configuration (connectors, trace settings), as `staffix-admin-store-yaml` does (`console.yaml` of the data directory); exactly one is required |

The console provides Spring Boot (web, RestClient), Jackson, the SPIs above and `staffix-api`: a plugin depends on
them as `provided`, as `staffix-admin-logs/staffix-admin-logs-loki/pom.xml` does. Anything else it needs, it shades
into its jar or ships as more jars in `plugins/`.

The distribution's own `plugins/` holds the YAML store, the audit log and the Prometheus, Loki and Elasticsearch
connectors (the demo distribution adds the demo connector). The console refuses to start without a store and an audit
sink; the connectors you do not use can go. In the Docker image, add plugins by building on it rather than mounting
over `plugins/`: see [Installation](installation.md#docker-image).

## Writing an audit sink

The console ships no durable sink of its own: to keep the [audit trail](security.md#audit-trail) in a file of its
own, a database or a SIEM, write one.

1. Implement `AuditSink` from `staffix-admin-audit-spi` (`provided`). `record(AuditEntry)` is called after the
   action, on the request's thread: keep it short, or hand the entry to a thread of your own. A sink that throws is
   logged and neither stops the other sinks nor undoes the action, which already happened, so a sink that must not
   lose entries buffers or retries itself.
2. Declare it as a bean in an `@AutoConfiguration` class, named in your jar's
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
3. Drop the jar into `plugins/`. Every sink receives every entry: keep `staffix-admin-audit-log` next to yours to
   keep the log lines too, or remove it.
