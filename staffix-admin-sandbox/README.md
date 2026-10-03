# staffix-admin sandbox

Three staffix engines trading with each other, announced to a locally running console, with their metrics in
Prometheus and their FIX logs in Loki. Like the console, it builds against the staffix snapshot (see the main README).

## Run

```shell
# 1. Prometheus, Loki and the OpenTelemetry collector (add --profile grafana for Grafana on :3000)
(cd staffix-admin-sandbox/docker && docker compose up -d)

# 2. The console, with the sandbox users (reader, writer, engine; password = name), reading the sandbox's
#    Prometheus and Loki through its connectors
./mvnw install
(cd staffix-admin-sandbox/docker && docker compose --profile console up -d console)
#    or from the working copy, with the connectors in data/ (git ignores it, so editing them changes no tracked file)
[ -f data/connectors.yaml ] || { mkdir -p data && cp staffix-admin-sandbox/connectors.yaml data/; }
java -jar staffix-admin-app/target/staffix-admin.jar --staffix.admin.security.jaas-config=classpath:jaas/dev.conf

# 3. The engines
./mvnw install -Psandbox -pl staffix-admin-sandbox
./mvnw exec:java -Psandbox -pl staffix-admin-sandbox
```

Open http://localhost:8080 and log in as `writer`. Stop with `docker compose --profile console down` in
`staffix-admin-sandbox/docker`; add `-v` to also forget the connectors the containerized console stored.

The containerized console runs the jar `./mvnw install` built, with the users of `docker/conf/console` and the
connectors of `connectors.yaml` (copied into its volume on first start). It uses host networking, so it reaches the
engines, Prometheus and Loki as the host does: Linux, or Docker Desktop with host networking enabled.

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
