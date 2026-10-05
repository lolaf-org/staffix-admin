# staffix-admin demo

The console without real engines: three simulated engines whose sessions drop, reconnect and follow session windows,
and generated metrics and FIX logs. Their meters are described by staffix's own monitoring, and sessions not serving
clients carry an application timer, `orders.pricing`, recorded per `venue`.

| Module | |
|---|---|
| `staffix-admin-simulator` | The simulated engines, a separate process seen by the console as real engines (see its README); a Docker image |
| `staffix-admin-connector-demo` | The `demo-metrics` and `demo-logs` connector kinds, reading the simulator's telemetry |
| `staffix-admin-dist-demo` | The console's distribution plus the demo connector, demo users and connectors to the simulator, as `target/staffix-admin-demo.zip`; a Docker image |

## Run it with Docker

```shell
./mvnw install                  # in the repository root
cd staffix-admin-demo
docker compose up
```

Open http://localhost:8080 and log in as `reader` or `writer` (password = name). The images are rebuilt on each
`up` from what `./mvnw install` produced. `docker compose down -v` also forgets the connectors the console
stored in its volume.

## Run it without Docker

From the repository root, with the demo distribution, whose `data/` already holds the connectors to the simulator:

```shell
(cd staffix-admin-demo/staffix-admin-dist-demo/target/staffix-admin-demo/staffix-admin && java -jar staffix-admin.jar) &
java -jar staffix-admin-demo/staffix-admin-simulator/target/staffix-admin-simulator.jar
```

The simulator announces to `http://localhost:8080` by default; see its README for its ports and settings.
