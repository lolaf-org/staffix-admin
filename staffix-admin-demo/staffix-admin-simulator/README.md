# staffix-admin simulator

Three simulated engines (`paris-prod`, `london-prod`, `nyc-uat`) for demos and UI work. Each implements staffix's
`AdminApi` and is served by staffix's own HTTP admin API connector, so the console meets them exactly as it meets
real engines: they announce themselves, answer `status`, `settings`, `components` and dictionaries, and refuse what
staffix refuses. Sessions log on, drop, reconnect, follow session windows and can be switched to their backups.

Their metrics and FIX logs are generated and served on a separate port for the console's `demo-metrics` and
`demo-logs` connectors, which the demo distribution (`staffix-admin-dist-demo`) declares in `data/console.yaml`.
Values derive from the instant and the session, so a chart shows the same data on each refresh. `nyc-uat` logs
through slf4j and has no monitoring plugin, so the console shows its telemetry as not collected; `paris-prod`'s
client acceptors measure no decoding, encoding or clock offset.

```shell
./mvnw install
./mvnw exec:java -pl :staffix-admin-simulator
# or without Maven, from the runnable jar the build produces
java -jar staffix-admin-demo/staffix-admin-simulator/target/staffix-admin-simulator.jar
```

Its `Dockerfile` builds an image from that jar; the demo's `compose.yaml` (see the demo's README) runs it next to the
console, with these properties in `JAVA_OPTS`.

| System property | Default | |
|---|---|---|
| `consoleUrl` | `http://localhost:8080` | where the engines announce themselves |
| `consoleUsername` / `consolePassword` | `engine` / `engine` | a console user with the `ENGINE` role |
| `adminApiPort` | `8687` | the engines' HTTP admin API (the sandbox uses 8686) |
| `demoPort` | `8688` | the generated metrics and logs (`/demo/metrics`, `/demo/logs`, `/demo/health`) |
