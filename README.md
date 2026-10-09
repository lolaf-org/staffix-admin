# staffix-admin

A web admin console for [staffix](https://github.com/lolaf-org/staffix) FIX engines: see every session of every
engine at a glance, control and configure them, and read their metrics and FIX logs, from one place.

## Main facts

- **One console, many engines.** Engines announce themselves through staffix's HTTP admin API connector; nothing to
  register by hand. The console calls each engine with the token it announced.
- **The browser only talks to the console.** Engine URLs and tokens never reach it.
- **Telemetry from your backends, not from the engines.** Metrics come from Prometheus, Elasticsearch or ClickHouse,
  FIX logs from Loki, Elasticsearch or ClickHouse, as staffix exports them through OpenTelemetry. Searching days of
  logs never loads an engine.
- **Pluggable.** Backends, secret schemes, the audit sink and the configuration store are plugins: jars dropped into
  `plugins/`.
- **Roles and audit.** Operators log in through JAAS as `READER` or `WRITER`; every change, and every refused one, is
  audited.
- **Stack.** Spring Boot 4 on Java 21, React with Ant Design; one executable jar, or a Docker image.

## Features

- **Sessions overview**: every session of every engine, coloured by status (logged in, outside session time, logged
  out by an operator, down within session time), with counts, filters and search.
- **Session control**: log on and out, set sequence numbers, sequence resets, switch an initiator to a backup.
- **Session settings**: edit a session's settings in a form built from the engine's own schema; add and remove
  acceptor sessions.
- **Send messages**: inject application messages into a session, one per line.
- **Availability**: a session's status over time and its availability within session time.
- **Metrics**: message rates and latencies, round-trip time, clock offset, and the application's own timers and
  gauges.
- **Logs**: search FIX messages by text, type and direction, decode them with the session's dictionary, export them
  as CSV.
- **Order trace**: follow an order by any of its ids across sessions and engines, from request to fill and drop copy.
- **Connectors**: declare the metrics and logs backends, and which sessions read which, from the UI.

## Quick start

The demo runs the console against three simulated engines with generated metrics and logs, nothing else to install:

```shell
git clone https://github.com/lolaf-org/staffix-admin.git
cd staffix-admin
./mvnw install -DskipTests
cd staffix-admin-demo
docker compose up
```

Open http://localhost:8080 and log in as `reader` or `writer` (password = name). Without Docker, see the
[demo's README](staffix-admin-demo/README.md).

To run it for your own engines, see [Installation](docs/installation.md); to try it with real engines, Prometheus,
Loki, Elasticsearch or ClickHouse, the [sandbox](staffix-admin-sandbox/README.md).

## Documentation

| Guide | |
|---|---|
| [Installation](docs/installation.md) | Distribution, users, Docker image, connecting engines |
| [Configuration](docs/configuration.md) | Every setting and its default |
| [Connectors](docs/connectors.md) | Metrics and logs backends, `console.yaml`, credentials |
| [Backends](docs/backends.md) | Prometheus, Loki, Elasticsearch, ClickHouse and OpenTelemetry collector setup |
| [Sessions](docs/sessions.md) | Statuses, control, settings, sending, availability |
| [Metrics](docs/metrics.md) | Meters, statistics, rate windows |
| [Logs and trace](docs/logs-and-trace.md) | Log search, decoding, CSV, order trace |
| [Security](docs/security.md) | Logins, roles, engine announcements, audit, TLS |
| [Plugins](docs/plugins.md) | Adding backends, secret schemes, audit sinks, stores |
| [Development](docs/development.md) | Modules, build, API contract, UI hot reload |
