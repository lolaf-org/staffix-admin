# staffix-admin sandbox

Three staffix engines trading with each other, announced to a locally running console, with their metrics in
Prometheus and their FIX logs in Loki. It builds against the staffix snapshot (`0.9.1-SNAPSHOT`), so install staffix
locally first (`./mvnw install` in the staffix repository).

## Run

```shell
# 1. Prometheus, Loki and the OpenTelemetry collector (add --profile grafana for Grafana on :3000)
(cd staffix-admin-sandbox/docker && docker compose up -d)

# 2. The console, with the development users (reader, writer, engine; password = name)
./mvnw install
java -jar staffix-admin-app/target/staffix-admin.jar --staffix.admin.security.jaas-config=classpath:jaas/dev.conf \
  --staffix.admin.metrics.prometheus.url=http://localhost:9090 \
  --staffix.admin.metrics.prometheus.timer-base-unit=microseconds

# 3. The engines
./mvnw install -Psandbox -pl staffix-admin-sandbox
./mvnw exec:java -Psandbox -pl staffix-admin-sandbox
```

Open http://localhost:8080 and log in as `writer`. Stop with `docker compose down` in `staffix-admin-sandbox/docker`.

## What runs

| engine | initiates to | accepts from |
|---|---|---|
| `alpha-bank` | `venue-x.trading` (backup `trading-drp`, whose address nobody listens on), `venue-y.trading`, `venue-y.market-data` | `venue-x.drop-copy` |
| `venue-x` | `alpha-bank.drop-copy` | `alpha-bank.trading` |
| `venue-y` | | `alpha-bank.trading`, `alpha-bank.market-data`, `alpha-bank.end-of-day` (outside its window), `gamma-fund.trading` (never connects) |

The bank asks for quotes every 200 ms and the venues answer; `venue-y` publishes News on `market-data` and `venue-x`
on the drop copy. All three engines share HTTP admin API port 8686. System properties `consoleUrl`,
`consoleUsername`, `consolePassword`, `adminApiPort` and `otlpUrl` override the defaults.

Both sides of each connection are separate engines: switching `alpha-bank`'s `venue-x.trading` to its backup turns
it red, and logging a session out on one side shows the other side lose it.
