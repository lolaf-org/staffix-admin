# Security

## Logins and roles

Operators log in against JAAS. Point `staffix.admin.security.jaas-config` at a JAAS configuration whose login
context is `staffix-admin` (or use the JVM's `-Djava.security.auth.login.config`). The distribution's
`config/jaas.conf` reads `config/users.properties`; replace that module with your own (LDAP...) to log in against
your directory.

| Role | |
|---|---|
| `READER` | Sees everything |
| `WRITER` | Also changes: session control, settings, sending messages, connectors (implies `READER`) |
| `ENGINE` | Only announces engines; cannot open the UI |

Map your JAAS principals to roles with `staffix.admin.security.role-mappings`:

```yaml
staffix:
  admin:
    security:
      jaas-config: file:/etc/staffix-admin/jaas.conf
      role-mappings:
        fix-ops: [WRITER]
        fix-support: [READER]
```

## Engine announcements

Engines announce themselves with an `ENGINE` user's credentials and a bearer token the console then calls them with.
An engine already registered that announces another URL or token is accepted only if it answers there with that
token and, when its URL changed, no longer answers at the old one: so an `ENGINE` user cannot point a live engine's
operations at another server, while an engine restarted with a new token, or moved, is taken at once. An engine id
never seen, or one whose engine is down, is accepted as announced: keep the `ENGINE` password as secret as an
operator's.

The browser only ever talks to the console: engine URLs and tokens never reach it.

## Audit trail

Every state-changing action, and every refused one, is an audit entry: when, who, what, the outcome and why it failed,
and the engine, group and session when it is a session's. It never holds a secret: a connector change names what
changed, not the values. The distribution's `staffix-admin-audit-log` plugin logs each entry on the
`staffix.admin.audit` logger (INFO when it succeeded, WARN when it failed), so the trail is as durable as your
logging. To keep it elsewhere (a database, a SIEM), [write a sink](plugins.md#writing-an-audit-sink).

## TLS

Two links carry credentials: browsers and engines to the console (logins, session cookies, engine announcements
with their tokens), and the console to the engines (each engine's bearer token, on every call).

The console serves HTTPS with a Spring Boot SSL bundle; behind a TLS-terminating proxy instead, let it trust the
proxy's forwarded headers so it knows requests came in over HTTPS and marks its session cookie secure:

```yaml
spring:
  ssl:
    bundle:
      pem:
        console:
          keystore:
            certificate: file:/etc/staffix-admin/tls/console.crt
            private-key: file:/etc/staffix-admin/tls/console.key
        engines:
          truststore:
            certificate: file:/etc/staffix-admin/tls/engines-ca.crt
server:
  port: 8443
  ssl:
    bundle: console
  # behind a proxy, instead of server.ssl:
  # forward-headers-strategy: native
staffix:
  admin:
    engine-ssl-bundle: engines
    require-engine-tls: true
```

- `staffix.admin.engine-ssl-bundle`: the bundle whose truststore the console trusts engines' HTTPS certificates with;
  unset uses the JVM's default truststore.
- `staffix.admin.require-engine-tls`: refuses an engine announcing a plain `http` URL (400, logged). Off by default
  so the demo and a local setup work without certificates; turn it on in production.

On the engine side, staffix's HTTP admin connector serves HTTPS with `sslContext` (Spring Boot
`staffix.admin-api-http.ssl-bundle`) and trusts an HTTPS console with its announcement's `sslContext`
(`staffix.admin-api-http.announce-ssl-bundle`), the JVM's default truststore otherwise. Client certificates are not
supported yet.
