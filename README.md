# staffix-admin

HTTP admin console for [staffix](https://github.com/lolaf-org/staffix) FIX engines: a Spring Boot backend that
serves a React UI and proxies the engines' HTTP admin connectors. The browser never talks to an engine directly.

## Modules

| Module | Content |
|---|---|
| `staffix-admin-server` | Spring Boot backend: REST API for the UI, engine proxy. Its controllers are the API contract |
| `staffix-admin-ui` | React + Ant Design UI |
| `staffix-admin-app` | The executable jar: the server with the UI inside |
| `staffix-admin-metrics` | `staffix-admin-metrics-spi`, the SPI for metrics backends; `staffix-admin-metrics-prometheus`, metrics from Prometheus; `staffix-admin-metrics-elasticsearch`, metrics from Elasticsearch |
| `staffix-admin-logs` | `staffix-admin-logs-spi`, the SPI for logs backends; `staffix-admin-logs-loki`, FIX message logs from Loki; `staffix-admin-logs-elasticsearch`, FIX message logs from Elasticsearch; `staffix-admin-logs-elasticsearch-common`, the Elasticsearch client both Elasticsearch plugins use |
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
(`staffix-admin-api-http-impl`, see staffix's runtime administration guide) with its `announcement` URL pointing
here and the credentials of a user with the `ENGINE` role. The console then calls each engine with the token it announced.

### Docker image

`staffix-admin-dist/Dockerfile` packages the distribution, running as a non-root user:

```shell
./mvnw install
docker build -t staffix-admin staffix-admin-dist
docker run -p 8080:8080 \
    -v /etc/staffix-admin:/opt/staffix-admin/config:ro \
    -v staffix-admin-data:/data \
    -e STAFFIX_ADMIN_SECRETS_MASTERKEY=... \
    staffix-admin
```

- `config/` holds the image's copy of the distribution's: as shipped it lets nobody in. Mount your own in its place,
  starting from `staffix-admin-dist/src/config/`: `application.yml`, `jaas.conf` and your users or JAAS module. Hash
  a password with the image itself:
  `docker run --rm --entrypoint sh staffix-admin -c "java -Dloader.main=org.lolaf.staffix.admin.server.security.jaas.PasswordHasher -jar staffix-admin.jar 'secret'"`.
- `/data` is the data directory: `connectors.yaml` lives there, so keep it on a volume.
- `JAVA_OPTS` passes JVM options; any setting can also come from the environment
  (`staffix.admin.engine-request-timeout` as `STAFFIX_ADMIN_ENGINEREQUESTTIMEOUT`).
- Plugins of your own: build on the image, `FROM staffix-admin` then `COPY my-plugin.jar plugins/`, rather than
  mounting over `plugins/`, which would hide the store and audit log the console needs.
- The console serves plain HTTP until told otherwise: see [TLS](#tls).

To try the console without real engines, run the demo (simulated engines, generated metrics and FIX logs, demo
users): `./mvnw install`, then `docker compose up` in `staffix-admin-demo`. See `staffix-admin-demo/README.md`,
which also runs it without Docker.

## Session settings

A session's Settings tab shows its settings as the engine stores them, in a form built from the engine's own session
settings schema: each field's tooltip gives its name as files and the API write it and what it does, and an unset
field shows the default the engine applies. Writers edit and save them; the engine restarts the session on them
unless its "Restart live session on update" is off.

- Writers add acceptor sessions from an engine's card ("Acceptor session"), blank or starting from another acceptor
  session's settings, into one of the engine's session settings stores, and remove acceptor sessions from their Status
  tab.
  An initiator's sessions are the targets it was built with: the console neither adds nor removes them, nor changes
  their identity.
- A store backed by files keeps the change; one held in memory loses it when the engine restarts, which the tab and
  the store picker say.
- Application settings the session's application declares are listed with their descriptions; a secret one is typed
  blind and left empty keeps its value. Certificates stay as the engine's files configure them.
- A field a session file sets through a `${...}` placeholder keeps it when its value is not changed; changing it is
  refused, as its source owns it.
- Every change is audited by the names of the fields it changed, never their values.

## Metrics and logs

The console reads engines' metrics and FIX logs from backends described by connectors. A connector has an id, a
name, a kind (`prometheus` or `elasticsearch-metrics` for metrics, `loki` or `elasticsearch-logs` for logs) and that
kind's settings; there can be several of a kind, e.g. one Loki per environment. A session's logs go through one of its engine's messages loggers and its metrics
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
      minRateWindow: 10s            # two scrape intervals (default 20s)
  - id: prod-loki
    name: Production Loki
    kind: loki
    settings:
      url: http://loki:3100
      indexedLabels: [ fix_eid, fix_sg, fix_sn ]   # labels Loki indexes, matched in the stream selector
      streamSelector: '{service_name="fix-gateway"}'  # default {service_name=~".+"}
      tenant: ${env:LOKI_TENANT}   # X-Scope-OrgID, for a multi-tenant Loki
  - id: prod-elasticsearch-logs
    name: Production Elasticsearch logs
    kind: elasticsearch-logs
    settings:
      url: https://elasticsearch:9200
      index: logs-*.otel-*          # the default
      apiKey: ${env:ES_API_KEY}     # or username and password
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

The console also reads how each engine emits (staffix's `GET components`): the timer unit of its monitoring plugin
and how often it exports, and whether a messages logger exports anything a backend reads (a slf4j or file logger does
not, so its sessions' logs show as not collected).

The Metrics tab offers the meters the scope's running sessions publish, as their engine describes them (staffix's
`GET meters`), each with what it measures: staffix's own (message latencies, round-trip time, clock offset, logon
state) and the timers the session's application created with `FixSessionsMonitoringContext.getTimer`, split by their
own tags. An application's timer shows once the application asked for it in that session. A timer reads as a
quantile (when it publishes its percentile histogram), a mean or a rate; a gauge as its value.

**Prometheus** charts read the engines' micrometer meters received through the OpenTelemetry collector with
`translation_strategy: UnderscoreEscapingWithoutSuffixes` (as in `staffix-admin-sandbox/docker`): a meter or a tag
named with dots or dashes reads with underscores. RTT and clock offset need staffix's RTT measurement enabled.

A rate needs two samples in the window it is averaged over, so the data sets the finest window a chart can show:
twice the engine's OTLP export `step` (staffix default 10 s), and twice the Prometheus scrape interval when
Prometheus scrapes the collector's `prometheus` exporter. The console knows the first from the engine; set the
connector's `minRateWindow` to the second (with Prometheus's own OTLP receiver there is no scrape: set it no higher
than twice the export step). Scrape faster than the engines export, e.g. every 2 s for a 5 s step: the collector keeps
only each meter's latest export, so a scrape at the same pace misses exports and leaves too few samples for the
narrowest windows, which then chart nothing. The Metrics tab's rate window cannot go under either, and a chart steps
down to a point a second over a period of a few minutes. To see second by second, an engine must export every second.

**Loki** holds what staffix's OTLP messages logger sends: every message in and out, and session events. Loki keeps
the `fix.*` attributes as structured metadata, so by default the console filters them after a stream selector that
scans every stream of the time range. Promote `fix.eid`, `fix.sg` and `fix.sn` to index labels in Loki's
`otlp_config` (as `staffix-admin-sandbox/docker/conf/loki.yaml` does) and list them in `indexedLabels`, so a query
reads only the streams of its engine, group or session.

**Elasticsearch** connectors read what the OpenTelemetry collector's `elasticsearch` exporter writes with its OTel
mapping (the default), into the `metrics-*.otel-*` and `logs-*.otel-*` data streams (each connector's `index`). A
`fix.*` attribute is matched by its own name, whether staffix sent it as a resource or a record attribute. The
collector needs two processors on the metrics pipeline (as in
`staffix-admin-sandbox/docker/conf/otel-collector-elastic.yaml`):

- `cumulative_to_delta` on histograms: Elasticsearch stores only delta histograms, and drops the cumulative ones
  micrometer exports.
- A rename of micrometer's `<timer>.max` gauges, e.g. to `<timer>_max` with `metrics_transform`: when Elasticsearch
  maps `metrics.<timer>.max` before `metrics.<timer>`, the timer is no longer mapped as a histogram and cannot be read.

Points are computed per step: a rate from the recordings of the buckets in its window, a mean from their sum and
count, a quantile once per window from the histogram (so a window wider than the step gives fewer points), a gauge's
value as its average over the step. A mean or a quantile is approximated from the histogram's buckets, and a timer
without a histogram (an `aggregate_metric_double`) has a rate and a mean but no quantile. Set `minRateWindow` to twice
the engines' export step. A logs text filter matches whole words of the message, case insensitive (`body.text` is a
text field), rather than any substring.

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
distribution's own `plugins/` holds the YAML store, the audit log and the Prometheus, Loki and Elasticsearch connectors (the demo
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

Engines announce themselves with an `ENGINE` user's credentials. An engine already registered that announces another
URL or token is accepted only if it answers there with that token and, when its URL changed, no longer answers at
the old one: so an `ENGINE` user cannot point a live engine's operations at another server, while an engine
restarted with a new token, or moved, is taken at once. An engine id never seen, or one whose engine is down, is
accepted as announced: keep the `ENGINE` password as secret as an operator's.

Every state-changing action, and every refused one, is an audit entry (`AuditEntry`): when, who, what, the outcome
and why it failed, and the engine, group and session when it is a session's. It never holds a secret: a connector
change names what changed, not the values. The distribution's `staffix-admin-audit-log` plugin logs each entry on
the `staffix.admin.audit` logger (INFO when it succeeded, WARN when it failed), so the trail is as durable as your
logging.

The console ships no other sink: to keep the trail elsewhere (a file of its own, a database, a SIEM), write one as a
[plugin](#plugins):

1. Implement `AuditSink` from `staffix-admin-audit-spi` (`provided`). `record(AuditEntry)` is called after the
   action, on the request's thread: keep it short, or hand the entry to a thread of your own. A sink that throws is
   logged and neither stops the other sinks nor undoes the action, which already happened, so a sink that must not
   lose entries buffers or retries itself.
2. Declare it as a bean in an `@AutoConfiguration` class, named in your jar's
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
3. Drop the jar into `plugins/`. Every sink receives every entry: keep `staffix-admin-audit-log` next to yours to
   keep the log lines too, or remove it. The console refuses to start without any sink.

### TLS

Two links carry credentials: browsers and engines to the console (logins, session cookies, engine announcements
with their tokens), and the console to the engines (each engine's bearer token, on every call).

The console serves HTTPS with a Spring Boot SSL bundle; behind a TLS-terminating proxy instead, let it trust the
proxy's forwarded headers so it knows requests came in over HTTPS and marks its session cookie secure:

```yaml
spring:
  ssl:
    bundle:
      pem:
        console:
          keystore:
            certificate: file:/etc/staffix-admin/tls/console.crt
            private-key: file:/etc/staffix-admin/tls/console.key
        engines:
          truststore:
            certificate: file:/etc/staffix-admin/tls/engines-ca.crt
server:
  port: 8443
  ssl:
    bundle: console
  # behind a proxy, instead of server.ssl:
  # forward-headers-strategy: native
staffix:
  admin:
    engine-ssl-bundle: engines
    require-engine-tls: true
```

- `staffix.admin.engine-ssl-bundle`: the bundle whose truststore the console trusts engines' HTTPS certificates with;
  unset uses the JVM's default truststore.
- `staffix.admin.require-engine-tls`: refuses an engine announcing a plain `http` URL (400, logged). Off by default
  so the demo and a local setup work without certificates; turn it on in production.

On the engine side, staffix's HTTP admin connector serves HTTPS with `sslContext` (Spring Boot
`staffix.admin-api-http.ssl-bundle`) and trusts an HTTPS console with its announcement's `sslContext`
(`staffix.admin-api-http.announce-ssl-bundle`), the JVM's default truststore otherwise. Client certificates are not
supported yet.

## UI development with hot reload

1. Run `DevelopmentApplication` (in the server's test sources, so on the test class path holding the plugins and the
   development users) from the IDE in the repository root, port 8080, after
   `[ -f data/connectors.yaml ] || { mkdir -p data && cp staffix-admin-demo/staffix-admin-dist-demo/src/data/connectors.yaml data/; }`; and the
   simulator (`./mvnw exec:java -pl :staffix-admin-simulator`) for engines.
2. Start the UI dev server, which first regenerates the TypeScript types from the running backend:

   ```shell
   cd staffix-admin-ui
   PATH=$PWD/node:$PATH npm run dev
   ```

Open http://localhost:5173. Vite proxies `/api` to the backend. After changing a controller or a DTO, restart the
backend and run `npm run generate-api:live`.
