# Installation

## The distribution

`./mvnw install` builds the distribution as `staffix-admin-dist/target/staffix-admin.zip` (and `.tar.gz`, and
unpacked under `staffix-admin-dist/target/staffix-admin/`). The console builds against the staffix snapshot
(`0.9.1-SNAPSHOT`): install staffix locally first (`./mvnw install` in the staffix repository).

| Path | Content |
|---|---|
| `staffix-admin.jar` | The console, with the UI inside |
| `plugins/` | The YAML store, the audit log, and the Prometheus, Loki, Elasticsearch and ClickHouse connectors: see [Plugins](plugins.md) |
| `config/` | `application.yml`, `jaas.conf` and `users.properties`, read from the directory the console starts in |

`users.properties` defines no user until you add one, so a fresh install lets nobody in. Hash a password, add the
user, then start the console:

```shell
cd staffix-admin-dist/target/staffix-admin/staffix-admin
java -Dloader.main=org.lolaf.staffix.admin.server.security.jaas.PasswordHasher -jar staffix-admin.jar 'secret'
# add a user with that hash to config/users.properties, e.g. alice = {bcrypt}..., WRITER
java -jar staffix-admin.jar
```

Open http://localhost:8080. Every setting is listed in [Configuration](configuration.md); to log in against your own
directory instead of the users file, see [Security](security.md).

## Connecting engines

Engines appear once they announce themselves. Add staffix's HTTP admin API connector to each engine
(`staffix-admin-api-http-impl`, see staffix's
[runtime administration guide](https://github.com/lolaf-org/staffix/blob/main/docs/runtime-administration.md)), with
its `announcement` URL pointing at the console and the credentials of a console user with the `ENGINE` role. The
console then calls each engine with the token it announced.

To see metrics and logs, point the engines' OpenTelemetry exports at a backend and declare it to the console as a
[connector](connectors.md).

## Docker image

`staffix-admin-dist/Dockerfile` packages the distribution, running as a non-root user:

```shell
./mvnw install
docker build -t staffix-admin staffix-admin-dist
docker run -p 8080:8080 \
    -v /etc/staffix-admin:/opt/staffix-admin/config:ro \
    -v staffix-admin-data:/data \
    -e STAFFIX_ADMIN_SECRETS_MASTERKEY=... \
    staffix-admin
```

- `config/` holds the image's copy of the distribution's: as shipped it lets nobody in. Mount your own in its place,
  starting from `staffix-admin-dist/src/config/`: `application.yml`, `jaas.conf` and your users or JAAS module. Hash
  a password with the image itself:
  `docker run --rm --entrypoint sh staffix-admin -c "java -Dloader.main=org.lolaf.staffix.admin.server.security.jaas.PasswordHasher -jar staffix-admin.jar 'secret'"`.
- `/data` is the data directory: `console.yaml` lives there, so keep it on a volume.
- `JAVA_OPTS` passes JVM options; any setting can also come from the environment
  (`staffix.admin.engine-request-timeout` as `STAFFIX_ADMIN_ENGINEREQUESTTIMEOUT`).
- Plugins of your own: build on the image, `FROM staffix-admin` then `COPY my-plugin.jar plugins/`, rather than
  mounting over `plugins/`, which would hide the store and audit log the console needs.
- The console serves plain HTTP until told otherwise: see [TLS](security.md#tls).
