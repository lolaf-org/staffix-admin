# staffix-admin

HTTP admin console for [staffix](https://github.com/lolaf-org/staffix) FIX engines: a Spring Boot backend that
serves a React UI and proxies the engines' HTTP admin connectors. The browser never talks to an engine directly.

## Modules

| Module | Content |
|---|---|
| `staffix-admin-server` | Spring Boot backend: REST API for the UI, engine proxy. Its controllers are the API contract |
| `staffix-admin-ui` | React + Ant Design UI |
| `staffix-admin-app` | The executable jar: the server with the UI inside |
| `staffix-admin-metrics-spi` | SPI for metrics backends (Prometheus, ...) |
| `staffix-admin-logs-spi` | SPI for logs backends (Loki, ...) |

## API contract

The Java controllers and DTOs are the source of truth. springdoc derives the OpenAPI spec from them, and the UI's
TypeScript types are generated from that spec, so changing a DTO breaks the UI compilation wherever it no longer
matches. A DTO field is required unless annotated JSpecify `@Nullable`.

The spec is never written by hand nor committed: the Maven build starts the server briefly and saves it to
`staffix-admin-server/target/openapi/admin-api.json`; a running server serves it at `/v3/api-docs`.

## Build and run

```shell
./mvnw install
java -jar staffix-admin-app/target/staffix-admin.jar
```

Open http://localhost:8080. The build downloads its own Node into `staffix-admin-ui/node/`; no system Node needed.

Until the real engine connector lands, the app runs on simulated engines (Spring profile `mock`, the default):
three engines whose sessions drop, reconnect and follow session windows, with generated metrics and FIX logs.
Development users, password = name: `reader`, `writer`, `engine` (announcement only).

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

Every state-changing action is logged on the `staffix.admin.audit` logger.

## UI development with hot reload

1. Run `StaffixAdminApplication` from the IDE (port 8080).
2. Start the UI dev server, which first regenerates the TypeScript types from the running backend:

   ```shell
   cd staffix-admin-ui
   PATH=$PWD/node:$PATH npm run dev
   ```

Open http://localhost:5173. Vite proxies `/api` to the backend. After changing a controller or a DTO, restart the
backend and run `npm run generate-api:live`.
