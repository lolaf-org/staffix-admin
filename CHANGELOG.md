# Changelog

All notable changes to this project are recorded here, in the format of
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). This project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each released version needs its own `## [x.y.z] - YYYY-MM-DD` heading here **before** the release is
cut: the release workflow refuses to run without one, and the GitHub Release for the tag is created
with that section as its body. Write it in the commit that precedes the release, together with the
matching `[x.y.z]:` link definition at the foot of the file.

There is deliberately no `[Unreleased]` section, which is where Keep a Changelog would collect notes
between releases. A section is written when the version it belongs to is being cut, so its heading
carries the right number and date the first time and the workflow's check has exactly one heading it
could mean.

## [0.9.0] - 2026-10-15

First public release. The staffix admin console is a web console for [staffix](https://github.com/lolaf-org/staffix)
FIX engines: every session of every engine at a glance, controlled and configured from one place, with their metrics
and FIX logs read from the backends they already export to. It needs Java 21, or Docker, and engines on staffix 0.9.1
or later with its HTTP admin API.

### Added

- **Engines register themselves.** An engine with staffix's HTTP admin API announces itself to the console with an
  `ENGINE` user's credentials; there is nothing to declare by hand. An announcement that would take over a live
  engine is refused, and the console can be made to require HTTPS towards engines.
- **Sessions overview**: every session of every engine, coloured by status (logged in, outside session time, logged
  out by an operator, down within session time), with counts, filters and search.
- **Session control**: log on and off, set sequence numbers, send a sequence reset, switch an initiator to a backup.
- **Session settings**: edit a session's settings in a form built from the engine's own schema; add and remove
  acceptor sessions.
- **Send messages**: send application messages into a session, several at once, one per line.
- **Availability**: a session's status over time and its availability within session time.
- **Alerts**: the alerts each engine raises are shown on the sessions, in the session panel and on an alerts page,
  with how long they have lasted and a link to their runbook. An operator can clear one by hand.
- **Metrics**: message rates and latencies, round-trip time, clock offset, and the application's own timers and
  gauges, over a time range or followed live.
- **FIX logs**: search messages by text, type and direction over a time range or live, decode them with the session's
  dictionary, see the session's metrics around a message, and export results as CSV.
- **Order trace**: follow an order by any of its ids across sessions and engines, from request to fill and drop copy.
- **Backends**: metrics from Prometheus, Elasticsearch or ClickHouse, FIX logs from Loki, Elasticsearch or ClickHouse,
  as staffix exports them through OpenTelemetry. Connectors are declared from the UI, with credentials given as
  references to environment variables or files, or stored encrypted under a master key; they are never shown back.
- **Logins and audit**: operators log in through JAAS, as `READER` (views) or `WRITER` (also changes); every change,
  and every refused one, is written to an audit log.
- **Schemas for the YAML files.** `config/` ships the JSON schemas of `application.yml` and `console.yaml`, so an
  editor such as VS Code completes the settings and flags a misspelled name or a malformed value as you type.
- **Light, dark and system themes.**
- **A distribution and a Docker image.** `staffix-admin-0.9.0.tar.gz` on the GitHub Release, started with
  `bin/staffix-admin.sh`, and `ghcr.io/lolaf-org/staffix-admin:0.9.0`. Both ship an `admin` user (`WRITER`) and an
  `engine` user (`ENGINE`) whose password is their name: change them before anyone else can reach the console.
- **Plugins.** Connectors, credential reference schemes, the audit sink and the configuration store are jars dropped
  into `plugins/`. The interfaces they implement are on Maven Central, as `org.lolaf.staffix.admin:staffix-admin-*-spi`,
  with sources and javadoc.

[0.9.0]: https://github.com/lolaf-org/staffix-admin/releases/tag/v0.9.0
