# Development

## Modules

| Module | Content |
|---|---|
| `staffix-admin-server` | Spring Boot backend: REST API for the UI, engine proxy. Its controllers are the API contract |
| `staffix-admin-ui` | React + Ant Design UI |
| `staffix-admin-app` | The executable jar: the server with the UI inside |
| `staffix-admin-metrics` | `staffix-admin-metrics-spi`, the SPI for metrics backends; `staffix-admin-metrics-prometheus`, `staffix-admin-metrics-elasticsearch` and `staffix-admin-metrics-clickhouse`, its plugins |
| `staffix-admin-logs` | `staffix-admin-logs-spi`, the SPI for logs backends; `staffix-admin-logs-loki`, `staffix-admin-logs-elasticsearch` and `staffix-admin-logs-clickhouse`, its plugins; `staffix-admin-logs-elasticsearch-common` and `staffix-admin-logs-clickhouse-common`, the client each pair of plugins shares |
| `staffix-admin-store` | `staffix-admin-store-spi`, where the console configuration is kept; `staffix-admin-store-yaml`, a YAML file |
| `staffix-admin-audit` | `staffix-admin-audit-spi`, where audit entries go; `staffix-admin-audit-log`, log lines |
| `staffix-admin-connector-spi` | What a backend plugin declares about its settings: descriptions, secrets, secret references |
| `staffix-admin-dist` | The distribution: the console's jar, its plugins and an example configuration; a Docker image |
| `staffix-admin-demo` | Simulated engines and the distribution reading their telemetry: see its [README](../staffix-admin-demo/README.md) |
| `staffix-admin-sandbox` | Real staffix engines trading with each other, with Prometheus, Loki, Elasticsearch or ClickHouse (profile `sandbox`): see its [README](../staffix-admin-sandbox/README.md) |

## Building

`./mvnw install` builds everything. The build downloads its own Node into `staffix-admin-ui/node/`;
no system Node is needed. The `staffix-admin-sandbox` module builds only with `-Psandbox`.

Every source file carries the Apache license header of `HEADER.txt`; the build fails without it, and
`./mvnw license:format` adds it.

## API contract

The Java controllers and DTOs are the source of truth. springdoc derives the OpenAPI spec from them, and the UI's
TypeScript types are generated from that spec, so changing a DTO breaks the UI compilation wherever it no longer
matches. A DTO field is required unless annotated JSpecify `@Nullable`.

The spec is never written by hand nor committed: the Maven build starts the server briefly and saves it to
`staffix-admin-server/target/openapi/admin-api.json`; a running server serves it at `/v3/api-docs`.

## UI development with hot reload

1. Run `DevelopmentApplication` (in the server's test sources, so on the test class path holding the plugins and the
   development users) from the IDE in the repository root, port 8080, after
   `[ -f data/console.yaml ] || { mkdir -p data && cp staffix-admin-demo/staffix-admin-dist-demo/src/data/console.yaml data/; }`;
   and the simulator (`./mvnw exec:java -pl :staffix-admin-simulator`) for engines.
2. Start the UI dev server, which first regenerates the TypeScript types from the running backend:

   ```shell
   cd staffix-admin-ui
   PATH=$PWD/node:$PATH npm run dev
   ```

Open http://localhost:5173. Vite proxies `/api` to the backend. After changing a controller or a DTO, restart the
backend and run `npm run generate-api:live`.

## Trying it

- The [demo](../staffix-admin-demo/README.md): simulated engines with generated metrics and logs, no backend to
  install.
- The [sandbox](../staffix-admin-sandbox/README.md): real staffix engines exporting to Prometheus, Loki,
  Elasticsearch or ClickHouse through an OpenTelemetry collector.
