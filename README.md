# staffix-admin

HTTP admin console for [staffix](https://github.com/lolaf-org/staffix) FIX engines: a Spring Boot backend that
serves a React UI and proxies the engines' HTTP admin connectors. The browser never talks to an engine directly.

## Modules

| Module | Content |
|---|---|
| `staffix-admin-server` | Spring Boot backend: REST API for the UI, engine proxy. Its controllers are the API contract |
| `staffix-admin-ui` | React + Ant Design UI |
| `staffix-admin-app` | The executable jar: the server with the UI inside |
| `staffix-admin-metrics` | `staffix-admin-metrics-spi`, the SPI for metrics backends; `staffix-admin-metrics-prometheus`, metrics from Prometheus |
| `staffix-admin-logs` | `staffix-admin-logs-spi`, the SPI for logs backends; `staffix-admin-logs-loki`, FIX message logs from Loki |
| `staffix-admin-store` | `staffix-admin-store-spi`, where the connectors are kept; `staffix-admin-store-yaml`, a YAML file |
| `staffix-admin-audit` | `staffix-admin-audit-spi`, where audit entries go; `staffix-admin-audit-log`, log lines |
| `staffix-admin-connector-spi` | What a backend plugin declares about its settings: descriptions, secrets, secret references |
| `staffix-admin-dist` | The distribution: the console's jar, its plugins and an example configuration |
| `staffix-admin-demo` | `staffix-admin-simulator`, simulated engines seen by the console as real ones; `staffix-admin-connector-demo`, the `demo-metrics` and `demo-logs` kinds reading their telemetry; `staffix-admin-dist-demo`, the distribution with them and demo users, and a docker compose running console and simulator |

## API contract

The Java controllers and DTOs are the source of truth. springdoc derives the OpenAPI spec from them, and the UI's
TypeScript types are generated from that spec, so changing a DTO breaks the UI compilation wherever it no longer
matches. A DTO field is required unless annotated JSpecify `@Nullable`.

The spec is never written by hand nor committed: the Maven build starts the server briefly and saves it to
`staffix-admin-server/target/openapi/admin-api.json`; a running server serves it at `/v3/api-docs`.

## Build and run

The console builds against the staffix snapshot (`0.9.1-SNAPSHOT`, branch `http-admin-api`), whose HTTP admin API
connector engines announce themselves with: install staffix locally first (`./mvnw install` in the staffix
repository).

```shell
./mvnw install
cd staffix-admin-dist/target/staffix-admin/staffix-admin
java -Dloader.main=org.lolaf.staffix.admin.server.security.jaas.PasswordHasher -jar staffix-admin.jar 'secret'
# add a user with that hash to config/users.properties, e.g. alice = {bcrypt}..., WRITER
java -jar staffix-admin.jar
```

The build produces the distribution `staffix-admin-dist/target/staffix-admin.zip` (and `.tar.gz`, and unpacked under
`staffix-admin-dist/target/staffix-admin/`): `staffix-admin.jar`, `plugins/` with the YAML store, the audit log,
the Prometheus and Loki connectors, and `config/`, read from the directory the console starts in: `application.yml`, `jaas.conf` and
`users.properties`, which defines no user until you add one, so a fresh install lets nobody in.

Open http://localhost:8080. The build downloads its own Node into `staffix-admin-ui/node/`; no system Node needed.

Engines appear once they announce themselves: add staffix's HTTP admin API connector to each engine
(`staffix-admin-api-http-impl`, see staffix's runtime administration guide) with `announceUrl` pointing here and the
credentials of a user with the `ENGINE` role. The console then calls each engine with the token it announced.

To try the console without real engines, run the demo: the console with the demo users (`reader`, `writer`,
`engine`, password = name) and the simulator, a separate process whose three engines announce themselves through
staffix's own HTTP admin API connector, with sessions that drop, reconnect and follow session windows, and serve
generated metrics and FIX logs to the `demo-metrics` and `demo-logs` connectors. With Docker:

```shell
./mvnw install
(cd staffix-admin-demo/staffix-admin-dist-demo/target/docker && docker compose up --build)
```

or from the demo distribution (`staffix-admin-demo/staffix-admin-dist-demo/target/staffix-admin-demo.zip`, also
unpacked under `staffix-admin-demo/staffix-admin-dist-demo/target/staffix-admin-demo/`), whose `data/` already holds the connectors to the simulator:

```shell
(cd staffix-admin-demo/staffix-admin-dist-demo/target/staffix-admin-demo/staffix-admin && java -jar staffix-admin.jar) &
java -jar staffix-admin-demo/staffix-admin-simulator/target/staffix-admin-simulator.jar
```

## Metrics and logs

The console reads engines' metrics and FIX logs from backends described by connectors. A connector has an id, a
name, a kind (`prometheus` for metrics, `loki` for logs) and that kind's settings; there can be several of a kind,
e.g. one Loki per environment. A session's logs go through one of its engine's messages loggers and its metrics
through one of its monitoring plugins, as the engine reports; each of these emitters reads from the connector
assigned to it, else from its engine's `defaults`, else from the global `defaults`. Telemetry with none, or `none`,
shows as not collected, and so do the metrics of a session without a monitoring plugin. A view spanning several
backends reads each and merges them; a latency quantile of a message type then shows the highest of the backends'.
The console creates no connector itself.

Connectors live in `connectors.yaml` under `staffix.admin.data-dir` (default `data`, relative to the working
directory), readable by the console's user only. Writers manage them in the Connectors tab: connectors with a form
per kind and a connection test, the defaults, and each engine's emitters with the sessions using them. The file can
also be edited by hand; changes apply without a restart.

A setting may be a reference resolved when the connector is built: `${env:NAME}` reads an environment variable,
`${file:/run/secrets/name}` a mounted secret file (without its trailing newline). Credentials, such as Loki's
`tenant`, are never shown by the console: give them as a reference, or let the console store them encrypted
(`enc:v1:...`, AES-GCM) under a master key, base64 encoded 32 bytes (`openssl rand -base64 32`) given as
`staffix.admin.secrets.master-key` (env `STAFFIX_ADMIN_SECRETS_MASTERKEY`) or `master-key-file`. Without a master
key, the console refuses to save a credential that is not a reference. To rotate the key, set the new one and move the
old one to `staffix.admin.secrets.previous-master-keys`; each save re-encrypts under the new key. A lost key means
typing those credentials again.

```yaml
version: 0
connectors:
  - id: prod-prometheus
    name: Production Prometheus
    kind: prometheus
    settings:
      url: http://prometheus:9090
      timerBaseUnit: microseconds   # for engines that do not report their micrometer baseTimeUnit
  - id: prod-loki
    name: Production Loki
    kind: loki
    settings:
      url: http://loki:3100
      indexedLabels: [ fix_eid, fix_sg, fix_sn ]   # labels Loki indexes, matched in the stream selector
      streamSelector: '{service_name="fix-gateway"}'  # default {service_name=~".+"}
      tenant: ${env:LOKI_TENANT}   # X-Scope-OrgID, for a multi-tenant Loki
defaults:
  metrics: prod-prometheus
  logs: prod-loki
engines:
  uat-gateway:
    defaults:
      metrics: none   # not collected
  prod-gateway:
    loggers:
      clients-otlp: clients-loki   # by messages logger instance id
    monitors:
      clients-metrics: none        # by monitoring plugin instance id
```

The console also reads how each engine emits (staffix's `GET emitters`): the timer unit of its monitoring plugin,
the meters it has on (charts of the others are not offered), and whether a messages logger exports anything a
backend reads (a slf4j or file logger does not, so its sessions' logs show as not collected).

**Prometheus** charts read the engines' micrometer meters received through the OpenTelemetry collector with
`translation_strategy: UnderscoreEscapingWithoutSuffixes` (as in `staffix-admin-sandbox/docker`). The engines must
publish percentile histograms for the latency charts; RTT and clock offset need staffix's RTT measurement enabled.

**Loki** holds what staffix's OTLP messages logger sends: every message in and out, and session events. Loki keeps
the `fix.*` attributes as structured metadata, so by default the console filters them after a stream selector that
scans every stream of the time range. Promote `fix.eid`, `fix.sg` and `fix.sn` to index labels in Loki's
`otlp_config` (as `staffix-admin-sandbox/docker/conf/loki.yaml` does) and list them in `indexedLabels`, so a query
reads only the streams of its engine, group or session.

## Plugins

Every jar in `plugins/` (next to `staffix-admin.jar`; `-Dloader.path=...` names another directory) joins the
console's class path at startup. A plugin is a jar whose `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
names its auto-configuration, which registers beans of the console's SPIs:

| SPI (module) | What a plugin's bean does |
|---|---|
| `MetricsSourceFactory` (`staffix-admin-metrics-spi`), `LogsSourceFactory` (`staffix-admin-logs-spi`) | adds a connector kind, with its settings class (`@Description`, `@Secret` from `staffix-admin-connector-spi`) |
| `SecretResolver` (`staffix-admin-connector-spi`) | adds a reference scheme, e.g. `${vault:...}`; a scheme already taken fails at startup |
| `AuditSink` (`staffix-admin-audit-spi`) | receives every audit entry, as `staffix-admin-audit-log` does (lines of the `staffix.admin.audit` logger); at least one is required |
| `ConnectorStore` (`staffix-admin-store-spi`) | keeps the connectors, as `staffix-admin-store-yaml` does (`connectors.yaml` of the data directory); exactly one is required |

The console provides Spring Boot (web, RestClient), Jackson, the SPIs above and `staffix-api`: a plugin depends on
them as `provided`, as `staffix-admin-logs/staffix-admin-logs-loki/pom.xml` does. Anything else it needs, it shades into its jar or ships as more jars in `plugins/`. The
distribution's own `plugins/` holds the YAML store, the audit log and the Prometheus and Loki connectors (the demo
distribution adds the demo connector); the
console refuses to start without a store and an audit sink, and the connectors you do not use can go.

## Security

Operators log in against JAAS. Point `staffix.admin.security.jaas-config` at a JAAS configuration whose login
context is `staffix-admin` (or use the JVM's `-Djava.security.auth.login.config`). Roles are `READER`, `WRITER`
(implies `READER`) and `ENGINE`; map your JAAS principals to them with `staffix.admin.security.role-mappings`, e.g.

```yaml
staffix:
  admin:
    security:
      jaas-config: file:/etc/staffix-admin/jaas.conf
      role-mappings:
        fix-ops: [WRITER]
        fix-support: [READER]
```

Every state-changing action, and every refused one, is an audit entry: who, what, the session when it is one, the
outcome and why it failed. By default entries are logged on the `staffix.admin.audit` logger, so the trail is as
durable as your logging. To keep it elsewhere (a file of its own, a database, a SIEM), define a bean implementing
`AuditSink` from `staffix-admin-audit-spi`; define a `LoggerAuditSink` bean too to keep the log lines.

## UI development with hot reload

1. Run `DevelopmentApplication` (in the server's test sources, so on the test class path holding the plugins and the
   development users) from the IDE in the repository root, port 8080, after
   `[ -f data/connectors.yaml ] || { mkdir -p data && cp staffix-admin-demo/staffix-admin-simulator/connectors.yaml data/; }`; and the
   simulator (`./mvnw exec:java -pl :staffix-admin-simulator`) for engines.
2. Start the UI dev server, which first regenerates the TypeScript types from the running backend:

   ```shell
   cd staffix-admin-ui
   PATH=$PWD/node:$PATH npm run dev
   ```

Open http://localhost:5173. Vite proxies `/api` to the backend. After changing a controller or a DTO, restart the
backend and run `npm run generate-api:live`.
