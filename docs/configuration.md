# Configuration

The console is a Spring Boot application: settings come from `config/application.yml` in the directory it starts
in, from the command line (`--staffix.admin.data-dir=...`) or from the environment (`STAFFIX_ADMIN_DATADIR`). What
admins change at runtime, connectors and trace settings, lives in `console.yaml` instead: see
[Connectors](connectors.md).

## Console

| Setting | Default | |
|---|---|---|
| `staffix.admin.data-dir` | `data` | Where `console.yaml` is kept, relative to the working directory |
| `staffix.admin.engine-heartbeat-timeout` | `90s` | An engine silent for longer is shown unreachable; several times the engines' announce interval, so one lost announcement does not flag it |
| `staffix.admin.engine-request-timeout` | `5s` | How long a call to an engine may take, connecting included, before the operator is told it did not answer |
| `staffix.admin.engine-ssl-bundle` | | A Spring Boot SSL bundle whose truststore trusts the engines' HTTPS certificates; unset uses the JVM's default truststore |
| `staffix.admin.require-engine-tls` | `false` | Refuses engines announcing a plain `http` URL; turn it on in production |

## Security

| Setting | Default | |
|---|---|---|
| `staffix.admin.security.jaas-config` | | The JAAS login configuration; unset uses the JVM's (`-Djava.security.auth.login.config`) |
| `staffix.admin.security.login-context-name` | `staffix-admin` | The login context of that configuration |
| `staffix.admin.security.role-mappings` | | Roles granted to JAAS principals by name, e.g. an LDAP group: `fix-ops: [WRITER]` |

See [Security](security.md) for the roles and TLS.

## Secrets

| Setting | Default | |
|---|---|---|
| `staffix.admin.secrets.master-key` | | Base64 encoded 32 bytes encrypting the credentials typed in the console; usually the `STAFFIX_ADMIN_SECRETS_MASTERKEY` environment variable |
| `staffix.admin.secrets.master-key-file` | | A file holding the master key, such as a mounted secret |
| `staffix.admin.secrets.previous-master-keys` | | Keys replaced by the current one: they still decrypt, and the next save re-encrypts under the current key |

See [Credentials](connectors.md#credentials).

## Server

Spring Boot's own settings apply, such as `server.port` (default `8080`), `server.ssl.bundle` and
`server.forward-headers-strategy`: see [TLS](security.md#tls).
