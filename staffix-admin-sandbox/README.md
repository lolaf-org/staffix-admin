# staffix-admin sandbox

Three staffix engines trading with each other, announced to a locally running console, with their metrics in
Prometheus and their FIX logs in Loki (or Elasticsearch, or ClickHouse). Two of them store their messages in
Postgres and post their alerts to Prometheus Alertmanager. It builds with the console (see
[Building](../docs/development.md#building)).

## Run

```shell
# 1. Prometheus, Loki and the OpenTelemetry collector, with Postgres and Alertmanager (started with any profile)
(cd staffix-admin-sandbox/docker && docker compose --profile prometheus-loki up -d)

# 2. The console, with the sandbox users (reader, writer, engine; password = name), reading the sandbox's
#    Prometheus and Loki through its connectors
./mvnw install
(cd staffix-admin-sandbox/docker && docker compose --profile console-prometheus-loki up -d)
#    or from the working copy, with the connectors in data/ (git ignores it, so editing them changes no tracked file)
[ -f data/console.yaml ] || { mkdir -p data && cp staffix-admin-sandbox/console-prometheus-loki.yaml data/console.yaml; }
DIST=staffix-admin-dist/target/staffix-admin/staffix-admin
java -Dloader.path=$DIST/plugins -jar $DIST/staffix-admin.jar --staffix.admin.security.jaas-config=file:staffix-admin-sandbox/console/jaas.conf

# 3. The engines
./mvnw exec:java -pl staffix-admin-sandbox
```

Open http://localhost:8080 and log in as `writer`. Stop with
`docker compose --profile prometheus-loki --profile console-prometheus-loki down` in `staffix-admin-sandbox/docker`;
add `-v` to also forget the connectors the containerized console stored.

The containerized console runs the distribution `./mvnw install` built, with the users of `docker/conf/console` and the
connectors of `console-prometheus-loki.yaml` (copied into its volume on first start). It uses host networking, so it
reaches the engines, Prometheus and Loki as the host does: Linux, or Docker Desktop with host networking enabled.

### Elasticsearch and Kibana

The `kibana` profile starts Elasticsearch, Kibana on :5601 and a second OpenTelemetry collector on port 4320, which
writes logs to the `logs-generic.otel-default` data stream and metrics to `metrics-generic.otel-default` (histograms
converted to delta, the only temporality Elasticsearch stores, and timers' `.max` gauges renamed `_max`, so the timers
map as histograms). Point the engines at it instead of the default collector:

```shell
(cd staffix-admin-sandbox/docker && docker compose --profile kibana up -d)
./mvnw exec:java -pl staffix-admin-sandbox -DotlpUrl=http://localhost:4320
```

In Kibana, Discover the `logs-*` or `metrics-*` data views. The `console-es` profile runs the console with the
connectors of `console-es.yaml`, which read Elasticsearch by default (instead of `console-prometheus-loki`: both
listen on 8080). They need the distribution's `staffix-admin-logs-elasticsearch` and
`staffix-admin-metrics-elasticsearch` plugins, with `staffix-admin-logs-elasticsearch-common`, all in its `plugins/`:

```shell
(cd staffix-admin-sandbox/docker && docker compose --profile console-es up -d)
# or from the working copy (data/console.yaml replaced: keep a copy if you edited it)
mkdir -p data && cp staffix-admin-sandbox/console-es.yaml data/console.yaml
java -Dloader.path=$DIST/plugins -jar $DIST/staffix-admin.jar --staffix.admin.security.jaas-config=file:staffix-admin-sandbox/console/jaas.conf
```

Stop with `docker compose --profile kibana --profile console-es down`; add `-v` to also drop the indexed data and the
connectors this console stored.

### ClickHouse

The `clickhouse` profile starts ClickHouse (HTTP on :8123, user and password `otel`, database `otel`) and a third
OpenTelemetry collector on port 4321, whose `clickhouse` exporter creates the `otel_logs`, `otel_traces` and
`otel_metrics_<type>` tables. The `console-clickhouse` profile runs the console with the connectors of
`console-clickhouse.yaml`, which read ClickHouse by default (instead of the other consoles: all listen on 8080):

```shell
(cd staffix-admin-sandbox/docker && docker compose --profile clickhouse up -d)
./mvnw exec:java -pl staffix-admin-sandbox -DotlpUrl=http://localhost:4321
(cd staffix-admin-sandbox/docker && docker compose --profile console-clickhouse up -d)
# or from the working copy (data/console.yaml replaced: keep a copy if you edited it)
mkdir -p data && cp staffix-admin-sandbox/console-clickhouse.yaml data/console.yaml
java -Dloader.path=$DIST/plugins -jar $DIST/staffix-admin.jar --staffix.admin.security.jaas-config=file:staffix-admin-sandbox/console/jaas.conf
# to query the tables yourself
(cd staffix-admin-sandbox/docker && docker compose exec clickhouse clickhouse-client --user otel --password otel --database otel)
```

Stop with `docker compose --profile clickhouse --profile console-clickhouse down`; add `-v` to also drop the data and
the connectors this console stored.

## What runs

| engine | initiates to | accepts from |
|---|---|---|
| `alpha-bank` | `venue-x.trading` (backup `trading-drp`, whose address nobody listens on), `venue-y.trading`, `venue-y.fixt-trading`, `venue-y.market-data` | `venue-x.drop-copy` |
| `venue-x` | `alpha-bank.drop-copy` | `alpha-bank.trading` |
| `venue-y` | | `alpha-bank.trading`, `alpha-bank.fixt-trading`, `alpha-bank.market-data`, `alpha-bank.end-of-day` (outside its window), `gamma-fund.trading` (never connects) |

The bank asks for quotes every 200 ms and the venues answer; on one quote in five it buys at the offer with a
NewOrderSingle naming the quote, which the venue fills, and `venue-x` copies its fills on the drop copy. Tracing a
QuoteReqID (e.g. `venue-x-195`) in the Trace tab finds the request, the quote, the order, its fill and the drop copy,
on both engines. `venue-y` publishes News on `market-data` and `venue-x` on the drop copy. `fixt-trading` is FIXT.1.1 with FIX 5.0 SP2 application messages; every other session is FIX 4.4.
The applications publish their own meters through staffix's monitoring: the bank times `quote.round.trip` (a
QuoteRequest until its Quote, tag `venue`) and reads `quotes.pending` from its requests in flight (a gauge read at each
export); the venues time `quote.pricing` and set `quote.last.mid` on every quote (a gauge they set). All three engines share HTTP admin API port 8686. System properties `consoleUrl`,
`consoleUsername`, `consolePassword`, `adminApiPort` and `otlpUrl` override the defaults.

`venue-x` stores its messages in Postgres through staffix's JDBC store, and `venue-y` through the async store in
front of the JDBC store; `alpha-bank` keeps them in memory. Each venue has its own schema (`venue_x`, `venue_y`) in
the `staffix` database, recreated at each start from the DDL the build generates with staffix's DDL plugin. Both
venues post their alerts to Alertmanager, labelled `env=sandbox` (its UI on http://localhost:9093, which notifies
nobody); `alpha-bank` logs them. Stopping Postgres (`docker compose stop postgres`) raises `messages-store` on
`venue-x`'s sessions, a warning that turns major once the store's check finds the database down: their sent messages
are no longer stored, but each sequence number update still waits for the pool's 2 second timeout, so a busy session
can drop and log on again. `venue-y`'s sessions keep trading on the async store's queue and raise
`async-store-resource` instead. Once Postgres is back (`docker compose start postgres`), the alerts clear after 30
seconds. Stopping the OpenTelemetry collector (`docker compose stop otel-collector`) raises `messages-logger` on
every session the same way, cleared 30 seconds after it is started again.
System properties `jdbcUrl`, `jdbcUsername`, `jdbcPassword` and `alertmanagerUrl` override the defaults.

`venue-x-gateway`'s sessions live in Postgres too, in `venue_x.fix_sessions_settings` through staffix's JDBC session
settings store: one row per session, its settings as JSON. Unlike the messages tables, that table is kept across runs,
so sessions added, edited or removed from the console survive a restart. The first start seeds it with the gateway's
sessions, through the store itself; later starts read whatever the table holds, and seed it again only once it is
empty. Edit a row by hand and reload the store from the console to see the change reach the engine:

```shell
(cd staffix-admin-sandbox/docker && docker compose exec postgres psql -U staffix -c \
  "select qualified_name, session_type, version, updated_at from venue_x.fix_sessions_settings")
# back to the seeded sessions at the next start
(cd staffix-admin-sandbox/docker && docker compose exec postgres psql -U staffix -c "drop table venue_x.fix_sessions_settings")
```

Both sides of each connection are separate engines: switching `alpha-bank`'s `venue-x.trading` to its backup turns
it red, and logging a session out on one side shows the other side lose it.
