# Connectors

The console reads the engines' metrics and FIX logs from backends, never from the engines: a connector describes one
backend. A connector has an id, a name, a kind and that kind's settings; there can be several of a kind, e.g. one
Loki per environment.

| Kind | Reads |
|---|---|
| `prometheus` | Metrics from Prometheus |
| `elasticsearch-metrics` | Metrics from Elasticsearch |
| `loki` | FIX logs from Loki |
| `elasticsearch-logs` | FIX logs from Elasticsearch |
| `clickhouse-metrics` | Metrics from ClickHouse |
| `clickhouse-logs` | FIX logs from ClickHouse |

Each backend needs some setup on the OpenTelemetry collector or the backend itself: see [Backends](backends.md).
Plugins can add kinds: see [Plugins](plugins.md).

## Which connector a session reads

A session's logs go through one of its engine's messages loggers and its metrics through one of its monitoring
plugins, as the engine reports. Each of these emitters reads from the connector assigned to it, else from its
engine's `defaults`, else from the global `defaults`. Telemetry with no connector, or `none`, shows as not collected,
and so do the metrics of a session without a monitoring plugin. A view spanning several backends reads each and merges
them; a latency quantile of a message type then shows the highest of the backends'. The console creates no connector
itself.

The console also reads how each engine emits (staffix's `GET components`): the timer unit of its monitoring plugin
and how often it exports, and whether a messages logger exports anything a backend reads (a slf4j or file logger does
not, so its sessions' logs show as not collected).

## console.yaml

Connectors live in the console configuration, `console.yaml` under the data directory (`staffix.admin.data-dir`,
default `data`, relative to the working directory), readable by the console's user only. Writers manage them in the
Connectors tab: connectors with a form per kind and a connection test, the defaults, and each engine's emitters with
the sessions using them. The file can also be edited by hand; changes apply without a restart. It also holds the
[trace settings](logs-and-trace.md#trace-settings). An editor can check it against the schema the distribution ships:
see [Editing the YAML files](configuration.md#editing-the-yaml-files).

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
  - id: prod-clickhouse-logs
    name: Production ClickHouse logs
    kind: clickhouse-logs
    settings:
      url: http://clickhouse:8123   # the HTTP interface
      database: otel                # the default, with table otel_logs
      username: fix_reader
      password: ${env:CLICKHOUSE_PASSWORD}
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

The Connectors tab's form describes each kind's settings.

## Credentials

A setting may be a reference resolved when the connector is built: `${env:NAME}` reads an environment variable,
`${file:/run/secrets/name}` a mounted secret file (without its trailing newline). A plugin can add schemes, such as
`${vault:...}`.

Credentials, such as Loki's `tenant`, are never shown by the console: give them as a reference, or let the console
store them encrypted (`enc:v1:...`, AES-GCM) under a master key, base64 encoded 32 bytes (`openssl rand -base64 32`)
given as `staffix.admin.secrets.master-key` (env `STAFFIX_ADMIN_SECRETS_MASTERKEY`) or `master-key-file`. Without a
master key, the console refuses to save a credential that is not a reference.

To rotate the key, set the new one and move the old one to `staffix.admin.secrets.previous-master-keys`; each save
re-encrypts under the new key. A lost key means typing those credentials again.
